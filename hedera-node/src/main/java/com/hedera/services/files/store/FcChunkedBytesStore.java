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

import com.hedera.services.state.merkle.internals.ChunkPath;
import com.swirlds.merkle.chunk.Chunk;
import com.swirlds.merkle.chunk.KeyedChunk;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.AbstractMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class FcChunkedBytesStore extends AbstractMap<String, byte[]> {
	private static final Logger log = LogManager.getLogger(FcChunkedBytesStore.class);

	private final Supplier<MerkleMap<ChunkPath, KeyedChunk<ChunkPath>>> chunks;

	public FcChunkedBytesStore(Supplier<MerkleMap<ChunkPath, KeyedChunk<ChunkPath>>> chunks) {
		this.chunks = chunks;
	}

	private ChunkPath at(Object key) {
		return new ChunkPath((String) key);
	}

	@Override
	public void clear() {
		chunks.get().clear();
	}

	/**
	 * Removes the blob at the given path.
	 *
	 * <B>NOTE:</B> This method breaks the standard {@code Map} contract,
	 * and does not return the contents of the removed blob.
	 *
	 * @param path
	 * 		the path of the blob
	 * @return {@code null}
	 */
	@Override
	public byte[] remove(Object path) {
		chunks.get().remove(at(path));
		return null;
	}

	/**
	 * Replaces the blob at the given path with the given contents.
	 *
	 * <B>NOTE:</B> This method breaks the standard {@code Map} contract,
	 * and does not return the contents of the previous blob.
	 *
	 * @param path
	 * 		the path of the blob
	 * @param value
	 * 		the contents to be set
	 * @return {@code null}
	 */
	@Override
	public byte[] put(String path, byte[] value) {
		var meta = at(path);
		if (chunks.get().containsKey(meta)) {
			final var chunk = chunks.get().getForModify(meta);
			chunk.setData(value);
			if (log.isDebugEnabled()) {
				log.debug("Modifying to {} new bytes (hash = {}) @ '{}'", value.length, chunk.getHash(), path);
			}
		} else {
			final KeyedChunk<ChunkPath> newChunk = new KeyedChunk<>(value);
			if (log.isDebugEnabled()) {
				log.debug("Putting {} new bytes (hash = {}) @ '{}'", value.length, newChunk.getHash(), path);
			}
			chunks.get().put(at(path), newChunk);
		}
		return null;
	}

	@Override
	public byte[] get(Object path) {
		return Optional.ofNullable(chunks.get().get(at(path)))
				.map(Chunk::getData)
				.orElse(null);
	}

	@Override
	public boolean containsKey(Object path) {
		return chunks.get().containsKey(at(path));
	}

	@Override
	public boolean isEmpty() {
		return chunks.get().isEmpty();
	}

	@Override
	public int size() {
		return chunks.get().size();
	}

	@Override
	public Set<Entry<String, byte[]>> entrySet() {
		throw new UnsupportedOperationException();
	}
}
