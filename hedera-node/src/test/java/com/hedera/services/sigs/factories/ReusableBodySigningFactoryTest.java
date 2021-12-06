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

import com.hedera.services.sigs.utils.MiscCryptoUtils;
import com.hedera.services.utils.TxnAccessor;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.EXPECTED_SIG;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.data;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.pk;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.sig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReusableBodySigningFactoryTest {
	@Mock
	private TxnAccessor accessor;
	@Mock
	private Secp256k1PointDecoder decoder;

	private ReusableBodySigningFactory subject;

	@BeforeEach
	void setUp() {
		subject = new ReusableBodySigningFactory(decoder);
	}

	@Test
	void resetWorks() {
		// when:
		subject.resetFor(accessor);

		// then:
		assertSame(accessor, subject.getAccessor());
	}

	@Test
	void createsExpectedBodySigGivenInjectedAccessor() {
		given(accessor.getTxnBytes()).willReturn(data);

		subject = new ReusableBodySigningFactory(accessor, decoder);

		final var actualSig = subject.signBodyWithEd25519(pk, sig);

		assertEquals(EXPECTED_SIG, actualSig);
	}

	@Test
	void createsExpectedBodySig() {
		given(accessor.getTxnBytes()).willReturn(data);

		// when:
		subject.resetFor(accessor);
		// and:
		final var actualSig = subject.signBodyWithEd25519(pk, sig);

		// then:
		assertEquals(EXPECTED_SIG, actualSig);
	}

	@Test
	void createsExpectedKeccak256Sig() {
		final var mockCompressed = "012345678901234567890123456789012".getBytes();
		final var mockUncompressed = "0123456789012345678901234567890123456789012345678901234567890123".getBytes();
		given(decoder.getDecoded(mockCompressed)).willReturn(mockUncompressed);
		given(accessor.getKeccak256Hash()).willReturn(digest);

		final var expectedSig = expectedEcdsaSecp256k1Sig(mockUncompressed);

		subject.resetFor(accessor);
		final var actualSig = subject.signKeccak256DigestWithSecp256k1(mockCompressed, sig);

		assertEquals(expectedSig, actualSig);
	}

	private TransactionSignature expectedEcdsaSecp256k1Sig(final byte[] pk) {
		final var expectedContents = new byte[digest.length + sig.length];
		System.arraycopy(sig, 0, expectedContents, 0, sig.length);
		System.arraycopy(digest, 0, expectedContents, sig.length, digest.length);
		return new TransactionSignature(
				expectedContents,
				0, sig.length,
				pk, 0, pk.length,
				sig.length, digest.length, SignatureType.ECDSA_SECP256K1);
	}

	static final byte[] digest = MiscCryptoUtils.keccak256DigestOf(data);
}
