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

import com.hedera.services.utils.TxnAccessor;
import com.swirlds.common.crypto.TransactionSignature;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.services.sigs.factories.PlatformSigFactory.ecdsaSecp256k1Sig;
import static com.hedera.services.sigs.factories.PlatformSigFactory.ed25519Sig;

@Singleton
public class ReusableBodySigningFactory implements TxnScopedPlatformSigFactory {
	private final Secp256k1PointDecoder decoder;

	private TxnAccessor accessor;

	@Inject
	public ReusableBodySigningFactory(final Secp256k1PointDecoder decoder) {
		this.decoder = decoder;
	}

	public ReusableBodySigningFactory(final TxnAccessor accessor, final Secp256k1PointDecoder decoder) {
		this.decoder = decoder;
		this.accessor = accessor;
	}

	public void resetFor(final TxnAccessor accessor) {
		this.accessor = accessor;
	}

	@Override
	public TransactionSignature signBodyWithEd25519(final byte[] publicKey, final byte[] sigBytes) {
		return ed25519Sig(publicKey, sigBytes, accessor.getTxnBytes());
	}

	@Override
	public TransactionSignature signKeccak256DigestWithSecp256k1(final byte[] publicKey, final byte[] sigBytes) {
		final var rawPublicKey = decoder.getDecoded(publicKey);
		return ecdsaSecp256k1Sig(rawPublicKey, sigBytes, accessor.getKeccak256Hash());
	}

	/* --- Only used by unit tests --- */
	TxnAccessor getAccessor() {
		return accessor;
	}
}
