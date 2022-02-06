package com.hedera.services.stream;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doCallRealMethod;

@ExtendWith(MockitoExtension.class)
class HederaStreamTypeTest {
	private static final String expectedRecordDescription = "records";
	private static final String expectedRecordExtension = "rcd";
	private static final String expectedRecordSigExtension = "rcd_sig";
	private static final byte[] expectedRecordSigFileHeader = new byte[] { 5 };

	@Mock
	private HederaStreamType subject;

	@Test
	void capturesV5RecordStreamMetadata() {
		doCallRealMethod().when(subject).getDescription();
		doCallRealMethod().when(subject).getExtension();
		doCallRealMethod().when(subject).getSigExtension();
		doCallRealMethod().when(subject).getSigFileHeader();

		assertEquals(expectedRecordDescription, subject.getDescription());
		assertEquals(expectedRecordExtension, subject.getExtension());
		assertEquals(expectedRecordSigExtension, subject.getSigExtension());
		assertArrayEquals(expectedRecordSigFileHeader, subject.getSigFileHeader());
	}
}