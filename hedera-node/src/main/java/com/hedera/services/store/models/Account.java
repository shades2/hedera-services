package com.hedera.services.store.models;

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

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getMaxAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setAlreadyUsedAutomaticAssociationsTo;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setMaxAutomaticAssociationsTo;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.aggregateNftAllowances;
import static com.hedera.services.utils.EntityNumPair.MISSING_NUM_PAIR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;

/**
 * Encapsulates the state and operations of a Hedera account.
 * <p>
 * Operations are validated, and throw a {@link com.hedera.services.exceptions.InvalidTransactionException}
 * with response code capturing the failure when one occurs.
 *
 * <b>NOTE:</b> This implementation is incomplete, and includes
 * only the API needed to support the Hedera Token Service. The
 * memo field, for example, is not yet present.
 */
public class Account {
	private final Id id;

	private long expiry;
	private long balance;
	private boolean deleted = false;
	private boolean isSmartContract = false;
	private boolean isReceiverSigRequired = false;
	private long ownedNfts;
	private long autoRenewSecs;
	private ByteString alias = ByteString.EMPTY;
	private JKey key;
	private String memo = "";
	private Id proxy;
	private int autoAssociationMetadata;
	private TreeMap<EntityNum, Long> cryptoAllowances;
	private TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances;
	private TreeMap<FcTokenAllowanceId, FcTokenAllowance> nftAllowances;
	private int numAssociations;
	private int numZeroBalances;
	private EntityNumPair lastAssociatedToken;

	public Account(Id id) {
		this.id = id;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public void initBalance(long balance) {
		this.balance = balance;
	}

	public long getOwnedNfts() {
		return ownedNfts;
	}

	public void setOwnedNfts(long ownedNfts) {
		this.ownedNfts = ownedNfts;
	}

	public void incrementOwnedNfts() {
		this.ownedNfts++;
	}

	public void setAutoAssociationMetadata(int autoAssociationMetadata) {
		this.autoAssociationMetadata = autoAssociationMetadata;
	}

	public Address canonicalAddress() {
		if (alias.isEmpty()) {
			return id.asEvmAddress();
		} else {
			return Address.wrap(Bytes.wrap(alias.toByteArray()));
		}
	}

	public int getAutoAssociationMetadata() {
		return autoAssociationMetadata;
	}

	public int getMaxAutomaticAssociations() {
		return getMaxAutomaticAssociationsFrom(autoAssociationMetadata);
	}

	public int getAlreadyUsedAutomaticAssociations() {
		return getAlreadyUsedAutomaticAssociationsFrom(autoAssociationMetadata);
	}

	public void setMaxAutomaticAssociations(int maxAutomaticAssociations) {
		autoAssociationMetadata = setMaxAutomaticAssociationsTo(autoAssociationMetadata, maxAutomaticAssociations);
	}

	public void setAlreadyUsedAutomaticAssociations(int alreadyUsedCount) {
		validateTrue(isValidAlreadyUsedCount(alreadyUsedCount), NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
		autoAssociationMetadata = setAlreadyUsedAutomaticAssociationsTo(autoAssociationMetadata, alreadyUsedCount);
	}

	public void incrementUsedAutomaticAssocitions() {
		var count = getAlreadyUsedAutomaticAssociations();
		setAlreadyUsedAutomaticAssociations(++count);
	}

	public void decrementUsedAutomaticAssocitions() {
		var count = getAlreadyUsedAutomaticAssociations();
		setAlreadyUsedAutomaticAssociations(--count);
	}

	public EntityNumPair getLastAssociatedToken() {
		return lastAssociatedToken;
	}

	public void setLastAssociatedToken(final EntityNumPair lastAssociatedToken) {
		this.lastAssociatedToken = lastAssociatedToken;
	}

	public int getNumAssociations() {
		return numAssociations;
	}

	public void setNumAssociations(final int numAssociations) {
		if (numAssociations < 0) {
			// not possible
			this.numAssociations = 0;
		} else {
			this.numAssociations = numAssociations;
		}
	}

	public int getNumZeroBalances() {
		return numZeroBalances;
	}

	public void setNumZeroBalances(final int numZeroBalances) {
		if (numZeroBalances < 0) {
			// not possible
			this.numZeroBalances = 0;
		} else {
			this.numZeroBalances = numZeroBalances;
		}
	}

	/**
	 * Associated the given list of Tokens to this account.
	 *
	 * @param tokens
	 * 		List of tokens to be associated to the Account
	 * @param tokenStore
	 * 		TypedTokenStore to validate if existing relationship with the tokens to be associated with.
	 * @param isAutomaticAssociation
	 * 		boolean flag to denote if its an automaticAssociation.
	 * @param shouldEnableRelationship
	 * 		boolean flag to denote if the new relationships have to enabled by default without considering the KYC key and Freeze Key
	 * @return A list of TokenRelationships [new and old] that are touched by associating the tokens to this account.
	 */
	public List<TokenRelationship> associateWith(
			final List<Token> tokens,
			final TypedTokenStore tokenStore,
			final boolean isAutomaticAssociation,
			final boolean shouldEnableRelationship) {
		List<TokenRelationship> tokenRelationshipsToPersist = new ArrayList<>();
		var currKey = lastAssociatedToken;
		TokenRelationship prevRel = currKey.equals(MISSING_NUM_PAIR) ?
				null : tokenStore.getLatestTokenRelationship(this);
		for (var token : tokens) {
			validateFalse(tokenStore.hasAssociation(token, this), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
			if (isAutomaticAssociation) {
				incrementUsedAutomaticAssocitions();
			}

			final var newRel = shouldEnableRelationship ?
					token.newEnabledRelationship(this) :
					token.newRelationshipWith(this, false);
			if (prevRel != null) {
				final var prevKey = prevRel.getPrevKey();
				newRel.setPrevKey(prevKey);
				newRel.setNextKey(currKey);
				prevRel.setPrevKey(newRel.getKey());
				tokenRelationshipsToPersist.add(prevRel);
			}
			numZeroBalances++;
			numAssociations++;
			prevRel = newRel;
			currKey = newRel.getKey();
		}
		lastAssociatedToken = currKey;
		tokenRelationshipsToPersist.add(prevRel);
		return tokenRelationshipsToPersist;
	}

	/**
	 * Applies the given list of {@link Dissociation}s, validating that this account is
	 * indeed associated to each involved token.
	 *  @param dissociations
	 * 		the dissociations to perform.
	 * @param tokenStore
	 * 		tokenStore to load the prev and next tokenRelationships of the account
	 * @param validator
	 * 		validator to check if the dissociating token has expired.
	 * @return A list of TokenRelationships that are touched by the dissociating tokens.
	 */
	public List<TokenRelationship> dissociateUsing(
			List<Dissociation> dissociations,
			TypedTokenStore tokenStore,
			OptionValidator validator) {
		final Map<EntityNumPair, TokenRelationship> unPersistedRelationships = new HashMap<>();
		for (var dissociation : dissociations) {
			validateTrue(id.equals(dissociation.dissociatingAccountId()), FAIL_INVALID);

			decrementCounters(dissociation);

			dissociation.updateModelRelsSubjectTo(validator);

			if (dissociation.dissociatingAccountRel().isAutomaticAssociation()) {
				decrementUsedAutomaticAssocitions();
			}

			final var tokenId = dissociation.dissociatedTokenId();
			final var associationKey = EntityNumPair.fromLongs(id.num(),tokenId.num());

			if (lastAssociatedToken.equals(associationKey)) {
				// removing the latest associated token from the account
				updateLastAssociation(tokenStore, unPersistedRelationships, associationKey);
			} else {
				/* get next, prev tokenRelationships and update the links by un-linking the dissociating relationship */
				updateAssociationList(tokenStore, unPersistedRelationships, dissociation.dissociatingToken(), associationKey);
			}
		}
		return unPersistedRelationships.values().stream().toList();
	}

	private void updateAssociationList(
			final TypedTokenStore tokenStore,
			final Map<EntityNumPair, TokenRelationship> unPersistedRelationships,
			final Token token,
			final EntityNumPair associationKey) {
		final var dissociatingRel = unPersistedRelationships.getOrDefault(associationKey,
				tokenStore.loadTokenRelationship(token, this));
		final var prevKey = dissociatingRel.getPrevKey();
		final var prevToken = tokenStore.loadPossiblyDeletedOrAutoRemovedToken(
				Id.fromGrpcToken(prevKey.asAccountTokenRel().getRight()));
		final var prevRel = unPersistedRelationships.getOrDefault(prevKey,
				tokenStore.loadTokenRelationship(prevToken, this));
		// nextKey can be 0.
		final var nextKey = dissociatingRel.getNextKey();
		if (!nextKey.equals(MISSING_NUM_PAIR)) {
			final var nextToken = tokenStore.loadPossiblyDeletedOrAutoRemovedToken(
					Id.fromGrpcToken(nextKey.asAccountTokenRel().getRight()));
			final var nextRel = unPersistedRelationships.getOrDefault(nextKey,
					tokenStore.loadTokenRelationship(nextToken, this));
			nextRel.setPrevKey(prevKey);
			unPersistedRelationships.put(nextKey, nextRel);
		}
		prevRel.setNextKey(nextKey);
		unPersistedRelationships.put(prevKey, prevRel);
	}

	private void updateLastAssociation(
			final TypedTokenStore tokenStore,
			final Map<EntityNumPair, TokenRelationship> unPersistedRelationships,
			final EntityNumPair associationKey) {
		final var latestRel =  unPersistedRelationships.getOrDefault(associationKey,
				tokenStore.getLatestTokenRelationship(this));
		final var nextKey = latestRel.getNextKey();

		if (!nextKey.equals(MISSING_NUM_PAIR)) {
			final var nextToken = tokenStore.loadPossiblyDeletedOrAutoRemovedToken(
					Id.fromGrpcToken(nextKey.asAccountTokenRel().getRight()));
			final var nextRel = unPersistedRelationships.getOrDefault(nextKey,
					tokenStore.loadTokenRelationship(nextToken, this));
			lastAssociatedToken = nextRel.getKey();
			nextRel.setPrevKey(latestRel.getPrevKey());
			unPersistedRelationships.put(nextKey, nextRel);
		} else {
			lastAssociatedToken = MISSING_NUM_PAIR;
		}
	}

	private void decrementCounters(final Dissociation dissociation) {
		if (shouldDecreaseNumZeroBalances(dissociation)) {
			numZeroBalances--;
		}
		numAssociations--;
	}

	private boolean shouldDecreaseNumZeroBalances(final Dissociation dissociation) {
		if (dissociation.dissociatingToken().getTreasury().getId().equals(id)) {
			return dissociation.dissociatedTokenTreasuryRel().getBalance() == 0;
		} else {
			return dissociation.dissociatingAccountRel().getBalance() == 0;
		}
	}

	public Id getId() {
		return id;
	}

	private boolean isValidAlreadyUsedCount(int alreadyUsedCount) {
		return alreadyUsedCount >= 0 && alreadyUsedCount <= getMaxAutomaticAssociations();
	}

	/* NOTE: The object methods below are only overridden to improve
	readability of unit tests; this model object is not used in hash-based
	collections, so the performance of these methods doesn't matter. */

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(Account.class)
				.add("id", id)
				.add("expiry", expiry)
				.add("balance", balance)
				.add("deleted", deleted)
				.add("ownedNfts", ownedNfts)
				.add("alreadyUsedAutoAssociations", getAlreadyUsedAutomaticAssociations())
				.add("maxAutoAssociations", getMaxAutomaticAssociations())
				.add("alias", getAlias().toStringUtf8())
				.add("cryptoAllowances", cryptoAllowances)
				.add("fungibleTokenAllowances", fungibleTokenAllowances)
				.add("nftAllowances", nftAllowances)
				.add("numAssociations", numAssociations)
				.add("numZeroBalances", numZeroBalances)
				.add("lastAssociatedToken", lastAssociatedToken)
				.toString();
	}

	public long getExpiry() {
		return expiry;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(final boolean deleted) {
		this.deleted = deleted;
	}

	public boolean isSmartContract() {
		return isSmartContract;
	}

	public void setSmartContract(boolean val) {
		this.isSmartContract = val;
	}

	public boolean isReceiverSigRequired() {
		return this.isReceiverSigRequired;
	}

	public void setReceiverSigRequired(boolean isReceiverSigRequired) {
		this.isReceiverSigRequired = isReceiverSigRequired;
	}

	public long getBalance() {
		return balance;
	}

	public long getAutoRenewSecs() {
		return autoRenewSecs;
	}

	public void setAutoRenewSecs(final long autoRenewSecs) {
		this.autoRenewSecs = autoRenewSecs;
	}

	public JKey getKey() {
		return key;
	}

	public void setKey(final JKey key) {
		this.key = key;
	}

	public String getMemo() {
		return memo;
	}

	public void setMemo(final String memo) {
		this.memo = memo;
	}

	public Id getProxy() {
		return proxy;
	}

	public void setProxy(final Id proxy) {
		this.proxy = proxy;
	}

	public ByteString getAlias() {
		return alias;
	}

	public void setAlias(final ByteString alias) {
		this.alias = alias;
	}

	public Map<EntityNum, Long> getCryptoAllowances() {
		return cryptoAllowances == null ? Collections.emptyMap() : cryptoAllowances;
	}

	public SortedMap<EntityNum, Long> getMutableCryptoAllowances() {
		if (cryptoAllowances == null) {
			cryptoAllowances = new TreeMap<>();
		}
		return cryptoAllowances;
	}

	public void setCryptoAllowances(final Map<EntityNum, Long> cryptoAllowances) {
		this.cryptoAllowances = new TreeMap<>(cryptoAllowances);
	}

	public Map<FcTokenAllowanceId, Long> getFungibleTokenAllowances() {
		return fungibleTokenAllowances == null ? Collections.emptyMap() : fungibleTokenAllowances;
	}

	public SortedMap<FcTokenAllowanceId, Long> getMutableFungibleTokenAllowances() {
		if (fungibleTokenAllowances == null) {
			fungibleTokenAllowances = new TreeMap<>();
		}
		return fungibleTokenAllowances;
	}

	public void setFungibleTokenAllowances(
			final Map<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
		this.fungibleTokenAllowances = new TreeMap<>(fungibleTokenAllowances);
	}

	public Map<FcTokenAllowanceId, FcTokenAllowance> getNftAllowances() {
		return nftAllowances == null ? Collections.emptyMap() : nftAllowances;
	}

	public SortedMap<FcTokenAllowanceId, FcTokenAllowance> getMutableNftAllowances() {
		if (nftAllowances == null) {
			nftAllowances = new TreeMap<>();
		}
		return nftAllowances;
	}

	public void setNftAllowances(
			final Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowances) {
		this.nftAllowances = new TreeMap<>(nftAllowances);
	}

	public int getTotalAllowances() {
		// each serial number of an NFT is considered as an allowance.
		// So for Nft allowances aggregated amount is considered for limit calculation.
		return cryptoAllowances.size() + fungibleTokenAllowances.size() + aggregateNftAllowances(nftAllowances);
	}
}
