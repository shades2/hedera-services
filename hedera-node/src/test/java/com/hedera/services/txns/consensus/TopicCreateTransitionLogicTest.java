package com.hedera.services.txns.consensus;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TopicStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.TopicCreateAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class TopicCreateTransitionLogicTest {
	private static final long VALID_AUTORENEW_PERIOD_SECONDS = 30 * 86400L;
	private static final long INVALID_AUTORENEW_PERIOD_SECONDS = -1L;
	private static final String TOO_LONG_MEMO = "too-long";
	private static final String VALID_MEMO = "memo";
	private static final TopicID NEW_TOPIC_ID = asTopic("7.6.54321");

	// key to be used as a valid admin or submit key.
	private static final Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	private static final AccountID payer = AccountID.newBuilder().setAccountNum(2_345L).build();
	private static final Instant consensusTimestamp = Instant.ofEpochSecond(1546304463);
	private TransactionBody transactionBody;

	private MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
	private MerkleMap<EntityNum, MerkleTopic> topics = new MerkleMap<>();

	@Mock
	private TransactionContext transactionContext;
	@Mock
	private OptionValidator validator;
	@Mock
	private EntityIdSource entityIdSource;
	@Mock
	private TopicStore topicStore;
	@Mock
	private AccountStore accountStore;
	@Mock
	private Account autoRenew;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private AliasManager aliasManager;

	private TopicCreateTransitionLogic subject;
	private TopicCreateAccessor accessor;
	private SwirldTransaction topicCreateTxn;

	@BeforeEach
	private void setup() {
		accounts.clear();
		topics.clear();

		subject = new TopicCreateTransitionLogic(
				topicStore, entityIdSource, validator, sigImpactHistorian, transactionContext, accountStore);
	}

	@Test
	void hasCorrectApplicability() throws InvalidProtocolBufferException {
		givenValidTransactionWithAllOptions();

		assertTrue(subject.applicability().test(transactionBody));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void syntaxCheckWithAdminKey() throws InvalidProtocolBufferException {
		givenValidTransactionWithAllOptions();

		assertEquals(OK, subject.semanticCheck().apply(transactionBody));
	}

	@Test
	void syntaxCheckWithInvalidAdminKey() throws InvalidProtocolBufferException {
		givenValidTransactionWithAllOptions();
		given(validator.hasGoodEncoding(key)).willReturn(false);

		assertEquals(BAD_ENCODING, subject.validateSemantics(accessor));
	}

	@Test
	void followsHappyPath() throws InvalidProtocolBufferException {
		givenValidTransactionWithAllOptions();
		given(aliasManager.unaliased(MISC_ACCOUNT)).willReturn(EntityNum.fromAccountId(MISC_ACCOUNT));
		given(validator.memoCheck(anyString())).willReturn(OK);
		given(transactionContext.accessor()).willReturn(accessor);
		given(accountStore.loadAccountOrFailWith(any(), any())).willReturn(autoRenew);
		given(autoRenew.isSmartContract()).willReturn(false);
		given(validator.isValidAutoRenewPeriod(Duration.newBuilder().setSeconds(VALID_AUTORENEW_PERIOD_SECONDS).build()))
				.willReturn(true);
		given(transactionContext.consensusTime()).willReturn(consensusTimestamp);
		given(entityIdSource.newTopicId(any())).willReturn(NEW_TOPIC_ID);
		given(aliasManager.unaliased(payer)).willReturn(EntityNum.fromAccountId(payer));

		subject.doStateTransition();

		verify(topicStore).persistNew(any());
		verify(sigImpactHistorian).markEntityChanged(NEW_TOPIC_ID.getTopicNum());
	}

	@Test
	void memoTooLong() throws InvalidProtocolBufferException {
		givenTransactionWithTooLongMemo();
		given(validator.memoCheck(anyString())).willReturn(MEMO_TOO_LONG);
		given(transactionContext.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(payer)).willReturn(EntityNum.fromAccountId(payer));

		assertFailsWith(() -> subject.doStateTransition(), MEMO_TOO_LONG);

		assertTrue(topics.isEmpty());
	}

	@Test
	void badSubmitKey() throws InvalidProtocolBufferException {
		givenTransactionWithInvalidSubmitKey();
		given(transactionContext.accessor()).willReturn(accessor);
		given(validator.attemptDecodeOrThrow(any())).willThrow(new InvalidTransactionException(BAD_ENCODING));
		given(aliasManager.unaliased(payer)).willReturn(EntityNum.fromAccountId(payer));

		assertFailsWith(() -> subject.doStateTransition(), BAD_ENCODING);

		assertTrue(topics.isEmpty());
	}

	@Test
	void missingAutoRenewPeriod() throws InvalidProtocolBufferException {
		givenTransactionWithMissingAutoRenewPeriod();
		given(validator.memoCheck(anyString())).willReturn(OK);
		given(transactionContext.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(payer)).willReturn(EntityNum.fromAccountId(payer));

		assertFailsWith(() -> subject.doStateTransition(), INVALID_RENEWAL_PERIOD);

		assertTrue(topics.isEmpty());
	}

	@Test
	void badAutoRenewPeriod() throws InvalidProtocolBufferException {
		givenTransactionWithInvalidAutoRenewPeriod();
		given(validator.memoCheck(anyString())).willReturn(OK);
		given(transactionContext.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(payer)).willReturn(EntityNum.fromAccountId(payer));

		assertFailsWith(() -> subject.doStateTransition(), AUTORENEW_DURATION_NOT_IN_RANGE);

		assertTrue(topics.isEmpty());
	}

	@Test
	void invalidAutoRenewAccountId() throws InvalidProtocolBufferException {
		givenTransactionWithInvalidAutoRenewAccountId();
		given(validator.memoCheck(anyString())).willReturn(OK);
		given(transactionContext.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(payer)).willReturn(EntityNum.fromAccountId(payer));
		given(aliasManager.unaliased(MISC_ACCOUNT)).willReturn(EntityNum.fromAccountId(MISC_ACCOUNT));
		given(accountStore.loadAccountOrFailWith(any(), any())).willThrow(
				new InvalidTransactionException(INVALID_AUTORENEW_ACCOUNT));
		given(validator.isValidAutoRenewPeriod(Duration.newBuilder().setSeconds(VALID_AUTORENEW_PERIOD_SECONDS).build()))
				.willReturn(true);

		assertFailsWith(() -> subject.doStateTransition(), INVALID_AUTORENEW_ACCOUNT);

		assertTrue(topics.isEmpty());
	}

	@Test
	void detachedAutoRenewAccountId() throws InvalidProtocolBufferException {
		givenTransactionWithDetachedAutoRenewAccountId();
		given(validator.memoCheck(anyString())).willReturn(OK);
		given(transactionContext.accessor()).willReturn(accessor);
		given(validator.isValidAutoRenewPeriod(Duration.newBuilder().setSeconds(VALID_AUTORENEW_PERIOD_SECONDS).build()))
				.willReturn(true);
		given(validator.memoCheck(anyString())).willReturn(OK);

		given(validator.isValidAutoRenewPeriod(accessor.autoRenewPeriod())).willReturn(true);
		given(accountStore.loadAccountOrFailWith(any(), any())).willThrow(
				new InvalidTransactionException(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
		given(aliasManager.unaliased(payer)).willReturn(EntityNum.fromAccountId(payer));

		assertFailsWith(() -> subject.doStateTransition(), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

		assertTrue(topics.isEmpty());
	}

	@Test
	void autoRenewAccountNotAllowed() throws InvalidProtocolBufferException {
		givenTransactionWithAutoRenewAccountWithoutAdminKey();
		given(transactionContext.accessor()).willReturn(accessor);
		given(validator.isValidAutoRenewPeriod(accessor.autoRenewPeriod())).willReturn(true);
		given(accountStore.loadAccountOrFailWith(any(), any())).willReturn(autoRenew);
		given(autoRenew.isSmartContract()).willReturn(false);
		given(validator.memoCheck(anyString())).willReturn(OK);
		given(aliasManager.unaliased(payer)).willReturn(EntityNum.fromAccountId(payer));

		assertFailsWith(() -> subject.doStateTransition(), AUTORENEW_ACCOUNT_NOT_ALLOWED);

		assertTrue(topics.isEmpty());
	}

	private void givenTransaction(
			final ConsensusCreateTopicTransactionBody.Builder body) throws InvalidProtocolBufferException {
		final var txnId = TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(Timestamp.newBuilder().setSeconds(consensusTimestamp.getEpochSecond()));
		transactionBody = TransactionBody.newBuilder()
				.setTransactionID(txnId)
				.setConsensusCreateTopic(body)
				.build();
		topicCreateTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(transactionBody.toByteString())
				.build().toByteArray());
		accessor = new TopicCreateAccessor(topicCreateTxn, aliasManager);
	}

	private ConsensusCreateTopicTransactionBody.Builder getBasicValidTransactionBodyBuilder() {
		return ConsensusCreateTopicTransactionBody.newBuilder()
				.setAutoRenewPeriod(Duration.newBuilder()
						.setSeconds(VALID_AUTORENEW_PERIOD_SECONDS).build());
	}

	private void givenValidTransactionWithAllOptions() throws InvalidProtocolBufferException {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setMemo(VALID_MEMO)
						.setAdminKey(key)
						.setSubmitKey(key)
						.setAutoRenewAccount(MISC_ACCOUNT)
		);
	}

	private void givenTransactionWithTooLongMemo() throws InvalidProtocolBufferException {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setMemo(TOO_LONG_MEMO)
		);
	}

	private void givenTransactionWithInvalidSubmitKey() throws InvalidProtocolBufferException {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setSubmitKey(Key.getDefaultInstance())
		);
	}

	private void givenTransactionWithInvalidAutoRenewPeriod() throws InvalidProtocolBufferException {
		givenTransaction(
				ConsensusCreateTopicTransactionBody.newBuilder()
						.setAutoRenewPeriod(Duration.newBuilder()
								.setSeconds(INVALID_AUTORENEW_PERIOD_SECONDS).build())
		);
	}

	private void givenTransactionWithMissingAutoRenewPeriod() throws InvalidProtocolBufferException {
		givenTransaction(
				ConsensusCreateTopicTransactionBody.newBuilder()
		);
	}

	private void givenTransactionWithInvalidAutoRenewAccountId() throws InvalidProtocolBufferException {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAutoRenewAccount(MISC_ACCOUNT)
		);
	}

	private void givenTransactionWithDetachedAutoRenewAccountId() throws InvalidProtocolBufferException {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAutoRenewAccount(MISC_ACCOUNT)
		);
		given(aliasManager.unaliased(MISC_ACCOUNT)).willReturn(EntityNum.fromAccountId(MISC_ACCOUNT));
	}

	private void givenTransactionWithAutoRenewAccountWithoutAdminKey() throws InvalidProtocolBufferException {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAutoRenewAccount(MISC_ACCOUNT)
		);
		given(aliasManager.unaliased(MISC_ACCOUNT)).willReturn(EntityNum.fromAccountId(MISC_ACCOUNT));

	}
}
