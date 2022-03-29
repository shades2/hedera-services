package com.hedera.services.txns.crypto.validators;

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

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.aggregateNftAllowances;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.buildTokenAllowanceKey;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedId;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSerials;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

/**
 * Validations for {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} and
 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transaction allowances
 */
public interface AllowanceChecks {
	/**
	 * Validates the CryptoAllowances given in {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param cryptoAllowancesList
	 * 		crypto allowances list
	 * @param payerAccount
	 * 		Account of the payer for the Allowance approve/adjust txn
	 * @return response code after validation
	 */
	ResponseCodeEnum validateCryptoAllowances(final List<CryptoAllowance> cryptoAllowancesList,
			final Account payerAccount);

	/**
	 * Validate fungible token allowances list {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param tokenAllowancesList
	 * 		token allowances list
	 * @param payerAccount
	 * 		Account of the payer for the Allowance approve/adjust txn
	 * @return
	 */
	ResponseCodeEnum validateFungibleTokenAllowances(final List<TokenAllowance> tokenAllowancesList,
			final Account payerAccount);

	/**
	 * Validate nft allowances list {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param nftAllowancesList
	 * 		nft allowances list
	 * @param payerAccount
	 * 		Account of the payer for the Allowance approve/adjust txn
	 * @return
	 */
	ResponseCodeEnum validateNftAllowances(final List<NftAllowance> nftAllowancesList,
			final Account payerAccount);

	default ResponseCodeEnum validateNftAllowances(
			final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> nftsMap,
			final TypedTokenStore tokenStore,
			final AccountStore accountStore,
			final List<NftAllowance> nftAllowancesList,
			final Account payerAccount) {
		if (nftAllowancesList.isEmpty()) {
			return OK;
		}

		final List<Pair<EntityNum, FcTokenAllowanceId>> nftKeys = new ArrayList<>();
		for (var allowance : nftAllowancesList) {
			nftKeys.add(buildTokenAllowanceKey(allowance.getOwner(), allowance.getTokenId(), allowance.getSpender()));
		}
		if (hasRepeatedId(nftKeys)) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		for (final var allowance : nftAllowancesList) {
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			final var delegatingSpender = Id.fromGrpcAccount(allowance.getDelegatingSpender());
			final var tokenId = allowance.getTokenId();
			final var serialNums = allowance.getSerialNumbersList();
			final var token = tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(tokenId));
			final var approvedForAll = allowance.getApprovedForAll().getValue();

			if (token.isFungibleCommon()) {
				return FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
			}

			final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
			if (fetchResult.getRight() != OK) {
				return fetchResult.getRight();
			}
			final var ownerAccount = fetchResult.getLeft();

			var validity = validateTokenBasics(ownerAccount, spender, token, tokenStore);
			if (validity != OK) {
				return validity;
			}

			if (approvedForAll && !delegatingSpender.equals(Id.MISSING_ID)) {
				return DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
			} else if (!delegatingSpender.equals(Id.MISSING_ID)) {
				final var allowanceKey = FcTokenAllowanceId.from(
						EntityNum.fromTokenId(tokenId), delegatingSpender.asEntityNum());
				if (!ownerAccount.getApprovedForAllNftsAllowances().contains(allowanceKey)) {
					return DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
				}
			}

			if (!approvedForAll && serialNums.isEmpty()) {
				return EMPTY_ALLOWANCES;
			}

			validity = validateSerialNums(nftsMap.get(), serialNums, token);
			if (validity != OK) {
				return validity;
			}
		}
		return OK;
	}

	/**
	 * Validate all allowances in {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param cryptoAllowances
	 * 		crypto allowances list
	 * @param tokenAllowances
	 * 		fungible token allowances list
	 * @param nftAllowances
	 * 		nft allowances list
	 * @param payerAccount
	 * 		Account of the payer for the allowance approve/adjust txn.
	 * @param maxLimitPerTxn
	 * 		max allowance limit per transaction
	 * @return response code after validation
	 */
	default ResponseCodeEnum allowancesValidation(final List<CryptoAllowance> cryptoAllowances,
			final List<TokenAllowance> tokenAllowances,
			final List<NftAllowance> nftAllowances,
			final Account payerAccount,
			final int maxLimitPerTxn) {

		// feature flag for allowances
		if (!isEnabled()) {
			return NOT_SUPPORTED;
		}
		var validity = commonChecks(cryptoAllowances, tokenAllowances, nftAllowances, maxLimitPerTxn);
		if (validity != OK) {
			return validity;
		}

		validity = validateCryptoAllowances(cryptoAllowances, payerAccount);
		if (validity != OK) {
			return validity;
		}

		validity = validateFungibleTokenAllowances(tokenAllowances, payerAccount);
		if (validity != OK) {
			return validity;
		}

		validity = validateNftAllowances(nftAllowances, payerAccount);
		if (validity != OK) {
			return validity;
		}

		return OK;
	}

	default ResponseCodeEnum validateCryptoAllowanceBasics(final Id ownerId, final Id spender) {
		if (ownerId.equals(spender)) {
			return SPENDER_ACCOUNT_SAME_AS_OWNER;
		}
		return OK;
	}

	default ResponseCodeEnum validateTokenBasics(
			final Account ownerAccount,
			final Id spenderId,
			final Token token,
			final TypedTokenStore tokenStore) {
		if (ownerAccount.getId().equals(spenderId)) {
			return SPENDER_ACCOUNT_SAME_AS_OWNER;
		}
		if (!tokenStore.hasAssociation(token, ownerAccount)) {
			return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
		}
		return OK;
	}

	default ResponseCodeEnum commonChecks(final List<CryptoAllowance> cryptoAllowances,
			final List<TokenAllowance> tokenAllowances,
			final List<NftAllowance> nftAllowances,
			final int maxLimitPerTxn) {
		// each serial number of an NFT is considered as an allowance.
		// So for Nft allowances aggregated amount is considered for limit calculation.
		final var totalAllowances = cryptoAllowances.size() + tokenAllowances.size()
				+ aggregateNftAllowances(nftAllowances);

		if (exceedsTxnLimit(totalAllowances, maxLimitPerTxn)) {
			return MAX_ALLOWANCES_EXCEEDED;
		}
		if (emptyAllowances(totalAllowances)) {
			return EMPTY_ALLOWANCES;
		}
		return OK;
	}

	/**
	 * Validates serial numbers for {@link NftAllowance}
	 *
	 * @param nftsMap
	 * 		The MerkleMap of nftId and MerkleUniqueToken to fetch the uniqueToken's owner/spender etc..
	 * @param serialNums
	 * 		given serial numbers in the {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} operation
	 * @param token
	 * 		token for which allowance is related to
	 * @return response code after validation
	 */
	default ResponseCodeEnum validateSerialNums(
			final MerkleMap<EntityNumPair, MerkleUniqueToken> nftsMap,
			final List<Long> serialNums,
			final Token token) {
		if (hasRepeatedSerials(serialNums)) {
			return REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
		}

		for (var serial : serialNums) {
			final var nftId = NftId.withDefaultShardRealm(token.getId().num(), serial);
			if (serial <= 0 || !nftsMap.containsKey(EntityNumPair.fromNftId(nftId))) {
				return INVALID_TOKEN_NFT_SERIAL_NUMBER;
			}
		}

		return OK;
	}

	default boolean exceedsTxnLimit(final int totalAllowances, final int maxLimit) {
		return totalAllowances > maxLimit;
	}

	default boolean emptyAllowances(final int totalAllowances) {
		return totalAllowances == 0;
	}

	default Pair<Account, ResponseCodeEnum> fetchOwnerAccount(Id owner, Account payerAccount,
			AccountStore accountStore) {
		if (owner.equals(Id.MISSING_ID) || owner.equals(payerAccount.getId())) {
			return Pair.of(payerAccount, OK);
		} else {
			try {
				return Pair.of(accountStore.loadAccount(owner), OK);
			} catch (InvalidTransactionException ex) {
				return Pair.of(payerAccount, INVALID_ALLOWANCE_OWNER_ID);
			}
		}
	}

	/**
	 * Check if the allowance feature is enabled
	 *
	 * @return true if the feature is enabled in {@link com.hedera.services.context.properties.GlobalDynamicProperties}
	 */
	boolean isEnabled();
}
