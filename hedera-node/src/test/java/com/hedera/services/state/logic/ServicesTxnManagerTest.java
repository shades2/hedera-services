package com.hedera.services.state.logic;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.records.RecordCache;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
class ServicesTxnManagerTest {
	private final long submittingMember = 1;
	private final Instant consensusTime = Instant.ofEpochSecond(1_234_567L, 890);
	private final AccountID effectivePayer = IdUtils.asAccount("0.0.75231");

	@Mock
	private Runnable processLogic;
	@Mock
	private Runnable recordStreaming;
	@Mock
	private Runnable triggeredProcessLogic;
	@Mock
	private TxnAccessor accessor;
	@Mock
	private HederaLedger ledger;
	@Mock
	private RecordCache recordCache;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private SigImpactHistorian sigImpactHistorian;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private ServicesTxnManager subject;

	@BeforeEach
	void setup() {
		subject = new ServicesTxnManager(
				processLogic, recordStreaming, triggeredProcessLogic, recordCache,
				ledger, txnCtx, sigImpactHistorian, recordsHistorian);
	}

	@Test
	void managesHappyPath() {
		// setup:
		InOrder inOrder = inOrder(ledger, txnCtx, processLogic, recordStreaming, recordsHistorian, sigImpactHistorian);

		// when:
		subject.process(accessor, consensusTime, submittingMember);

		// then:
		inOrder.verify(txnCtx).resetFor(accessor, consensusTime, submittingMember);
		inOrder.verify(sigImpactHistorian).setChangeTime(consensusTime);
		inOrder.verify(recordsHistorian).clearHistory();
		inOrder.verify(ledger).begin();
		inOrder.verify(processLogic).run();
		inOrder.verify(ledger).commit();
		inOrder.verify(recordStreaming).run();
	}

	@Test
	void warnsOnFailedRecordStreaming() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getSignedTxnWrapper()).willReturn(Transaction.getDefaultInstance());
		willThrow(IllegalStateException.class).given(recordStreaming).run();

		// when:
		subject.process(accessor, consensusTime, submittingMember);

		// then:
		assertThat(logCaptor.errorLogs(), contains(
				Matchers.startsWith("Possibly CATASTROPHIC failure in record streaming")));
	}

	@Test
	void usesFallbackLoggingWhenNecessary() {
		willThrow(IllegalStateException.class).given(recordStreaming).run();

		// when:
		subject.process(accessor, consensusTime, submittingMember);

		// then:
		assertThat(logCaptor.errorLogs(), contains(
				Matchers.startsWith("Possibly CATASTROPHIC failure in record streaming"),
				Matchers.startsWith("Full details could not be logged")));
	}

	@Test
	void setsFailInvalidAndWarnsOnProcessFailure() {
		// setup:
		InOrder inOrder = inOrder(ledger, txnCtx, processLogic, recordStreaming);

		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getSignedTxnWrapper()).willReturn(Transaction.getDefaultInstance());
		willThrow(IllegalStateException.class).given(ledger).begin();

		// when:
		subject.process(accessor, consensusTime, submittingMember);

		// then:
		inOrder.verify(ledger).begin();
		inOrder.verify(txnCtx).setStatus(ResponseCodeEnum.FAIL_INVALID);
		inOrder.verify(ledger).rollback();
		inOrder.verify(recordStreaming, never()).run();
		// and:
		assertThat(logCaptor.errorLogs(), contains(
				Matchers.startsWith("Possibly CATASTROPHIC failure in txn processing")));
	}

	@Test
	void retriesRecordCreationOnCommitFailureThenRollbacks() {
		// setup:
		InOrder inOrder = inOrder(ledger, txnCtx, processLogic, recordStreaming, recordCache);

		given(txnCtx.effectivePayer()).willReturn(effectivePayer);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getSignedTxnWrapper()).willReturn(Transaction.getDefaultInstance());
		// and:
		willThrow(IllegalStateException.class).given(ledger).commit();

		// when:
		subject.process(accessor, consensusTime, submittingMember);

		// then:
		inOrder.verify(txnCtx).resetFor(accessor, consensusTime, submittingMember);
		inOrder.verify(ledger).begin();
		inOrder.verify(processLogic).run();
		inOrder.verify(ledger).commit();
		inOrder.verify(recordCache).setFailInvalid(effectivePayer, accessor, consensusTime, submittingMember);
		inOrder.verify(ledger).rollback();
		inOrder.verify(recordStreaming, never()).run();
		// and:
		assertThat(logCaptor.errorLogs(), contains(
				Matchers.startsWith("Possibly CATASTROPHIC failure in txn commit")));
	}

	@Test
	void warnsOnFailedRecordRecreate() {
		// setup:
		InOrder inOrder = inOrder(ledger, txnCtx, processLogic, recordStreaming, recordCache);

		given(txnCtx.effectivePayer()).willReturn(effectivePayer);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getSignedTxnWrapper()).willReturn(Transaction.getDefaultInstance());
		// and:
		willThrow(IllegalStateException.class).given(ledger).commit();
		willThrow(IllegalStateException.class).given(recordCache).setFailInvalid(any(), any(), any(), anyLong());

		// when:
		subject.process(accessor, consensusTime, submittingMember);

		// then:
		inOrder.verify(txnCtx).resetFor(accessor, consensusTime, submittingMember);
		inOrder.verify(ledger).begin();
		inOrder.verify(processLogic).run();
		inOrder.verify(ledger).commit();
		inOrder.verify(recordCache).setFailInvalid(effectivePayer, accessor, consensusTime, submittingMember);
		inOrder.verify(ledger).rollback();
		inOrder.verify(recordStreaming, never()).run();
		// and:
		assertThat(logCaptor.errorLogs(), contains(
				Matchers.startsWith("Possibly CATASTROPHIC failure in txn commit"),
				Matchers.startsWith("Possibly CATASTROPHIC failure in failure record creation")));
	}

	@Test
	void warnsOnFailedRollback() {
		// setup:
		InOrder inOrder = inOrder(ledger, txnCtx, processLogic, recordStreaming, recordCache);

		given(txnCtx.effectivePayer()).willReturn(effectivePayer);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getSignedTxnWrapper()).willReturn(Transaction.getDefaultInstance());
		// and:
		willThrow(IllegalStateException.class).given(ledger).commit();
		willThrow(IllegalStateException.class).given(ledger).rollback();

		// when:
		subject.process(accessor, consensusTime, submittingMember);

		// then:
		inOrder.verify(txnCtx).resetFor(accessor, consensusTime, submittingMember);
		inOrder.verify(ledger).begin();
		inOrder.verify(processLogic).run();
		inOrder.verify(ledger).commit();
		inOrder.verify(recordCache).setFailInvalid(effectivePayer, accessor, consensusTime, submittingMember);
		inOrder.verify(ledger).rollback();
		inOrder.verify(recordStreaming, never()).run();
		// and:
		assertThat(logCaptor.errorLogs(), contains(
				Matchers.startsWith("Possibly CATASTROPHIC failure in txn commit"),
				Matchers.startsWith("Possibly CATASTROPHIC failure in txn rollback")));
	}
}