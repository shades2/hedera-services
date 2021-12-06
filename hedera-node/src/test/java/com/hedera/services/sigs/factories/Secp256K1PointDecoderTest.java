package com.hedera.services.sigs.factories;

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

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.test.factories.keys.KeyFactory;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class Secp256K1PointDecoderTest {
	@Mock
	private NodeLocalProperties properties;

	private Secp256k1PointDecoder subject;

	@BeforeEach
	void setUp() {
		given(properties.sec256k1PointCacheMaxSize()).willReturn(1L);
		subject = new Secp256k1PointDecoder(properties);
	}

	@Test
	void loadsMissingPoint() {
		final var kp = KeyFactory.ecdsaKpGenerator.generateKeyPair();
		final var q = ((ECPublicKeyParameters) kp.getPublic()).getQ();
		final var compressed = q.getEncoded(true);
		final var uncompressed = Arrays.copyOfRange(q.getEncoded(false), 1, 65);

		assertArrayEquals(uncompressed, subject.getDecoded(compressed));
	}
}
