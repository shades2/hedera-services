package com.hedera.services.store.contracts.precompile.proxy;

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

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.DecodingFacade;
import com.hedera.services.store.contracts.precompile.EncodingFacade;
import com.hedera.services.store.models.NftId;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.enums.TokenType.FUNGIBLE_COMMON;
import static com.hedera.services.store.contracts.precompile.DescriptorUtils.getRedirectTarget;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_BALANCE_OF_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_DECIMALS;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_NAME;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_OWNER_OF_NFT;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_SYMBOL;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TOKEN_URI_NFT;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TOTAL_SUPPLY_TOKEN;
import static com.hedera.services.utils.MiscUtils.asSecondsTimestamp;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

public class RedirectViewExecutor {
	public static final long MINIMUM_TINYBARS_COST = 100;

	private final Bytes input;
	private final MessageFrame frame;
	private final WorldLedgers ledgers;
	private final EncodingFacade encoder;
	private final DecodingFacade decoder;
	private final RedirectGasCalculator gasCalculator;
	private final HederaStackedWorldStateUpdater updater;

	public RedirectViewExecutor(
			final Bytes input,
			final MessageFrame frame,
			final EncodingFacade encoder,
			final DecodingFacade decoder,
			final RedirectGasCalculator gasCalculator
	) {
		this.input = input;
		this.frame = frame;
		this.encoder = encoder;
		this.decoder = decoder;
		this.gasCalculator = gasCalculator;

		this.updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
		this.ledgers = updater.trackingLedgers();
	}

	public Pair<Long, Bytes> computeCosted() {
		final var target = getRedirectTarget(input);
		final var tokenId = target.tokenId();
		final var now = asSecondsTimestamp(frame.getBlockValues().getTimestamp());
		final var costInGas = gasCalculator.compute(now, MINIMUM_TINYBARS_COST);

		final var selector = target.descriptor();
		final var isFungibleToken = FUNGIBLE_COMMON.equals(ledgers.typeOf(tokenId));
		final Bytes answer;
		if (selector == ABI_ID_NAME) {
			final var name = ledgers.nameOf(tokenId);
			answer = encoder.encodeName(name);
		} else if (selector == ABI_ID_SYMBOL) {
			final var symbol = ledgers.symbolOf(tokenId);
			answer = encoder.encodeSymbol(symbol);
		} else if (selector == ABI_ID_DECIMALS) {
			validateTrue(isFungibleToken, INVALID_TOKEN_ID);
			final var decimals = ledgers.decimalsOf(tokenId);
			answer = encoder.encodeDecimals(decimals);
		} else if (selector == ABI_ID_TOTAL_SUPPLY_TOKEN) {
			final var totalSupply = ledgers.totalSupplyOf(tokenId);
			answer = encoder.encodeTotalSupply(totalSupply);
		} else if (selector == ABI_ID_BALANCE_OF_TOKEN) {
			final var wrapper = decoder.decodeBalanceOf(input.slice(24), updater::unaliased);
			final var balance = ledgers.balanceOf(wrapper.accountId(), tokenId);
			answer = encoder.encodeBalance(balance);
		} else if (selector == ABI_ID_OWNER_OF_NFT) {
			validateFalse(isFungibleToken, INVALID_TOKEN_ID);
			final var wrapper = decoder.decodeOwnerOf(input.slice(24));
			final var nftId = NftId.fromGrpc(tokenId, wrapper.serialNo());
			final var owner = ledgers.ownerOf(nftId);
			final var priorityAddress = ledgers.canonicalAddress(owner);
			answer = encoder.encodeOwner(priorityAddress);
		} else if (selector == ABI_ID_TOKEN_URI_NFT) {
			validateFalse(isFungibleToken, INVALID_TOKEN_ID);
			final var wrapper = decoder.decodeTokenUriNFT(input.slice(24));
			final var nftId = NftId.fromGrpc(tokenId, wrapper.serialNo());
			final var metadata = ledgers.metadataOf(nftId);
			answer = encoder.encodeTokenUri(metadata);
		} else {
			// Only view functions can be used inside a ContractCallLocal
			throw new InvalidTransactionException(NOT_SUPPORTED);
		}
		return Pair.of(costInGas, answer);
	}
}
