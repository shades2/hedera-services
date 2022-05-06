package com.hedera.services.state.merkle;

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
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.MutabilityException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getMaxAutomaticAssociationsFrom;
import static com.hedera.services.state.serdes.IoUtils.readNullable;
import static com.hedera.services.state.serdes.IoUtils.readNullableSerializable;
import static com.hedera.services.state.serdes.IoUtils.writeNullable;
import static com.hedera.services.state.serdes.IoUtils.writeNullableSerializable;
import static com.hedera.services.utils.EntityIdUtils.asIdLiteral;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.hedera.services.utils.SerializationUtils.deserializeApproveForAllNftsAllowances;
import static com.hedera.services.utils.SerializationUtils.deserializeCryptoAllowances;
import static com.hedera.services.utils.SerializationUtils.deserializeFungibleTokenAllowances;
import static com.hedera.services.utils.SerializationUtils.serializeApproveForAllNftsAllowances;
import static com.hedera.services.utils.SerializationUtils.serializeCryptoAllowances;
import static com.hedera.services.utils.SerializationUtils.serializeTokenAllowances;

public class MerkleAccountState extends AbstractMerkleLeaf {
	private static final int MAX_CONCEIVABLE_MEMO_UTF8_BYTES = 1_024;

	static final int RELEASE_0230_VERSION = 10;
	static final int RELEASE_0250_ALPHA_VERSION = 11;
	static final int RELEASE_0250_VERSION = 12;
	static final int RELEASE_0270_VERSION = 13;
	private static final int CURRENT_VERSION = RELEASE_0270_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x354cfc55834e7f12L;

	public static final String DEFAULT_MEMO = "";
	private static final ByteString DEFAULT_ALIAS = ByteString.EMPTY;

	private JKey key;
	private long expiry;
	private long hbarBalance;
	private long autoRenewSecs;
	private String memo = DEFAULT_MEMO;
	private boolean deleted;
	private boolean smartContract;
	private boolean receiverSigRequired;
	private EntityId proxy;
	private long nftsOwned;
	private int number;
	private ByteString alias = DEFAULT_ALIAS;
	private int numContractKvPairs;
	private int maxAutoAssociations;
	private int usedAutoAssociations;
	private int numAssociations;
	private int numPositiveBalances;
	private long headTokenId;
	private int numTreasuryTitles;
	private long stakedToMe;
	private long stakePeriodStart;
	private long stakedNum;
//	private long stakedRealm;
	private boolean declineReward;

	// C.f. https://github.com/hashgraph/hedera-services/issues/2842; we may want to migrate
	// these per-account maps to top-level maps using the "linked-list" values idiom
	private Map<EntityNum, Long> cryptoAllowances = Collections.emptyMap();
	private Map<FcTokenAllowanceId, Long> fungibleTokenAllowances = Collections.emptyMap();
	private Set<FcTokenAllowanceId> approveForAllNfts = Collections.emptySet();

	public MerkleAccountState() {
		// RuntimeConstructable
	}

	public MerkleAccountState(final MerkleAccountState that) {
		this.key = that.key;
		this.expiry = that.expiry;
		this.hbarBalance = that.hbarBalance;
		this.autoRenewSecs = that.autoRenewSecs;
		this.memo = that.memo;
		this.deleted = that.deleted;
		this.smartContract = that.smartContract;
		this.receiverSigRequired = that.receiverSigRequired;
		this.proxy = that.proxy;
		this.number = that.number;
		this.maxAutoAssociations = that.maxAutoAssociations;
		this.usedAutoAssociations = that.usedAutoAssociations;
		this.alias = that.alias;
		this.numContractKvPairs = that.numContractKvPairs;
		this.cryptoAllowances = that.cryptoAllowances;
		this.fungibleTokenAllowances = that.fungibleTokenAllowances;
		this.approveForAllNfts = that.approveForAllNfts;
		this.numAssociations = that.numAssociations;
		this.numPositiveBalances = that.numPositiveBalances;
		this.headTokenId = that.headTokenId;
		this.nftsOwned = that.nftsOwned;
		this.numTreasuryTitles = that.numTreasuryTitles;
		this.stakedToMe = that.stakedToMe;
		this.stakePeriodStart = that.stakePeriodStart;
		this.stakedNum = that.stakedNum;
//		this.stakedRealm = that.stakedRealm;
		this.declineReward = that.declineReward;
	}

	public MerkleAccountState(
			final JKey key,
			final long expiry,
			final long hbarBalance,
			final long autoRenewSecs,
			final String memo,
			final boolean deleted,
			final boolean smartContract,
			final boolean receiverSigRequired,
			final EntityId proxy,
			final int number,
			final int maxAutoAssociations,
			final int usedAutoAssociations,
			final ByteString alias,
			final int numContractKvPairs,
			final Map<EntityNum, Long> cryptoAllowances,
			final Map<FcTokenAllowanceId, Long> fungibleTokenAllowances,
			final Set<FcTokenAllowanceId> approveForAllNfts,
			final int numAssociations,
			final int numPositiveBalances,
			final long headTokenId,
			final long nftsOwned,
			final int numTreasuryTitles,
			final long stakedToMe,
			final long stakePeriodStart,
			final long stakedNum,
//			final long stakedRealm,
			final boolean declineReward
	) {
		this.key = key;
		this.expiry = expiry;
		this.hbarBalance = hbarBalance;
		this.autoRenewSecs = autoRenewSecs;
		this.memo = Optional.ofNullable(memo).orElse(DEFAULT_MEMO);
		this.deleted = deleted;
		this.smartContract = smartContract;
		this.receiverSigRequired = receiverSigRequired;
		this.proxy = proxy;
		this.number = number;
		this.maxAutoAssociations = maxAutoAssociations;
		this.usedAutoAssociations = usedAutoAssociations;
		this.alias = Optional.ofNullable(alias).orElse(DEFAULT_ALIAS);
		this.numContractKvPairs = numContractKvPairs;
		this.cryptoAllowances = cryptoAllowances;
		this.fungibleTokenAllowances = fungibleTokenAllowances;
		this.approveForAllNfts = approveForAllNfts;
		this.numAssociations = numAssociations;
		this.numPositiveBalances = numPositiveBalances;
		this.headTokenId = headTokenId;
		this.nftsOwned = nftsOwned;
		this.numTreasuryTitles = numTreasuryTitles;
		this.stakedToMe = stakedToMe;
		this.stakePeriodStart = stakePeriodStart;
		this.stakedNum = stakedNum;
//		this.stakedRealm = stakedRealm;
		this.declineReward = declineReward;
	}

	/* --- MerkleLeaf --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public int getMinimumSupportedVersion() {
		return RELEASE_0230_VERSION;
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		key = readNullable(in, JKeySerializer::deserialize);
		expiry = in.readLong();
		hbarBalance = in.readLong();
		autoRenewSecs = in.readLong();
		memo = in.readNormalisedString(MAX_CONCEIVABLE_MEMO_UTF8_BYTES);
		deleted = in.readBoolean();
		smartContract = in.readBoolean();
		receiverSigRequired = in.readBoolean();
		proxy = readNullableSerializable(in);
		// Added in 0.16
		nftsOwned = in.readLong();
		// Added in 0.18
		if (version >= RELEASE_0250_ALPHA_VERSION) {
			maxAutoAssociations = in.readInt();
			usedAutoAssociations = in.readInt();
		} else {
			final var autoAssociationMetadata = in.readInt();
			maxAutoAssociations = getMaxAutomaticAssociationsFrom(autoAssociationMetadata);
			usedAutoAssociations = getAlreadyUsedAutomaticAssociationsFrom(autoAssociationMetadata);
		}
		number = in.readInt();
		// Added in 0.21
		alias = ByteString.copyFrom(in.readByteArray(Integer.MAX_VALUE));
		// Added in 0.22
		numContractKvPairs = in.readInt();
		if (version >= RELEASE_0230_VERSION) {
			cryptoAllowances = deserializeCryptoAllowances(in);
			fungibleTokenAllowances = deserializeFungibleTokenAllowances(in);
			approveForAllNfts = deserializeApproveForAllNftsAllowances(in);
		}
		if (version >= RELEASE_0250_ALPHA_VERSION) {
			numAssociations = in.readInt();
			numPositiveBalances = in.readInt();
			headTokenId = in.readLong();
		}
		if (version >= RELEASE_0250_VERSION) {
			numTreasuryTitles = in.readInt();
		}
		if (version >= RELEASE_0270_VERSION) {
			stakedToMe = in.readLong();
			stakePeriodStart = in.readLong();
			stakedNum = in.readLong();
//			stakedRealm = in.readLong();
			declineReward = in.readBoolean();
		}
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		writeNullable(key, out, (keyOut, dout) -> dout.write(keyOut.serialize()));
		out.writeLong(expiry);
		out.writeLong(hbarBalance);
		out.writeLong(autoRenewSecs);
		out.writeNormalisedString(memo);
		out.writeBoolean(deleted);
		out.writeBoolean(smartContract);
		out.writeBoolean(receiverSigRequired);
		writeNullableSerializable(proxy, out);
		out.writeLong(nftsOwned);
		out.writeInt(maxAutoAssociations);
		out.writeInt(usedAutoAssociations);
		out.writeInt(number);
		out.writeByteArray(alias.toByteArray());
		out.writeInt(numContractKvPairs);
		serializeCryptoAllowances(out, cryptoAllowances);
		serializeTokenAllowances(out, fungibleTokenAllowances);
		serializeApproveForAllNftsAllowances(out, approveForAllNfts);
		out.writeInt(numAssociations);
		out.writeInt(numPositiveBalances);
		out.writeLong(headTokenId);
		out.writeInt(numTreasuryTitles);
		out.writeLong(stakedToMe);
		out.writeLong(stakePeriodStart);
		out.writeLong(stakedNum);
//		out.writeLong(stakedRealm);
		out.writeBoolean(declineReward);
	}

	/* --- Copyable --- */
	public MerkleAccountState copy() {
		setImmutable(true);
		return new MerkleAccountState(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleAccountState.class != o.getClass()) {
			return false;
		}

		var that = (MerkleAccountState) o;

		return this.number == that.number &&
				this.expiry == that.expiry &&
				this.hbarBalance == that.hbarBalance &&
				this.autoRenewSecs == that.autoRenewSecs &&
				Objects.equals(this.memo, that.memo) &&
				this.deleted == that.deleted &&
				this.smartContract == that.smartContract &&
				this.receiverSigRequired == that.receiverSigRequired &&
				Objects.equals(this.proxy, that.proxy) &&
				this.nftsOwned == that.nftsOwned &&
				this.numContractKvPairs == that.numContractKvPairs &&
				this.maxAutoAssociations == that.maxAutoAssociations &&
				this.usedAutoAssociations == that.usedAutoAssociations &&
				equalUpToDecodability(this.key, that.key) &&
				Objects.equals(this.alias, that.alias) &&
				Objects.equals(this.cryptoAllowances, that.cryptoAllowances) &&
				Objects.equals(this.fungibleTokenAllowances, that.fungibleTokenAllowances) &&
				Objects.equals(this.approveForAllNfts, that.approveForAllNfts) &&
				this.numAssociations == that.numAssociations &&
				this.numPositiveBalances == that.numPositiveBalances &&
				this.headTokenId == that.headTokenId &&
				this.numTreasuryTitles == that.numTreasuryTitles &&
				this.stakedToMe == that.stakedToMe &&
				this.stakePeriodStart == that.stakePeriodStart &&
				this.stakedNum == that.stakedNum &&
//				this.stakedRealm == that.stakedRealm &&
				this.declineReward == that.declineReward;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				key,
				expiry,
				hbarBalance,
				autoRenewSecs,
				memo,
				deleted,
				smartContract,
				receiverSigRequired,
				proxy,
				nftsOwned,
				number,
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				numAssociations,
				numPositiveBalances,
				headTokenId,
				numTreasuryTitles,
				stakedToMe,
				stakePeriodStart,
				stakedNum,
//				stakedRealm,
				declineReward);
	}

	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("number", number + " <-> " + asIdLiteral(number))
				.add("key", describe(key))
				.add("expiry", expiry)
				.add("balance", hbarBalance)
				.add("autoRenewSecs", autoRenewSecs)
				.add("memo", memo)
				.add("deleted", deleted)
				.add("smartContract", smartContract)
				.add("numContractKvPairs", numContractKvPairs)
				.add("receiverSigRequired", receiverSigRequired)
				.add("proxy", proxy)
				.add("nftsOwned", nftsOwned)
				.add("alreadyUsedAutoAssociations", usedAutoAssociations)
				.add("maxAutoAssociations", maxAutoAssociations)
				.add("alias", alias.toStringUtf8())
				.add("cryptoAllowances", cryptoAllowances)
				.add("fungibleTokenAllowances", fungibleTokenAllowances)
				.add("approveForAllNfts", approveForAllNfts)
				.add("numAssociations", numAssociations)
				.add("numPositiveBalances", numPositiveBalances)
				.add("headTokenId", headTokenId)
				.add("numTreasuryTitles", numTreasuryTitles)
				.add("stakedToMe", stakedToMe)
				.add("stakePeriodStart", stakePeriodStart)
				.add("stakedNum", stakedNum)
//				.add("stakedRealm", stakedRealm)
				.add("declineReward", declineReward)
				.toString();
	}

	public int number() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}

	public void setAlias(ByteString alias) {
		this.alias = alias;
	}

	public JKey key() {
		return key;
	}

	public long expiry() {
		return expiry;
	}

	public long balance() {
		return hbarBalance;
	}

	public long autoRenewSecs() {
		return autoRenewSecs;
	}

	public String memo() {
		return memo;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public boolean isSmartContract() {
		return smartContract;
	}

	public boolean isReceiverSigRequired() {
		return receiverSigRequired;
	}

	public EntityId proxy() {
		return proxy;
	}

	public long nftsOwned() {
		return nftsOwned;
	}

	public ByteString getAlias() {
		return alias;
	}

	public void setAccountKey(JKey key) {
		assertMutable("key");
		this.key = key;
	}

	public void setExpiry(long expiry) {
		assertMutable("expiry");
		this.expiry = expiry;
	}

	public void setHbarBalance(long hbarBalance) {
		assertMutable("hbarBalance");
		this.hbarBalance = hbarBalance;
	}

	public void setAutoRenewSecs(long autoRenewSecs) {
		assertMutable("autoRenewSecs");
		this.autoRenewSecs = autoRenewSecs;
	}

	public void setMemo(String memo) {
		assertMutable("memo");
		this.memo = memo;
	}

	public void setDeleted(boolean deleted) {
		assertMutable("isSmartContract");
		this.deleted = deleted;
	}

	public void setSmartContract(boolean smartContract) {
		assertMutable("isSmartContract");
		this.smartContract = smartContract;
	}

	public void setReceiverSigRequired(boolean receiverSigRequired) {
		assertMutable("isReceiverSigRequired");
		this.receiverSigRequired = receiverSigRequired;
	}

	public void setProxy(EntityId proxy) {
		assertMutable("proxy");
		this.proxy = proxy;
	}

	public void setNftsOwned(final long nftsOwned) {
		assertMutable("nftsOwned");
		this.nftsOwned = nftsOwned;
	}

	public int getNumAssociations() {
		return numAssociations;
	}

	public void setNumAssociations(final int numAssociations) {
		assertMutable("numAssociations");
		this.numAssociations = numAssociations;
	}

	public int getNumPositiveBalances() {
		return numPositiveBalances;
	}

	public void setNumPositiveBalances(final int numPositiveBalances) {
		assertMutable("numPositiveBalances");
		this.numPositiveBalances = numPositiveBalances;
	}

	public long getHeadTokenId() {
		return headTokenId;
	}

	public void setHeadTokenId(final long headTokenId) {
		assertMutable("headTokenId");
		this.headTokenId = headTokenId;
	}

	public int getNumContractKvPairs() {
		return numContractKvPairs;
	}

	public void setNumContractKvPairs(int numContractKvPairs) {
		assertMutable("numContractKvPairs");
		this.numContractKvPairs = numContractKvPairs;
	}

	public int getMaxAutomaticAssociations() {
		return maxAutoAssociations;
	}

	public int getUsedAutomaticAssociations() {
		return usedAutoAssociations;
	}

	public void setMaxAutomaticAssociations(int maxAutomaticAssociations) {
		assertMutable("maxAutomaticAssociations");
		this.maxAutoAssociations = maxAutomaticAssociations;
	}

	public void setUsedAutomaticAssociations(int usedAutoAssociations) {
		assertMutable("usedAutomaticAssociations");
		this.usedAutoAssociations = usedAutoAssociations;
	}

	public Map<EntityNum, Long> getCryptoAllowances() {
		return Collections.unmodifiableMap(cryptoAllowances);
	}

	public void setCryptoAllowances(final SortedMap<EntityNum, Long> cryptoAllowances) {
		assertMutable("cryptoAllowances");
		this.cryptoAllowances = cryptoAllowances;
	}

	public Map<EntityNum, Long> getCryptoAllowancesUnsafe() {
		return cryptoAllowances;
	}

	public void setCryptoAllowancesUnsafe(final Map<EntityNum, Long> cryptoAllowances) {
		assertMutable("cryptoAllowances");
		this.cryptoAllowances = cryptoAllowances;
	}

	public boolean isTokenTreasury() {
		return numTreasuryTitles > 0;
	}

	public int getNumTreasuryTitles() {
		return numTreasuryTitles;
	}

	public void setNumTreasuryTitles(final int numTreasuryTitles) {
		assertMutable("numTreasuryTitles");
		this.numTreasuryTitles = numTreasuryTitles;
	}

	public Set<FcTokenAllowanceId> getApproveForAllNfts() {
		return Collections.unmodifiableSet(approveForAllNfts);
	}

	public void setApproveForAllNfts(final Set<FcTokenAllowanceId> approveForAllNfts) {
		assertMutable("ApproveForAllNfts");
		this.approveForAllNfts = approveForAllNfts;
	}

	public Set<FcTokenAllowanceId> getApproveForAllNftsUnsafe() {
		return approveForAllNfts;
	}

	public Map<FcTokenAllowanceId, Long> getFungibleTokenAllowances() {
		return Collections.unmodifiableMap(fungibleTokenAllowances);
	}

	public void setFungibleTokenAllowances(final SortedMap<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
		assertMutable("fungibleTokenAllowances");
		this.fungibleTokenAllowances = fungibleTokenAllowances;
	}

	public Map<FcTokenAllowanceId, Long> getFungibleTokenAllowancesUnsafe() {
		return fungibleTokenAllowances;
	}

	public void setFungibleTokenAllowancesUnsafe(final Map<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
		assertMutable("fungibleTokenAllowances");
		this.fungibleTokenAllowances = fungibleTokenAllowances;
	}

	public long getStakedToMe() {
		return stakedToMe;
	}

	public void setStakedToMe(final long stakedToMe) {
		assertMutable("stakedToMe");
		this.stakedToMe = stakedToMe;
	}

	public long getStakePeriodStart() {
		return stakePeriodStart;
	}

	public void setStakePeriodStart(final long stakePeriodStart) {
		assertMutable("stakePeriodStart");
		this.stakePeriodStart = stakePeriodStart;
	}

	public long getStakedNum() {
		return stakedNum;
	}

	public void setStakedNum(final long stakedNum) {
		assertMutable("stakedNum");
		this.stakedNum = stakedNum;
	}

//	public long getStakedRealm() {
//		return stakedRealm;
//	}
//
//	public void setStakedRealm(final long stakedRealm) {
//		assertMutable("stakedRealm");
//		this.stakedRealm = stakedRealm;
//	}

	public boolean isDeclineReward() {
		return declineReward;
	}

	public void setDeclineReward(final boolean declineReward) {
		assertMutable("declineReward");
		this.declineReward = declineReward;
	}

	private void assertMutable(String proximalField) {
		if (isImmutable()) {
			throw new MutabilityException("Cannot set " + proximalField + " on an immutable account state!");
		}
	}
}
