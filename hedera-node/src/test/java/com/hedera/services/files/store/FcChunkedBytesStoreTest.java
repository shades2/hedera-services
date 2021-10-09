package com.hedera.services.files.store;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.internals.ChunkPath;
import com.swirlds.merkle.chunk.KeyedChunk;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class FcChunkedBytesStoreTest {
	private static final byte[] aData = "BlobA".getBytes();
	private static final byte[] bData = "BlobB".getBytes();
	private static final ChunkPath pathA = new ChunkPath("pathA");

	private KeyedChunk<ChunkPath> chunkA;
	private KeyedChunk<ChunkPath> chunkB;
	private MerkleMap<ChunkPath, KeyedChunk<ChunkPath>> pathedBlobs;

	private FcChunkedBytesStore subject;

	@BeforeEach
	private void setup() {
		pathedBlobs = mock(MerkleMap.class);

		givenMockBlobs();

		subject = new FcChunkedBytesStore(() -> pathedBlobs);
	}

	@Test
	void delegatesClear() {
		subject.clear();

		verify(pathedBlobs).clear();
	}

	@Test
	void delegatesRemoveOfMissing() {
		given(pathedBlobs.remove(pathA.getLoc())).willReturn(null);

		assertNull(subject.remove(pathA));
	}

	@Test
	void delegatesRemoveAndReturnsNull() {
		given(pathedBlobs.remove(pathA.getLoc())).willReturn(chunkA);

		assertNull(subject.remove(pathA));
	}

	@Test
	void delegatesPutUsingGetForModifyIfExtantBlob() {
		given(pathedBlobs.containsKey(pathA)).willReturn(true);
		given(pathedBlobs.getForModify(pathA)).willReturn(chunkA);

		final var oldBytes = subject.put(pathA.getLoc(), aData);

		verify(pathedBlobs).containsKey(pathA);
		verify(pathedBlobs).getForModify(pathA);
		verify(chunkA).setData(aData);

		assertNull(oldBytes);
	}

	@Test
	void delegatesPutUsingGetAndFactoryIfNewBlob() {
		final var keyCaptor = ArgumentCaptor.forClass(ChunkPath.class);
		final var valueCaptor = ArgumentCaptor.forClass(KeyedChunk.class);
		given(pathedBlobs.containsKey(pathA)).willReturn(false);

		final var oldBytes = subject.put(pathA.getLoc(), aData);

		verify(pathedBlobs).containsKey(pathA);
		verify(pathedBlobs).put(keyCaptor.capture(), valueCaptor.capture());

		assertEquals(pathA, keyCaptor.getValue());
		assertSame(chunkA, valueCaptor.getValue());
		assertNull(oldBytes);
	}

	@Test
	void propagatesNullFromGet() {
		given(pathedBlobs.get(pathA)).willReturn(null);

		assertNull(subject.get(pathA));
	}

	@Test
	void delegatesGet() {
		given(pathedBlobs.get(pathA)).willReturn(chunkA);

		assertArrayEquals(aData, subject.get(pathA));
	}

	@Test
	void delegatesContainsKey() {
		given(pathedBlobs.containsKey(pathA)).willReturn(true);

		assertTrue(subject.containsKey(pathA));
	}

	@Test
	void delegatesIsEmpty() {
		given(pathedBlobs.isEmpty()).willReturn(true);

		assertTrue(subject.isEmpty());
		verify(pathedBlobs).isEmpty();
	}

	@Test
	void delegatesSize() {
		given(pathedBlobs.size()).willReturn(123);

		assertEquals(123, subject.size());
	}

	private void givenMockBlobs() {
		chunkA = mock(KeyedChunk.class);
		chunkB = mock(KeyedChunk.class);

		given(chunkA.getData()).willReturn(aData);
		given(chunkB.getData()).willReturn(bData);
	}

	@Test
	void putDeletesReplacedValueIfNoCopyIsHeld() {
		final MerkleMap<String, MerkleOptionalBlob> blobs = new MerkleMap<>();
		blobs.put("path", new MerkleOptionalBlob("FIRST".getBytes()));

		final var replaced = blobs.put("path", new MerkleOptionalBlob("SECOND".getBytes()));

		assertTrue(replaced.getDelegate().isReleased());
	}

	@Test
	void putDoesNotDeleteReplacedValueIfCopyIsHeld() {
		final MerkleMap<String, MerkleOptionalBlob> blobs = new MerkleMap<>();
		blobs.put("path", new MerkleOptionalBlob("FIRST".getBytes()));

		final var copy = blobs.copy();
		final var replaced = copy.put("path", new MerkleOptionalBlob("SECOND".getBytes()));

		assertFalse(replaced.getDelegate().isReleased());
	}

	private MerkleBlobMeta at(final String key) {
		return new MerkleBlobMeta(key);
	}
}
