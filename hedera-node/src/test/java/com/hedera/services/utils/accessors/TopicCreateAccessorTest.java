package com.hedera.services.utils.accessors;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TopicCreateAccessorTest {
	private TopicCreateAccessor accessor;

	@Mock
	private AliasManager aliasManager;

	private AccountID autoRenewAccount = asAccount("0.0.4");
	private AccountID payer = asAccount("0.0.2");
	private AccountID autoRenewAlias = asAccountWithAlias("dummy");
	private AccountID payerAlias = asAccountWithAlias("dummyPayer");


	private SwirldTransaction topicCreateTxn;

	@Test
	void getsMetaCorrectly() throws InvalidProtocolBufferException {
		final TransactionBody createTxn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(payer))
				.setConsensusCreateTopic(
						ConsensusCreateTopicTransactionBody.newBuilder()
								.setAutoRenewAccount(autoRenewAccount)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(2000))
								.build()
				)
				.setMemo("Hi!")
				.build();
		givenTxn(createTxn);
		accessor = new TopicCreateAccessor(topicCreateTxn, aliasManager);

		given(aliasManager.unaliased(autoRenewAccount)).willReturn(EntityNum.fromAccountId(autoRenewAccount));
		given(aliasManager.unaliased(payer)).willReturn(EntityNum.fromAccountId(payer));
		assertEquals(Id.fromGrpcAccount(autoRenewAccount), accessor.accountToAutoRenew());
		assertEquals(payer, accessor.getPayer());
	}

	@Test
	void looksUpAliasCorrectly() throws InvalidProtocolBufferException {
		final TransactionBody createTxn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(payerAlias))
				.setConsensusCreateTopic(
						ConsensusCreateTopicTransactionBody.newBuilder()
								.setAutoRenewAccount(autoRenewAlias)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(2000))
								.build()
				)
				.setMemo("Hi!")
				.build();
		givenTxn(createTxn);
		accessor = new TopicCreateAccessor(topicCreateTxn, aliasManager);

		given(aliasManager.unaliased(autoRenewAlias)).willReturn(EntityNum.fromAccountId(autoRenewAccount));
		given(aliasManager.unaliased(payerAlias)).willReturn(EntityNum.fromAccountId(payer));

		assertEquals(Id.fromGrpcAccount(autoRenewAccount), accessor.accountToAutoRenew());
		assertEquals(payer, accessor.getPayer());
	}

	@Test
	void givesMissingIdIfInvalidAlias() throws InvalidProtocolBufferException {
		final TransactionBody createTxn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(payerAlias))
				.setConsensusCreateTopic(
						ConsensusCreateTopicTransactionBody.newBuilder()
								.setAutoRenewAccount(autoRenewAlias)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(2000))
								.build()
				)
				.setMemo("Hi!")
				.build();
		givenTxn(createTxn);
		accessor = new TopicCreateAccessor(topicCreateTxn, aliasManager);

		given(aliasManager.unaliased(autoRenewAlias)).willReturn(MISSING_NUM);
		given(aliasManager.unaliased(payerAlias)).willReturn(MISSING_NUM);

		assertEquals(MISSING_NUM.toId(), accessor.accountToAutoRenew());
		assertEquals(MISSING_NUM.toGrpcAccountId(), accessor.getPayer());
	}

	void givenTxn(TransactionBody body) {
		topicCreateTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(body.toByteString())
				.build().toByteArray());
	}
}
