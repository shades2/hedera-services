package com.hedera.services.ledger;

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
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

/**
 * Process object that encapsulates a balance change, either ℏ or token unit .
 *
 * Includes an optional override for the {@link ResponseCodeEnum} to be used
 * in the case that the change is determined to result to an insufficient balance;
 * and a field to contain the new balance that will result from the change.
 * (This field is helpful to simplify work done in {@link HederaLedger}.)
 *
 * The {@code tokenId} and {@code accountId} fields are temporary, needed to interact with the
 * {@code BackingAccounts} and {@code BackingTokenRels} components whose APIs still use gRPC types.
 */
public class BalanceChange {
	static final TokenID NO_TOKEN_FOR_HBAR_ADJUST = TokenID.getDefaultInstance();

	private Id token;

	private Id account;
	private long originalUnits;
	private long newBalance;
	private long aggregatedUnits;
	private long allowanceUnits;
	private boolean exemptFromCustomFees = false;
	private NftId nftId = null;
	private TokenID tokenId = null;
	private AccountID accountId;
	private AccountID counterPartyAccountId = null;
	private ResponseCodeEnum codeForInsufficientBalance;
	private ByteString alias;
	private int expectedDecimals = -1;
	private boolean isApprovedAllowance = false;
	private AccountID payerID = null;

	public static BalanceChange changingHbar(AccountAmount aa, AccountID payerID) {
		return new BalanceChange(null, aa, INSUFFICIENT_ACCOUNT_BALANCE, payerID);
	}

	public static BalanceChange changingFtUnits(Id token, TokenID tokenId, AccountAmount aa, AccountID payerID) {
		final var tokenChange = new BalanceChange(token, aa, INSUFFICIENT_TOKEN_BALANCE, payerID);
		tokenChange.tokenId = tokenId;
		return tokenChange;
	}

	public static BalanceChange hbarAdjust(Id id, long amount, AccountID payerID, boolean isApprovedAllowance) {
		return new BalanceChange(id, amount, payerID, isApprovedAllowance, INSUFFICIENT_ACCOUNT_BALANCE);
	}

	public static BalanceChange changingNftOwnership(Id token, TokenID tokenId, NftTransfer nftTransfer, AccountID payerID) {
		final var nftChange = new BalanceChange(
				token,
				nftTransfer.getSenderAccountID(),
				nftTransfer.getReceiverAccountID(),
				nftTransfer.getSerialNumber(),
				SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
		nftChange.tokenId = tokenId;
		nftChange.isApprovedAllowance = nftTransfer.getIsApproval();
		nftChange.payerID = payerID;
		return nftChange;
	}

	public static BalanceChange tokenAdjust(Id account, Id token, long amount, AccountID payerID, boolean isApprovedAllowance) {
		final var tokenChange = new BalanceChange(account, amount, payerID, isApprovedAllowance, INSUFFICIENT_TOKEN_BALANCE);
		tokenChange.payerID = payerID;
		tokenChange.token = token;
		tokenChange.tokenId = token.asGrpcToken();
		return tokenChange;
	}

	/* ℏ constructor */
	private BalanceChange(Id account, long amount, AccountID payerID, boolean isApprovedAllowance, ResponseCodeEnum code) {
		this.token = null;
		this.account = account;
		this.accountId = account.asGrpcAccount();
		this.alias = accountId.getAlias();
		this.originalUnits = amount;
		this.isApprovedAllowance = isApprovedAllowance;
		this.payerID = payerID;
		this.codeForInsufficientBalance = code;
		this.aggregatedUnits = amount;
		if (isApprovedAllowance) {
			this.allowanceUnits = amount;
		}
	}

	/* HTS constructor */
	private BalanceChange(Id token, AccountAmount aa, ResponseCodeEnum code, AccountID payerID) {
		this.token = token;
		this.accountId = aa.getAccountID();
		this.alias = accountId.getAlias();
		this.account = Id.fromGrpcAccount(accountId);
		this.isApprovedAllowance = aa.getIsApproval();
		this.originalUnits = aa.getAmount();
		this.codeForInsufficientBalance = code;
		this.payerID = payerID;
		this.aggregatedUnits = aa.getAmount();
		if (isApprovedAllowance) {
			this.allowanceUnits = aa.getAmount();
		}
	}

	/* NFT constructor */
	private BalanceChange(Id token, AccountID sender, AccountID receiver, long serialNo, ResponseCodeEnum code) {
		this.token = token;
		this.nftId = new NftId(token.shard(), token.realm(), token.num(), serialNo);
		this.accountId = sender;
		this.counterPartyAccountId = receiver;
		this.account = Id.fromGrpcAccount(accountId);
		this.alias = accountId.getAlias();
		this.codeForInsufficientBalance = code;
		this.aggregatedUnits = serialNo;
	}

	public void replaceAliasWith(final AccountID createdId) {
		accountId = createdId;
		account = Id.fromGrpcAccount(createdId);
		alias = ByteString.EMPTY;
	}

	public boolean isForHbar() {
		return token == null;
	}

	public boolean isForFungibleToken() {
		return token != null && counterPartyAccountId == null;
	}

	public boolean isForNft() {
		return token != null && counterPartyAccountId != null;
	}

	public NftId nftId() {
		return nftId;
	}

	public long originalUnits() {
		return originalUnits;
	}

	public long serialNo() {
		return aggregatedUnits;
	}

	public long getNewBalance() {
		return newBalance;
	}

	public void setNewBalance(final long newBalance) {
		this.newBalance = newBalance;
	}

	public TokenID tokenId() {
		return (tokenId != null) ? tokenId : NO_TOKEN_FOR_HBAR_ADJUST;
	}

	public AccountID accountId() {
		return accountId;
	}

	public ByteString alias() {
		return alias;
	}

	public AccountID counterPartyAccountId() {
		return counterPartyAccountId;
	}

	public Id getAccount() {
		return account;
	}

	public Id getToken() {
		return token;
	}

	public ResponseCodeEnum codeForInsufficientBalance() {
		return codeForInsufficientBalance;
	}

	public boolean hasExpectedDecimals() {
		return expectedDecimals != -1;
	}

	public int getExpectedDecimals() {
		return expectedDecimals;
	}

	public void setExpectedDecimals(final int expectedDecimals) {
		this.expectedDecimals = expectedDecimals;
	}

	public boolean isApprovedAllowance() {
		return this.allowanceUnits == 0 ? isApprovedAllowance : this.allowanceUnits < 0;
	}

	public AccountID getPayerID() {
		return payerID;
	}

	public void aggregateUnits(long amount) {
		this.aggregatedUnits += amount;
	}

	public long getAggregatedUnits() {
		return this.aggregatedUnits;
	}

	public void addAllowanceUnits(long amount) {
		this.allowanceUnits += amount;
	}

	public long getAllowanceUnits() {
		return this.allowanceUnits;
	}

	/* NOTE: The object methods below are only overridden to improve readability of unit tests;
	this model object is not used in hash-based collections, so the performance of these
	methods doesn't matter. */

	@Override
	public boolean equals(final Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		if (counterPartyAccountId == null) {
			return MoreObjects.toStringHelper(BalanceChange.class)
					.add("token", token == null ? "ℏ" : token)
					.add("account", account)
					.add("alias", alias.toStringUtf8())
					.add("units", aggregatedUnits)
					.add("expectedDecimals", expectedDecimals)
					.toString();
		} else {
			return MoreObjects.toStringHelper(BalanceChange.class)
					.add("nft", token)
					.add("serialNo", aggregatedUnits)
					.add("from", account)
					.add("to", Id.fromGrpcAccount(counterPartyAccountId))
					.toString();
		}
	}

	public void setCodeForInsufficientBalance(ResponseCodeEnum codeForInsufficientBalance) {
		this.codeForInsufficientBalance = codeForInsufficientBalance;
	}

	public void setExemptFromCustomFees(boolean exemptFromCustomFees) {
		this.exemptFromCustomFees = exemptFromCustomFees;
	}

	public boolean isExemptFromCustomFees() {
		return exemptFromCustomFees;
	}

	public boolean hasNonEmptyAlias() {
		return accountId.getAccountNum() == 0 && !alias.isEmpty();
	}
}
