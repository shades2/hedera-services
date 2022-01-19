package com.hedera.services.queries.validation;

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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.ledger.accounts.AliasLookup;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class QueryFeeCheckTest {
	private static final long payerExpiry = 1_234_567L;
	private static final AccountID aMissing = asAccount("0.0.321321");
	private static final AccountID aRich = asAccount("0.0.2");
	private static final AccountID aNode = asAccount("0.0.3");
	private static final AccountID anotherNode = asAccount("0.0.4");
	private static final AccountID aBroke = asAccount("0.0.13257");
	private static final AccountID aDetached = asAccount("0.0.75231");
	private static final AccountID aQueryPayer = asAccount("0.0.13258");
	private static final AccountID aTestPayer = asAccount("0.0.13259");
	private static final AccountID aRichAlias = asAccountWithAlias("pretend");
	private static final AccountID invalidAlias = asAccountWithAlias("invalid");
	private static final TransactionID txnId = TransactionID.newBuilder().setAccountID(aRich).build();
	private static final long feeRequired = 1234L;
	private static final long aLittle = 2L;
	private static final long aLot = Long.MAX_VALUE - 1L;
	private static final long aFew = 100L;
	private static final EntityNum missingKey = EntityNum.fromAccountId(aMissing);
	private static final EntityNum richKey = EntityNum.fromAccountId(aRich);
	private static final EntityNum brokeKey = EntityNum.fromAccountId(aBroke);
	private static final EntityNum nodeKey = EntityNum.fromAccountId(aNode);
	private static final EntityNum anotherNodeKey = EntityNum.fromAccountId(anotherNode);
	private static final EntityNum queryPayerKey = EntityNum.fromAccountId(aQueryPayer);
	private static final EntityNum testPayerKey = EntityNum.fromAccountId(aTestPayer);

	private MerkleAccount detached, broke, rich, testPayer, queryPayer;
	private OptionValidator validator;
	private final MockGlobalDynamicProps dynamicProps = new MockGlobalDynamicProps();
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	private AliasManager aliasManager;

	private QueryFeeCheck subject;

	@BeforeEach
	private void setup() {
		detached = mock(MerkleAccount.class);
		given(detached.getBalance()).willReturn(0L);
		broke = mock(MerkleAccount.class);
		given(broke.getBalance()).willReturn(aLittle);
		given(broke.getExpiry()).willReturn(payerExpiry);
		rich = mock(MerkleAccount.class);
		given(rich.getBalance()).willReturn(aLot);

		testPayer = mock(MerkleAccount.class);
		given(testPayer.getBalance()).willReturn(aFew);
		queryPayer = mock(MerkleAccount.class);
		given(queryPayer.getBalance()).willReturn(aLot);

		accounts = mock(MerkleMap.class);
		given(accounts.get(missingKey)).willReturn(null);
		given(accounts.get(richKey)).willReturn(rich);
		given(accounts.get(brokeKey)).willReturn(broke);
		given(accounts.get(testPayerKey)).willReturn(testPayer);
		given(accounts.get(queryPayerKey)).willReturn(queryPayer);
		given(accounts.get(EntityNum.fromAccountId(aDetached))).willReturn(detached);

		given(accounts.containsKey(missingKey)).willReturn(false);
		given(accounts.containsKey(richKey)).willReturn(true);
		given(accounts.containsKey(brokeKey)).willReturn(true);
		given(accounts.containsKey(nodeKey)).willReturn(true);
		given(accounts.containsKey(anotherNodeKey)).willReturn(true);
		given(accounts.containsKey(testPayerKey)).willReturn(true);
		given(accounts.containsKey(testPayerKey)).willReturn(true);

		validator = mock(OptionValidator.class);

		aliasManager = mock(AliasManager.class);
		given(aliasManager.lookupIdBy(aRichAlias.getAlias())).willReturn(EntityNum.fromAccountId(aRich));
		given(aliasManager.lookupIdBy(invalidAlias.getAlias())).willReturn(EntityNum.MISSING_NUM);
		given(aliasManager.lookUpPayer(invalidAlias)).willReturn(
				AliasLookup.of(invalidAlias, INVALID_PAYER_ACCOUNT_ID));
		given(aliasManager.lookUpPayer(aRich)).willReturn(AliasLookup.of(aRich, OK));
		given(aliasManager.lookUpAccount(aRich)).willReturn(AliasLookup.of(aRich, OK));
		given(aliasManager.lookUpAccount(aNode)).willReturn(AliasLookup.of(aNode, OK));
		given(aliasManager.lookUpAccount(aBroke)).willReturn(AliasLookup.of(aBroke, OK));
		given(aliasManager.lookUpAccount(aQueryPayer)).willReturn(AliasLookup.of(aQueryPayer, OK));
		given(aliasManager.lookUpAccount(aTestPayer)).willReturn(AliasLookup.of(aTestPayer, OK));

		subject = new QueryFeeCheck(validator, dynamicProps, () -> accounts, aliasManager);
	}

	@Test
	void rejectsEmptyTransfers() {
		assertEquals(INVALID_ACCOUNT_AMOUNTS, subject.transfersPlausibility(null));
		assertEquals(INVALID_ACCOUNT_AMOUNTS, subject.transfersPlausibility(Collections.emptyList()));
	}

	@Test
	void acceptsSufficientFee() {
		assertEquals(
				OK,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle),
								adjustmentWith(aNode, aLittle)),
						aLittle - 1, aNode));
	}

	@Test
	void rejectsWrongRecipient() {
		assertEquals(
				INVALID_RECEIVING_NODE_ACCOUNT,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle),
								adjustmentWith(aBroke, aLittle)),
						aLittle - 1, aNode));
	}

	@Test
	void rejectsWhenNodeIsMissing() {
		assertEquals(
				INVALID_RECEIVING_NODE_ACCOUNT,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle * 2),
								adjustmentWith(aBroke, aLittle),
								adjustmentWith(aBroke, aLittle)),
						aLittle - 1, aNode));
	}

	@Test
	void allowsMultipleRecipients() {
		assertEquals(
				OK,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle * 2),
								adjustmentWith(aBroke, aLittle),
								adjustmentWith(aNode, aLittle)),
						aLittle - 1, aNode));
	}

	@Test
	void rejectsInsufficientNodePayment() {
		assertEquals(
				INSUFFICIENT_TX_FEE,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle * 2),
								adjustmentWith(aBroke, aLittle + aLittle / 2),
								adjustmentWith(aNode, aLittle / 2)),
						aLittle, aNode));
	}

	@Test
	void rejectsInsufficientFee() {
		assertEquals(
				INSUFFICIENT_TX_FEE,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle),
								adjustmentWith(aBroke, aLittle)),
						aLittle + 1, aNode));
	}

	@Test
	void filtersOnBasicImplausibility() {
		assertEquals(
				INVALID_ACCOUNT_AMOUNTS,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, aLot),
								adjustmentWith(aBroke, aLittle)), 0L, aNode));
	}

	@Test
	void rejectsOverflowingTransfer() {
		assertEquals(
				INVALID_ACCOUNT_AMOUNTS,
				subject.transfersPlausibility(
						transfersWith(
								adjustmentWith(aRich, aLot),
								adjustmentWith(aBroke, aLittle))));
	}

	@Test
	void rejectsNonNetTransfer() {
		assertEquals(
				INVALID_ACCOUNT_AMOUNTS,
				subject.transfersPlausibility(
						transfersWith(
								adjustmentWith(aRich, aLittle),
								adjustmentWith(aBroke, aLittle))));
	}

	@Test
	void catchesBadEntry() {
		given(aliasManager.lookUpAccount(aRich)).willReturn(AliasLookup.of(aRich, OK));
		given(aliasManager.lookUpAccount(aMissing)).willReturn(AliasLookup.of(aMissing, OK));
		given(aliasManager.lookUpAccount(aBroke)).willReturn(AliasLookup.of(aBroke, OK));
		assertEquals(
				ACCOUNT_ID_DOES_NOT_EXIST,
				subject.transfersPlausibility(
						transfersWith(
								adjustmentWith(aRich, -aLittle),
								adjustmentWith(aMissing, 0),
								adjustmentWith(aBroke, aLittle))));
	}

	@Test
	void rejectsMinValue() {
		final var adjustment = adjustmentWith(aRich, Long.MIN_VALUE);

		final var status = subject.adjustmentPlausibility(adjustment);

		assertEquals(INVALID_ACCOUNT_AMOUNTS, status);
	}

	@Test
	void nonexistentSenderHasNoBalance() {
		given(aliasManager.lookUpAccount(aMissing)).willReturn(AliasLookup.of(aMissing, OK));
		final var adjustment = adjustmentWith(aMissing, -aLittle);

		final var status = subject.adjustmentPlausibility(adjustment);

		assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, status);
	}

	@Test
	void brokePayerRejected() {
		final var adjustment = adjustmentWith(aBroke, -aLot);

		final var status = subject.adjustmentPlausibility(adjustment);

		assertEquals(INSUFFICIENT_PAYER_BALANCE, status);
	}

	@Test
	void detachedPayerRejectedWithRefinement() {
		given(aliasManager.lookUpAccount(aDetached)).willReturn(AliasLookup.of(aDetached, OK));
		given(validator.isAfterConsensusSecond(payerExpiry)).willReturn(false);
		final var adjustment = adjustmentWith(aDetached, -aLot);

		final var status = subject.adjustmentPlausibility(adjustment);

		assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, status);
	}

	@Test
	void cannotBeDetachedIfNoAutoRenew() {
		given(aliasManager.lookUpAccount(aDetached)).willReturn(AliasLookup.of(aDetached, OK));
		given(validator.isAfterConsensusSecond(payerExpiry)).willReturn(false);
		dynamicProps.disableAutoRenew();
		final var adjustment = adjustmentWith(aDetached, -aLot);

		final var status = subject.adjustmentPlausibility(adjustment);

		assertEquals(INSUFFICIENT_PAYER_BALANCE, status);
	}

	@Test
	void noLongerDetachedWithNonzeroBalance() {
		given(aliasManager.lookUpAccount(aDetached)).willReturn(AliasLookup.of(aDetached, OK));
		given(validator.isAfterConsensusSecond(payerExpiry)).willReturn(false);
		given(detached.getBalance()).willReturn(1L);
		final var adjustment = adjustmentWith(aDetached, -aLot);

		final var status = subject.adjustmentPlausibility(adjustment);

		assertEquals(INSUFFICIENT_PAYER_BALANCE, status);
	}

	@Test
	void missingReceiverRejected() {
		given(aliasManager.lookUpAccount(aMissing)).willReturn(AliasLookup.of(aMissing, OK));
		final var adjustment = adjustmentWith(aMissing, aLot);

		final var status = subject.adjustmentPlausibility(adjustment);

		assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, status);
	}

	@Test
	void validateQueryPaymentSucceeds() {
		final long amount = 8;
		final var body = getPaymentTxnBody(amount, null);

		assertEquals(aRich, body.getTransactionID().getAccountID());
		assertTrue(checkPayerInTransferList(body, aRich));
		assertEquals(OK, subject.validateQueryPaymentTransfers(body));
	}

	@Test
	void adjustmentAccountValidated() {
		given(aliasManager.lookUpAccount(invalidAlias)).willReturn(AliasLookup.of(invalidAlias, INVALID_ACCOUNT_ID));
		final var adjustment = adjustmentWith(invalidAlias, aLot);

		final var status = subject.adjustmentPlausibility(adjustment);

		assertEquals(INVALID_ACCOUNT_ID, status);
	}

	@Test
	void validateQueryPaymentSucceedsWithAliasPayer() {
		final long amount = 8;
		final var body = getPaymentTxnBodyWithAlias(amount, null, aRichAlias, aRichAlias);

		assertEquals(aRichAlias, body.getTransactionID().getAccountID());
		assertTrue(checkPayerInTransferList(body, aRichAlias));
		assertEquals(OK, subject.validateQueryPaymentTransfers(body));
	}

	@Test
	void validateQueryPaymentFailsWithInvalidAliasPayer() {
		final long amount = 8;
		final var body = getPaymentTxnBodyWithAlias(amount, null, invalidAlias, aRichAlias);

		assertEquals(invalidAlias, body.getTransactionID().getAccountID());
		assertTrue(checkPayerInTransferList(body, aRichAlias));
		assertEquals(INVALID_PAYER_ACCOUNT_ID, subject.validateQueryPaymentTransfers(body));
	}

	@Test
	void validateQueryPaymentFailsWithValidAliasPayerInvalidSender() {
		final long amount = 8;
		final var body = getPaymentTxnBodyWithAlias(amount, null, aRichAlias, invalidAlias);

		assertEquals(aRichAlias, body.getTransactionID().getAccountID());
		assertTrue(checkPayerInTransferList(body, invalidAlias));
		assertEquals(INVALID_ACCOUNT_ID, subject.validateQueryPaymentTransfers(body));
	}

	@Test
	void paymentFailsWithQueryPayerBalance() {
		final long amount = 5000L;
		given(aliasManager.lookUpAccount(aBroke)).willReturn(AliasLookup.of(aBroke, OK));
		given(aliasManager.lookUpAccount(aNode)).willReturn(AliasLookup.of(aNode, OK));
		final var transList = TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aBroke).setAmount(-1 * amount))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount));
		final var body = TransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().setTransfers(transList))
				.setTransactionID(txnId)
				.setNodeAccountID(aNode)
				.setTransactionFee(feeRequired)
				.build();

		assertEquals(aRich, body.getTransactionID().getAccountID());
		assertFalse(checkPayerInTransferList(body, aRich));
		assertEquals(INSUFFICIENT_PAYER_BALANCE, subject.validateQueryPaymentTransfers(body));
	}

	@Test
	void paymentFailsWithBrokenPayer() {
		final long amount = 5000L;
		given(aliasManager.lookUpAccount(aBroke)).willReturn(AliasLookup.of(aBroke, OK));
		given(aliasManager.lookUpPayer(aBroke)).willReturn(AliasLookup.of(aBroke, OK));
		given(aliasManager.lookUpAccount(aNode)).willReturn(AliasLookup.of(aNode, OK));
		final var transList = TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aBroke).setAmount(-1 * amount))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount));
		final var body = TransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().setTransfers(transList))
				.setTransactionID(TransactionID.newBuilder().setAccountID(aBroke))
				.setNodeAccountID(aNode)
				.setTransactionFee(feeRequired)
				.build();

		assertEquals(aBroke, body.getTransactionID().getAccountID());
		assertTrue(checkPayerInTransferList(body, aBroke));
		assertEquals(INSUFFICIENT_PAYER_BALANCE, subject.validateQueryPaymentTransfers(body));
	}

	@Test
	void paymentFailsWithInsufficientPayerBalance() {
		final long amount = 5000L;
		given(aliasManager.lookUpAccount(aBroke)).willReturn(AliasLookup.of(aBroke, OK));
		given(aliasManager.lookUpPayer(aBroke)).willReturn(AliasLookup.of(aBroke, OK));
		given(aliasManager.lookUpAccount(aNode)).willReturn(AliasLookup.of(aNode, OK));

		final var transList = TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aBroke).setAmount(-1 * amount))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount));
		final var body = TransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().setTransfers(transList))
				.setTransactionID(TransactionID.newBuilder().setAccountID(aBroke))
				.setNodeAccountID(aNode)
				.setTransactionFee(Long.MAX_VALUE)
				.build();

		assertEquals(INSUFFICIENT_PAYER_BALANCE, subject.validateQueryPaymentTransfers(body));
	}

	@Test
	void queryPaymentMultiPayerMultiNodeSucceeds() {
		final long amount = 200L;
		given(aliasManager.lookUpAccount(anotherNode)).willReturn(AliasLookup.of(anotherNode, OK));

		final var transList = TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aRich).setAmount(-1 * amount / 4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aTestPayer).setAmount(-1 * amount / 4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aQueryPayer).setAmount(-1 * amount / 2))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount / 2))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(anotherNode).setAmount(amount / 2));
		final var body = getPaymentTxnBody(amount, transList);

		assertEquals(3, body.getCryptoTransfer().getTransfers()
				.getAccountAmountsList().stream()
				.filter(aa -> aa.getAmount() < 0)
				.collect(Collectors.toList()).size());
		assertEquals(OK, subject.validateQueryPaymentTransfers(body));
	}

	@Test
	void queryPaymentMultiTransferFails() {
		final long amount = 200L;
		final var transList = TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aRich).setAmount(-1 * amount / 4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aBroke).setAmount(-1 * amount / 4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aQueryPayer).setAmount(-1 * amount / 4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aTestPayer).setAmount(-1 * amount / 4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount));
		final var body = getPaymentTxnBody(amount, transList);

		assertEquals(4, body.getCryptoTransfer().getTransfers()
				.getAccountAmountsList().stream()
				.filter(aa -> aa.getAmount() < 0)
				.collect(Collectors.toList()).size());
		assertEquals(INSUFFICIENT_PAYER_BALANCE, subject.validateQueryPaymentTransfers(body));
	}

	private AccountAmount adjustmentWith(final AccountID id, final long amount) {
		return AccountAmount.newBuilder()
				.setAccountID(id)
				.setAmount(amount)
				.build();
	}

	private List<AccountAmount> transfersWith(final AccountAmount a, final AccountAmount b) {
		return List.of(a, b);
	}

	private List<AccountAmount> transfersWith(final AccountAmount a, final AccountAmount b, final AccountAmount c) {
		return List.of(a, b, c);
	}

	private TransactionBody getPaymentTxnBody(final long amount, final TransferList.Builder transferList) {
		final var transList = (transferList != null)
				? transferList
				: TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aRich).setAmount(-1 * amount))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount));
		return TransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().setTransfers(transList))
				.setTransactionID(txnId)
				.setNodeAccountID(aNode)
				.setTransactionFee(feeRequired)
				.build();
	}

	private TransactionBody getPaymentTxnBodyWithAlias(final long amount, final TransferList.Builder transferList,
			final AccountID payer, final AccountID sender) {
		given(aliasManager.lookUpPayer(aRichAlias)).willReturn(AliasLookup.of(aRich, OK));
		given(aliasManager.lookUpPayer(invalidAlias)).willReturn(
				AliasLookup.of(invalidAlias, INVALID_PAYER_ACCOUNT_ID));
		given(aliasManager.lookUpAccount(aRichAlias)).willReturn(AliasLookup.of(aRich, OK));
		given(aliasManager.lookUpAccount(invalidAlias)).willReturn(AliasLookup.of(invalidAlias, INVALID_ACCOUNT_ID));

		final var transList = (transferList != null)
				? transferList
				: TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(sender).setAmount(-1 * amount))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount));
		return TransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().setTransfers(transList))
				.setTransactionID(getTxnIdWithAlias(payer))
				.setNodeAccountID(aNode)
				.setTransactionFee(feeRequired)
				.build();
	}

	private final TransactionID getTxnIdWithAlias(final AccountID payer) {
		return TransactionID.newBuilder().setAccountID(payer).build();
	}

	private boolean checkPayerInTransferList(final TransactionBody body, final AccountID payer) {
		final var payerTransfer = body.getCryptoTransfer().
				getTransfers().
				getAccountAmountsList().
				stream().filter(aa -> aa.getAccountID() == payer).findAny().orElse(null);
		return payerTransfer != null;
	}
}
