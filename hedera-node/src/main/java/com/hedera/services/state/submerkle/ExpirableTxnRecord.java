package com.hedera.services.state.submerkle;

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
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.IoUtils;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcqueue.FCQueueElement;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.serdes.IoUtils.staticReadNullable;
import static com.hedera.services.state.serdes.IoUtils.staticReadNullableSerializable;
import static com.hedera.services.state.serdes.IoUtils.staticWriteNullable;
import static com.hedera.services.state.serdes.IoUtils.staticWriteNullableSerializable;
import static com.hedera.services.state.serdes.IoUtils.staticWriteNullableString;
import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hedera.services.utils.SerializationUtils.deserializeCryptoAllowances;
import static com.hedera.services.utils.SerializationUtils.deserializeFungibleTokenAllowances;
import static com.hedera.services.utils.SerializationUtils.serializeCryptoAllowances;
import static com.hedera.services.utils.SerializationUtils.serializeTokenAllowances;
import static java.util.stream.Collectors.joining;

public class ExpirableTxnRecord implements FCQueueElement {
	public static final long UNKNOWN_SUBMITTING_MEMBER = -1;
	public static final long MISSING_PARENT_CONSENSUS_TIMESTAMP = -1;
	public static final short NO_CHILD_TRANSACTIONS = 0;

	static final List<EntityId> NO_TOKENS = null;
	static final List<CurrencyAdjustments> NO_TOKEN_ADJUSTMENTS = null;
	static final List<NftAdjustments> NO_NFT_TOKEN_ADJUSTMENTS = null;
	static final List<FcAssessedCustomFee> NO_CUSTOM_FEES = null;
	static final EntityId NO_SCHEDULE_REF = null;
	static final List<FcTokenAssociation> NO_NEW_TOKEN_ASSOCIATIONS = Collections.emptyList();

	private static final byte[] MISSING_TXN_HASH = new byte[0];

	static final int RELEASE_0230_VERSION = 7;
	static final int RELEASE_0250_VERSION = 8;
	static final int MERKLE_VERSION = RELEASE_0250_VERSION;

	static final int MAX_MEMO_BYTES = 32 * 1_024;
	static final int MAX_TXN_HASH_BYTES = 1_024;
	static final int MAX_INVOLVED_TOKENS = 10;
	static final int MAX_ASSESSED_CUSTOM_FEES_CHANGES = 20;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8b9ede7ca8d8db93L;
	public static final ByteString MISSING_ALIAS = ByteString.EMPTY;

	static DomainSerdes serdes = new DomainSerdes();

	private long expiry;
	private long submittingMember = UNKNOWN_SUBMITTING_MEMBER;

	private long fee;
	private long packedParentConsensusTime = MISSING_PARENT_CONSENSUS_TIMESTAMP;
	private short numChildRecords = NO_CHILD_TRANSACTIONS;
	private Hash hash;
	private TxnId txnId;
	private byte[] txnHash = MISSING_TXN_HASH;
	private String memo;
	private TxnReceipt receipt;
	private RichInstant consensusTime;
	private CurrencyAdjustments hbarAdjustments;
	private EvmFnResult contractCallResult;
	private EvmFnResult contractCreateResult;
	// IMPORTANT: This class depends on the invariant that if any of the
	// three token-related lists below (tokens, tokenAdjustments, and
	// nftTokenAdjustments) is non-null, then it has the same length as any
	// other non-null list. This would not be necessary if we provided the
	// class with information on the fungibility of the token types---and
	// this information is always available when the Builder is constructed.
	private List<EntityId> tokens = NO_TOKENS;
	private List<CurrencyAdjustments> tokenAdjustments = NO_TOKEN_ADJUSTMENTS;
	private List<NftAdjustments> nftTokenAdjustments = NO_NFT_TOKEN_ADJUSTMENTS;
	private EntityId scheduleRef = NO_SCHEDULE_REF;
	private List<FcAssessedCustomFee> assessedCustomFees = NO_CUSTOM_FEES;
	private List<FcTokenAssociation> newTokenAssociations = NO_NEW_TOKEN_ASSOCIATIONS;
	private ByteString alias = MISSING_ALIAS;
	private Map<EntityNum, Map<EntityNum, Long>> cryptoAllowances = Collections.emptyMap();
	private Map<EntityNum, Map<FcTokenAllowanceId, Long>> fungibleTokenAllowances = Collections.emptyMap();

	@Override
	public void release() {
		/* No-op */
	}

	public ExpirableTxnRecord() {
		/* RuntimeConstructable */
	}

	public ExpirableTxnRecord(Builder builder) {
		this.receipt = (builder.receiptBuilder != null) ? builder.receiptBuilder.build() : builder.receipt;
		this.txnHash = builder.txnHash;
		this.txnId = builder.txnId;
		this.consensusTime = builder.consensusTime;
		this.memo = builder.memo;
		this.fee = builder.fee;
		this.hbarAdjustments = builder.hbarAdjustments;
		this.contractCallResult = builder.contractCallResult;
		this.contractCreateResult = builder.contractCreateResult;
		this.tokens = builder.tokens;
		this.tokenAdjustments = builder.tokenAdjustments;
		this.nftTokenAdjustments = builder.nftTokenAdjustments;
		this.scheduleRef = builder.scheduleRef;
		this.assessedCustomFees = builder.assessedCustomFees;
		this.newTokenAssociations = builder.newTokenAssociations;
		this.packedParentConsensusTime = builder.packedParentConsensusTime;
		this.numChildRecords = builder.numChildRecords;
		this.alias = builder.alias;
		this.cryptoAllowances = builder.cryptoAllowances;
		this.fungibleTokenAllowances = builder.fungibleTokenAllowances;
	}

	/* --- Object --- */
	@Override
	public String toString() {
		var helper = MoreObjects.toStringHelper(this)
				.omitNullValues()
				.add("numChildRecords", numChildRecords)
				.add("receipt", receipt)
				.add("fee", fee)
				.add("txnHash", CommonUtils.hex(txnHash))
				.add("txnId", txnId)
				.add("consensusTimestamp", consensusTime)
				.add("expiry", expiry)
				.add("submittingMember", submittingMember)
				.add("memo", memo)
				.add("contractCreation", contractCreateResult)
				.add("contractCall", contractCallResult)
				.add("hbarAdjustments", hbarAdjustments)
				.add("scheduleRef", scheduleRef)
				.add("alias", alias.toStringUtf8());

		if (packedParentConsensusTime != MISSING_PARENT_CONSENSUS_TIMESTAMP) {
			helper.add("parentConsensusTime", Instant.ofEpochSecond(
					BitPackUtils.unsignedHighOrder32From(packedParentConsensusTime),
					BitPackUtils.signedLowOrder32From(packedParentConsensusTime)));
		}

		if (tokens != NO_TOKENS) {
			int n = tokens.size();
			var readable = IntStream.range(0, n)
					.mapToObj(i -> String.format(
							"%s(%s)",
							tokens.get(i).toAbbrevString(),
							reprOfNonEmptyChange(i, tokenAdjustments, nftTokenAdjustments)))
					.collect(joining(", "));
			helper.add("tokenAdjustments", readable);
		}

		if (assessedCustomFees != NO_CUSTOM_FEES) {
			var readable = assessedCustomFees.stream().map(
							assessedCustomFee -> String.format("(%s)", assessedCustomFee))
					.collect(joining(", "));
			helper.add("assessedCustomFees", readable);
		}

		if (newTokenAssociations != NO_NEW_TOKEN_ASSOCIATIONS) {
			var readable = newTokenAssociations.stream().map(
							newTokenAssociation -> String.format("(%s)", newTokenAssociation))
					.collect(joining(", "));
			helper.add("newTokenAssociations", readable);
		}

		if (cryptoAllowances.size() != 0) {
			final var readable = "[" + cryptoAllowances.entrySet().stream().map(
							ownerMap -> String.format("%s", ownerMap.getValue().entrySet().stream().map(
									allowance -> String.format("{owner : %s, spender : %s, allowance : %d}",
											ownerMap.getKey(),
											allowance.getKey(),
											allowance.getValue())).collect(joining(", "))))
					.collect(joining(", ")) + "]";
			helper.add("cryptoAllowances", readable);
		}

		if (fungibleTokenAllowances.size() != 0) {
			final var readable = "[" + fungibleTokenAllowances.entrySet().stream().map(
							ownerMap -> String.format("%s", ownerMap.getValue().entrySet().stream().map(
									allowance -> String.format("{owner : %s, token : %s, spender : %s, allowance : %d}",
											ownerMap.getKey(),
											allowance.getKey().getTokenNum().toString(),
											allowance.getKey().getSpenderNum().toString(),
											allowance.getValue())).collect(joining(", "))))
					.collect(joining(", ")) + "]";
			helper.add("fungibleTokenAllowances", readable);
		}

		return helper.toString();
	}

	private String reprOfNonEmptyChange(
			final int i,
			final List<CurrencyAdjustments> tokenAdjustments,
			final List<NftAdjustments> nftTokenAdjustments
	) {
		final var fungibleAdjust = tokenAdjustments.get(i);
		return fungibleAdjust.isEmpty() ? nftTokenAdjustments.get(i).toString() : fungibleAdjust.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || ExpirableTxnRecord.class != o.getClass()) {
			return false;
		}
		var that = (ExpirableTxnRecord) o;
		return this.fee == that.fee &&
				this.numChildRecords == that.numChildRecords &&
				this.packedParentConsensusTime == that.packedParentConsensusTime &&
				this.expiry == that.expiry &&
				this.submittingMember == that.submittingMember &&
				Objects.equals(this.receipt, that.receipt) &&
				Arrays.equals(this.txnHash, that.txnHash) &&
				this.txnId.equals(that.txnId) &&
				Objects.equals(this.consensusTime, that.consensusTime) &&
				Objects.equals(this.memo, that.memo) &&
				Objects.equals(this.contractCallResult, that.contractCallResult) &&
				Objects.equals(this.contractCreateResult, that.contractCreateResult) &&
				Objects.equals(this.hbarAdjustments, that.hbarAdjustments) &&
				Objects.equals(this.tokens, that.tokens) &&
				Objects.equals(this.tokenAdjustments, that.tokenAdjustments) &&
				Objects.equals(this.nftTokenAdjustments, that.nftTokenAdjustments) &&
				Objects.equals(this.assessedCustomFees, that.assessedCustomFees) &&
				Objects.equals(this.newTokenAssociations, that.newTokenAssociations) &&
				Objects.equals(this.alias, that.alias) &&
				Objects.equals(this.cryptoAllowances, that.cryptoAllowances) &&
				Objects.equals(this.fungibleTokenAllowances, that.fungibleTokenAllowances);
	}

	@Override
	public int hashCode() {
		var result = Objects.hash(
				receipt,
				txnId,
				consensusTime,
				memo,
				fee,
				contractCallResult,
				contractCreateResult,
				hbarAdjustments,
				expiry,
				submittingMember,
				tokens,
				tokenAdjustments,
				nftTokenAdjustments,
				scheduleRef,
				assessedCustomFees,
				newTokenAssociations,
				numChildRecords,
				packedParentConsensusTime,
				alias,
				cryptoAllowances,
				fungibleTokenAllowances);
		return result * 31 + Arrays.hashCode(txnHash);
	}

	/* --- SelfSerializable --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public int getMinimumSupportedVersion() {
		return RELEASE_0230_VERSION;
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		staticWriteNullableSerializable(receipt, out);

		out.writeByteArray(txnHash);

		staticWriteNullableSerializable(txnId, out);

		staticWriteNullable(consensusTime, out, RichInstant::serialize);
		staticWriteNullableString(memo, out);

		out.writeLong(this.fee);

		staticWriteNullableSerializable(hbarAdjustments, out);
		staticWriteNullableSerializable(contractCallResult, out);
		staticWriteNullableSerializable(contractCreateResult, out);

		out.writeLong(expiry);
		out.writeLong(submittingMember);

		out.writeSerializableList(tokens, true, true);
		out.writeSerializableList(tokenAdjustments, true, true);

		staticWriteNullableSerializable(scheduleRef, out);
		out.writeSerializableList(nftTokenAdjustments, true, true);
		out.writeSerializableList(assessedCustomFees, true, true);
		out.writeSerializableList(newTokenAssociations, true, true);

		if (numChildRecords != NO_CHILD_TRANSACTIONS) {
			out.writeBoolean(true);
			out.writeShort(numChildRecords);
		} else {
			out.writeBoolean(false);
		}

		if (packedParentConsensusTime != MISSING_PARENT_CONSENSUS_TIMESTAMP) {
			out.writeBoolean(true);
			out.writeLong(packedParentConsensusTime);
		} else {
			out.writeBoolean(false);
		}
		out.writeByteArray(alias.toByteArray());

		serializeAllowanceMaps(out, cryptoAllowances, fungibleTokenAllowances);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		receipt = staticReadNullableSerializable(in);
		txnHash = in.readByteArray(MAX_TXN_HASH_BYTES);
		txnId = staticReadNullableSerializable(in);
		consensusTime = staticReadNullable(in, RichInstant::from);
		memo = IoUtils.staticReadNullableString(in, MAX_MEMO_BYTES);
		fee = in.readLong();
		hbarAdjustments = staticReadNullableSerializable(in);
		contractCallResult = staticReadNullableSerializable(in);
		contractCreateResult = staticReadNullableSerializable(in);
		expiry = in.readLong();
		submittingMember = in.readLong();
		// Added in 0.7
		tokens = in.readSerializableList(MAX_INVOLVED_TOKENS);
		tokenAdjustments = in.readSerializableList(MAX_INVOLVED_TOKENS);
		// Added in 0.8
		scheduleRef = staticReadNullableSerializable(in);
		// Added in 0.16
		nftTokenAdjustments = in.readSerializableList(MAX_INVOLVED_TOKENS);
		assessedCustomFees = in.readSerializableList(MAX_ASSESSED_CUSTOM_FEES_CHANGES);
		// Added in 0.18
		newTokenAssociations = in.readSerializableList(Integer.MAX_VALUE);
		// Added in 0.21
		final var hasChildRecords = in.readBoolean();
		if (hasChildRecords) {
			numChildRecords = in.readShort();
		}
		// Added in 0.21
		final var hasParentConsensusTime = in.readBoolean();
		if (hasParentConsensusTime) {
			packedParentConsensusTime = in.readLong();
		}
		// Added in 0.21
		alias = ByteString.copyFrom(in.readByteArray(Integer.MAX_VALUE));
		// Added in 0.23
		deserializeAllowanceMaps(in, version);
	}

	private void deserializeAllowanceMaps(SerializableDataInputStream in, final int version) throws IOException {
		if (version < RELEASE_0250_VERSION) {
			// In release 0.24.x three _always-empty_ map sizes were serialized here
			in.readInt();
			in.readInt();
			in.readInt();
		} else {
			var numCryptoAllowances = in.readInt();
			if (numCryptoAllowances > 0) {
				cryptoAllowances = new TreeMap<>();
			}
			while (numCryptoAllowances-- > 0) {
				final EntityNum owner = EntityNum.fromLong(in.readLong());
				cryptoAllowances.put(owner, deserializeCryptoAllowances(in));
			}

			var numTokenAllowances = in.readInt();
			if (numTokenAllowances > 0) {
				fungibleTokenAllowances = new TreeMap<>();
			}
			while (numTokenAllowances-- > 0) {
				final EntityNum owner = EntityNum.fromLong(in.readLong());
				fungibleTokenAllowances.put(owner, deserializeFungibleTokenAllowances(in));
			}
		}
	}

	private void serializeAllowanceMaps(
			final SerializableDataOutputStream out,
			final Map<EntityNum, Map<EntityNum, Long>> cryptoAllowances,
			final Map<EntityNum, Map<FcTokenAllowanceId, Long>> fungibleTokenAllowances) throws IOException {
		out.writeInt(cryptoAllowances.size());
		for (var cryptoAllowance : cryptoAllowances.entrySet()) {
			out.writeLong(cryptoAllowance.getKey().longValue());
			serializeCryptoAllowances(out, cryptoAllowance.getValue());
		}
		out.writeInt(fungibleTokenAllowances.size());
		for (var tokenAllowance : fungibleTokenAllowances.entrySet()) {
			out.writeLong(tokenAllowance.getKey().longValue());
			serializeTokenAllowances(out, tokenAllowance.getValue());
		}
	}

	@Override
	public Hash getHash() {
		return this.hash;
	}

	@Override
	public void setHash(Hash hash) {
		this.hash = hash;
	}

	/* --- Object --- */
	public EntityId getScheduleRef() {
		return scheduleRef;
	}

	public List<EntityId> getTokens() {
		return tokens;
	}

	public List<CurrencyAdjustments> getTokenAdjustments() {
		return tokenAdjustments;
	}

	public List<NftAdjustments> getNftTokenAdjustments() {
		return nftTokenAdjustments;
	}

	public TxnReceipt getReceipt() {
		return receipt;
	}

	public ResponseCodeEnum getEnumStatus() {
		return receipt.getEnumStatus();
	}

	public byte[] getTxnHash() {
		return txnHash;
	}

	public TxnId getTxnId() {
		return txnId;
	}

	public RichInstant getConsensusTime() {
		return consensusTime;
	}

	public long getConsensusSecond() {
		return consensusTime.getSeconds();
	}

	public String getMemo() {
		return memo;
	}

	public long getFee() {
		return fee;
	}

	public EvmFnResult getContractCallResult() {
		return contractCallResult;
	}

	public EvmFnResult getContractCreateResult() {
		return contractCreateResult;
	}

	public CurrencyAdjustments getHbarAdjustments() {
		return hbarAdjustments;
	}

	public long getExpiry() {
		return expiry;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public long getSubmittingMember() {
		return submittingMember;
	}

	public void setSubmittingMember(long submittingMember) {
		this.submittingMember = submittingMember;
	}

	public List<FcAssessedCustomFee> getCustomFeesCharged() {
		return assessedCustomFees;
	}

	public List<FcTokenAssociation> getNewTokenAssociations() {
		return newTokenAssociations;
	}

	public short getNumChildRecords() {
		return numChildRecords;
	}

	public void setNumChildRecords(final short numChildRecords) {
		this.numChildRecords = numChildRecords;
	}

	public long getPackedParentConsensusTime() {
		return packedParentConsensusTime;
	}

	public void setPackedParentConsensusTime(final long packedParentConsensusTime) {
		this.packedParentConsensusTime = packedParentConsensusTime;
	}

	public ByteString getAlias() {
		return alias;
	}

	/* --- FastCopyable --- */
	@Override
	public boolean isImmutable() {
		return true;
	}

	@Override
	public ExpirableTxnRecord copy() {
		return this;
	}

	public static List<TransactionRecord> allToGrpc(List<ExpirableTxnRecord> records) {
		return records.stream()
				.map(ExpirableTxnRecord::asGrpc)
				.toList();
	}

	public TransactionRecord asGrpc() {
		var grpc = TransactionRecord.newBuilder();

		grpc.setTransactionFee(fee);
		if (receipt != null) {
			grpc.setReceipt(TxnReceipt.convert(receipt));
		}
		if (txnId != null) {
			grpc.setTransactionID(txnId.toGrpc());
		}
		if (consensusTime != null) {
			grpc.setConsensusTimestamp(consensusTime.toGrpc());
		}
		if (memo != null) {
			grpc.setMemo(memo);
		}
		if (txnHash != null && txnHash.length > 0) {
			grpc.setTransactionHash(ByteString.copyFrom(txnHash));
		}
		if (hbarAdjustments != null) {
			grpc.setTransferList(hbarAdjustments.toGrpc());
		}
		if (contractCallResult != null) {
			grpc.setContractCallResult(contractCallResult.toGrpc());
		}
		if (contractCreateResult != null) {
			grpc.setContractCreateResult(contractCreateResult.toGrpc());
		}
		if (tokens != NO_TOKENS) {
			setGrpcTokens(grpc, tokens, tokenAdjustments, nftTokenAdjustments);
		}
		if (scheduleRef != NO_SCHEDULE_REF) {
			grpc.setScheduleRef(scheduleRef.toGrpcScheduleId());
		}
		if (assessedCustomFees != NO_CUSTOM_FEES) {
			grpc.addAllAssessedCustomFees(
					assessedCustomFees.stream().map(FcAssessedCustomFee::toGrpc).toList());
		}
		if (newTokenAssociations != NO_NEW_TOKEN_ASSOCIATIONS) {
			grpc.addAllAutomaticTokenAssociations(
					newTokenAssociations.stream().map(FcTokenAssociation::toGrpc).toList());
		}
		if (alias != MISSING_ALIAS) {
			grpc.setAlias(alias);
		}
		if (packedParentConsensusTime != MISSING_PARENT_CONSENSUS_TIMESTAMP) {
			grpc.setParentConsensusTimestamp(asTimestamp(packedParentConsensusTime));
		}

		if (cryptoAllowances.size() != 0) {
			for (var entry : cryptoAllowances.entrySet()) {
				final var owner = entry.getKey();
				final var cryptoAllowancesForThisOwner = entry.getValue();
				for (var allowance : cryptoAllowancesForThisOwner.entrySet()) {
					final var cryptoAllowance = CryptoAllowance.newBuilder()
							.setOwner(owner.toGrpcAccountId())
							.setSpender(allowance.getKey().toGrpcAccountId())
							.setAmount(allowance.getValue())
							.build();
					grpc.addCryptoAdjustments(cryptoAllowance);
				}
			}
		}

		if (fungibleTokenAllowances.size() != 0) {
			for (var entry : fungibleTokenAllowances.entrySet()) {
				final var owner = entry.getKey();
				final var tokenAllowancesForThisOwner = entry.getValue();
				for (var allowance : tokenAllowancesForThisOwner.entrySet()) {
					final var allowanceId = allowance.getKey();
					final var tokenAllowance = TokenAllowance.newBuilder()
							.setOwner(owner.toGrpcAccountId())
							.setTokenId(allowanceId.getTokenNum().toGrpcTokenId())
							.setSpender(allowanceId.getSpenderNum().toGrpcAccountId())
							.setAmount(allowance.getValue())
							.build();
					grpc.addTokenAdjustments(tokenAllowance);
				}
			}
		}

		return grpc.build();
	}

	private static void setGrpcTokens(TransactionRecord.Builder grpcBuilder,
			final List<EntityId> tokens,
			final List<CurrencyAdjustments> tokenAdjustments,
			final List<NftAdjustments> nftTokenAdjustments) {
		for (int i = 0, n = tokens.size(); i < n; i++) {
			var tokenTransferList = TokenTransferList.newBuilder()
					.setToken(tokens.get(i).toGrpcTokenId());
			if (tokenAdjustments != null && !tokenAdjustments.isEmpty()) {
				tokenTransferList.addAllTransfers(tokenAdjustments.get(i).toGrpc().getAccountAmountsList());
			}
			if (nftTokenAdjustments != null && !nftTokenAdjustments.isEmpty()) {
				tokenTransferList.addAllNftTransfers(nftTokenAdjustments.get(i).toGrpc().getNftTransfersList());
			}
			grpcBuilder.addTokenTransferLists(tokenTransferList);
		}
	}

	public static Builder newBuilder() {
		return new Builder();
	}

	public static class Builder {
		private TxnReceipt receipt;
		private TxnReceipt.Builder receiptBuilder;

		private byte[] txnHash;
		private TxnId txnId;
		private RichInstant consensusTime;
		private String memo;
		private long fee;
		private long packedParentConsensusTime = MISSING_PARENT_CONSENSUS_TIMESTAMP;
		private short numChildRecords = NO_CHILD_TRANSACTIONS;
		private CurrencyAdjustments hbarAdjustments;
		private EvmFnResult contractCallResult;
		private EvmFnResult contractCreateResult;
		private List<EntityId> tokens;
		private List<CurrencyAdjustments> tokenAdjustments;
		private List<NftAdjustments> nftTokenAdjustments;
		private EntityId scheduleRef;
		private List<FcAssessedCustomFee> assessedCustomFees;
		private List<FcTokenAssociation> newTokenAssociations = NO_NEW_TOKEN_ASSOCIATIONS;
		private ByteString alias = MISSING_ALIAS;
		private Map<EntityNum, Map<EntityNum, Long>> cryptoAllowances = Collections.emptyMap();
		private Map<EntityNum, Map<FcTokenAllowanceId, Long>> fungibleTokenAllowances = Collections.emptyMap();

		private boolean onlyExternalizedIfSuccessful = false;

		public Builder setFee(long fee) {
			this.fee = fee;
			return this;
		}

		public Builder setTxnId(TxnId txnId) {
			this.txnId = txnId;
			return this;
		}

		public Builder setTxnHash(byte[] txnHash) {
			this.txnHash = txnHash;
			return this;
		}

		public Builder setMemo(String memo) {
			this.memo = memo;
			return this;
		}

		public Builder setReceipt(TxnReceipt receipt) {
			this.receipt = receipt;
			return this;
		}

		public Builder setReceiptBuilder(TxnReceipt.Builder receiptBuilder) {
			this.receiptBuilder = receiptBuilder;
			return this;
		}

		public Builder setConsensusTime(RichInstant consensusTime) {
			this.consensusTime = consensusTime;
			return this;
		}

		public Builder setHbarAdjustments(CurrencyAdjustments hbarAdjustments) {
			this.hbarAdjustments = hbarAdjustments;
			return this;
		}

		public Builder setContractCallResult(EvmFnResult contractCallResult) {
			this.contractCallResult = contractCallResult;
			return this;
		}

		public Builder setContractCreateResult(EvmFnResult contractCreateResult) {
			this.contractCreateResult = contractCreateResult;
			return this;
		}

		public Builder setTokens(List<EntityId> tokens) {
			this.tokens = tokens;
			return this;
		}

		public Builder setTokenAdjustments(List<CurrencyAdjustments> tokenAdjustments) {
			this.tokenAdjustments = tokenAdjustments;
			return this;
		}

		public Builder setNftTokenAdjustments(List<NftAdjustments> nftTokenAdjustments) {
			this.nftTokenAdjustments = nftTokenAdjustments;
			return this;
		}

		public Builder setScheduleRef(EntityId scheduleRef) {
			this.scheduleRef = scheduleRef;
			return this;
		}

		public Builder setAssessedCustomFees(List<FcAssessedCustomFee> assessedCustomFees) {
			this.assessedCustomFees = assessedCustomFees;
			return this;
		}

		public Builder setNewTokenAssociations(List<FcTokenAssociation> newTokenAssociations) {
			this.newTokenAssociations = newTokenAssociations;
			return this;
		}

		public Builder setParentConsensusTime(final Instant consTime) {
			this.packedParentConsensusTime = packedTime(consTime.getEpochSecond(), consTime.getNano());
			return this;
		}

		public Builder setNumChildRecords(final short numChildRecords) {
			this.numChildRecords = numChildRecords;
			return this;
		}

		public Builder setAlias(ByteString alias) {
			this.alias = alias;
			return this;
		}

		public Builder setCryptoAllowances(Map<EntityNum, Map<EntityNum, Long>> cryptoAllowances) {
			this.cryptoAllowances = cryptoAllowances;
			return this;
		}

		public Builder setFungibleTokenAllowances(
				Map<EntityNum, Map<FcTokenAllowanceId, Long>> fungibleTokenAllowances) {
			this.fungibleTokenAllowances = fungibleTokenAllowances;
			return this;
		}

		public ExpirableTxnRecord build() {
			return new ExpirableTxnRecord(this);
		}

		public Builder reset() {
			fee = 0;
			txnId = null;
			txnHash = MISSING_TXN_HASH;
			memo = null;
			receipt = null;
			consensusTime = null;

			nullOutSideEffectFields(true);

			return this;
		}

		public void revert() {
			if (receiptBuilder == null) {
				throw new IllegalStateException("Cannot revert a record with a built receipt");
			}
			receiptBuilder.revert();
			nullOutSideEffectFields(false);
		}

		public void excludeHbarChangesFrom(final ExpirableTxnRecord.Builder that) {
			if (that.hbarAdjustments == null) {
				return;
			}

			final var adjustsHere = this.hbarAdjustments.hbars.length;
			final var adjustsThere = that.hbarAdjustments.hbars.length;
			final var maxAdjusts = adjustsHere + adjustsThere;
			final var changedHere = this.hbarAdjustments.accountNums;
			final var changedThere = that.hbarAdjustments.accountNums;
			final var maxAccountCodes = changedHere.length + changedThere.length;


			final var netAdjustsHere = new long[maxAdjusts];
			final long[] netChanged = new long[maxAccountCodes];

			var i = 0;
			var j = 0;
			var k = 0;
			while (i < adjustsHere && j < adjustsThere) {
				final var iId = changedHere[i];
				final var jId = changedThere[j];
				final var cmp = Long.compare(iId, jId);
				if (cmp == 0) {
					final var net = this.hbarAdjustments.hbars[i++] - that.hbarAdjustments.hbars[j++];
					if (net != 0) {
						netAdjustsHere[k] = net;
						netChanged[k++] = iId;
					}
				} else if (cmp < 0) {
					netAdjustsHere[k] = this.hbarAdjustments.hbars[i++];
					netChanged[k++] = iId;
				} else {
					netAdjustsHere[k] = -that.hbarAdjustments.hbars[j++];
					netChanged[k++] = jId;
				}
			}
			/* Note that at most one of these loops can iterate a non-zero number of times,
			 * since if both did we could not have exited the prior loop. */
			while (i < adjustsHere) {
				final var iId = changedHere[i];
				netAdjustsHere[k] = this.hbarAdjustments.hbars[i++];
				netChanged[k++] = iId;
			}
			while (j < adjustsThere) {
				final var jId = changedThere[j];
				netAdjustsHere[k] = -that.hbarAdjustments.hbars[j++];
				netChanged[k++] = jId;
			}

			this.hbarAdjustments.hbars = Arrays.copyOfRange(netAdjustsHere, 0, k);
			this.hbarAdjustments.accountNums = Arrays.copyOfRange(netChanged, 0, k);
		}

		private void nullOutSideEffectFields(boolean removeCallResult) {
			hbarAdjustments = null;
			contractCreateResult = null;
			tokens = NO_TOKENS;
			tokenAdjustments = NO_TOKEN_ADJUSTMENTS;
			nftTokenAdjustments = NO_NFT_TOKEN_ADJUSTMENTS;
			scheduleRef = NO_SCHEDULE_REF;
			assessedCustomFees = NO_CUSTOM_FEES;
			newTokenAssociations = NO_NEW_TOKEN_ASSOCIATIONS;
			alias = MISSING_ALIAS;
			/*- if this is a revert of a child record we want to have contractCallResult -*/
			if (removeCallResult) {
				contractCallResult = null;
			}
			cryptoAllowances = Collections.emptyMap();
			fungibleTokenAllowances = Collections.emptyMap();
		}

		public CurrencyAdjustments getHbarAdjustments() {
			return hbarAdjustments;
		}

		public EvmFnResult getContractCallResult() {
			return contractCallResult;
		}

		public EvmFnResult getContractCreateResult() {
			return contractCreateResult;
		}

		public List<EntityId> getTokens() {
			return tokens;
		}

		public List<CurrencyAdjustments> getTokenAdjustments() {
			return tokenAdjustments;
		}

		public List<NftAdjustments> getNftTokenAdjustments() {
			return nftTokenAdjustments;
		}

		public EntityId getScheduleRef() {
			return scheduleRef;
		}

		public List<FcAssessedCustomFee> getAssessedCustomFees() {
			return assessedCustomFees;
		}

		public List<FcTokenAssociation> getNewTokenAssociations() {
			return newTokenAssociations;
		}

		public TxnReceipt.Builder getReceiptBuilder() {
			return receiptBuilder;
		}

		public TxnId getTxnId() {
			return txnId;
		}

		public ByteString getAlias() {
			return alias;
		}

		public boolean shouldNotBeExternalized() {
			return onlyExternalizedIfSuccessful &&
					!TxnReceipt.SUCCESS_LITERAL.equals(receiptBuilder.getStatus());
		}

		public long getFee() {
			return fee;
		}

		public void onlyExternalizeIfSuccessful() {
			onlyExternalizedIfSuccessful = true;
		}
	}

	/* --- Only used by unit tests --- */
	public void setCryptoAllowances(final Map<EntityNum, Map<EntityNum, Long>> cryptoAllowances) {
		this.cryptoAllowances = cryptoAllowances;
	}

	public void setFungibleTokenAllowances(final Map<EntityNum, Map<FcTokenAllowanceId, Long>> fungibleTokenAllowances) {
		this.fungibleTokenAllowances = fungibleTokenAllowances;
	}
}