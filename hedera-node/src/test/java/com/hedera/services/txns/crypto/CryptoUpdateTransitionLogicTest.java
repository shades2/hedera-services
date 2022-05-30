package com.hedera.services.txns.crypto;

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

import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;

class CryptoUpdateTransitionLogicTest {
	private static final Instant CONSENSUS_TIME = Instant.ofEpochSecond(1_234_567L);
	private static final long CUR_EXPIRY = CONSENSUS_TIME.getEpochSecond() + 2L;
	private static final long NEW_EXPIRY = CONSENSUS_TIME.getEpochSecond() + 7776000L;
	private static final int CUR_MAX_AUTOMATIC_ASSOCIATIONS = 10;
	private static final int NEW_MAX_AUTOMATIC_ASSOCIATIONS = 15;

	private static final Key SOME_KEY = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	private static final long SOME_AUTO_RENEW_PERIOD = 100_001L;
	private static final AccountID SOME_PROXY = AccountID.newBuilder().setAccountNum(4_321L).build();
	private static final AccountID PAYER = AccountID.newBuilder().setAccountNum(1_234L).build();
	private static final AccountID TARGET = AccountID.newBuilder().setAccountNum(9_999L).build();
	private static final String SOME_MEMO = "Not since life began";

	private boolean useLegacyFields;
	private HederaLedger ledger;
	private OptionValidator validator;
	private TransactionBody cryptoUpdateTxn;
	private SigImpactHistorian sigImpactHistorian;
	private TransactionContext txnCtx;
	private SignedTxnAccessor accessor;
	private GlobalDynamicProperties dynamicProperties;
	private CryptoUpdateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		useLegacyFields = false;

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
		ledger = mock(HederaLedger.class);
		accessor = mock(SignedTxnAccessor.class);
		validator = mock(OptionValidator.class);
		sigImpactHistorian = mock(SigImpactHistorian.class);
		dynamicProperties = mock(GlobalDynamicProperties.class);
		withRubberstampingValidator();

		subject = new CryptoUpdateTransitionLogic(ledger, validator, sigImpactHistorian, txnCtx, dynamicProperties);
	}

	@Test
	void updatesProxyIfPresent() {
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenTxnCtx(EnumSet.of(PROXY));

		subject.doStateTransition();

		verify(ledger).customize(argThat(TARGET::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		verify(sigImpactHistorian).markEntityChanged(TARGET.getAccountNum());
		final var changes = captor.getValue().getChanges();
		assertEquals(1, changes.changed().size());
		assertEquals(EntityId.fromGrpcAccountId(SOME_PROXY), changes.get(PROXY));
	}


	@Test
	void updatesReceiverSigReqIfPresent() {
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenTxnCtx(EnumSet.of(IS_RECEIVER_SIG_REQUIRED));

		subject.doStateTransition();

		verify(ledger).customize(argThat(TARGET::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		final var changes = captor.getValue().getChanges();
		assertEquals(1, changes.changed().size());
		assertEquals(true, changes.get(IS_RECEIVER_SIG_REQUIRED));
	}

	@Test
	void updatesReceiverSigReqIfTrueInLegacy() {
		useLegacyFields = true;
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenTxnCtx(EnumSet.of(IS_RECEIVER_SIG_REQUIRED));

		subject.doStateTransition();

		verify(ledger).customize(argThat(TARGET::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		final var changes = captor.getValue().getChanges();
		assertEquals(1, changes.changed().size());
		assertEquals(true, changes.get(IS_RECEIVER_SIG_REQUIRED));
	}

	@Test
	void updatesExpiryIfPresent() {
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenTxnCtx(EnumSet.of(EXPIRY));

		subject.doStateTransition();

		verify(ledger).customize(argThat(TARGET::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		final var changes = captor.getValue().getChanges();
		assertEquals(1, changes.changed().size());
		assertEquals(NEW_EXPIRY, (long) changes.get(EXPIRY));
	}

	@Test
	void updatesMaxAutomaticAssociationsIfPresent() {
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenTxnCtx(EnumSet.of(MAX_AUTOMATIC_ASSOCIATIONS));
		given(ledger.alreadyUsedAutomaticAssociations(any())).willReturn(CUR_MAX_AUTOMATIC_ASSOCIATIONS);
		given(dynamicProperties.areTokenAssociationsLimited()).willReturn(true);
		given(dynamicProperties.maxTokensPerAccount()).willReturn(NEW_MAX_AUTOMATIC_ASSOCIATIONS + 1);

		subject.doStateTransition();

		verify(ledger).customize(argThat(TARGET::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		final var changes = captor.getValue().getChanges();
		assertEquals(1, changes.changed().size());
		assertEquals(NEW_MAX_AUTOMATIC_ASSOCIATIONS, (int) changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
	}

	@Test
	void updateMaxAutomaticAssociationsFailAsExpectedWithMaxLessThanAlreadyExisting() {
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenTxnCtx(EnumSet.of(MAX_AUTOMATIC_ASSOCIATIONS));
		given(ledger.alreadyUsedAutomaticAssociations(any())).willReturn(NEW_MAX_AUTOMATIC_ASSOCIATIONS + 1);

		subject.doStateTransition();

		verify(ledger, never()).customize(argThat(TARGET::equals), captor.capture());
		verify(txnCtx).setStatus(EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT);
	}

	@Test
	void updateMaxAutomaticAssociationsFailAsExpectedWithMaxMoreThanAllowedTokenAssociations() {
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenTxnCtx(EnumSet.of(MAX_AUTOMATIC_ASSOCIATIONS));
		given(ledger.alreadyUsedAutomaticAssociations(any())).willReturn(CUR_MAX_AUTOMATIC_ASSOCIATIONS);
		given(dynamicProperties.areTokenAssociationsLimited()).willReturn(true);
		given(dynamicProperties.maxTokensPerAccount()).willReturn(NEW_MAX_AUTOMATIC_ASSOCIATIONS - 1);

		subject.doStateTransition();

		verify(ledger, never()).customize(argThat(TARGET::equals), captor.capture());
		verify(txnCtx).setStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
	}

	@Test
	void updatesMemoIfPresent() {
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenTxnCtx(EnumSet.of(MEMO));

		subject.doStateTransition();

		verify(ledger).customize(argThat(TARGET::equals), captor.capture());
		final var changes = captor.getValue().getChanges();
		assertEquals(1, changes.changed().size());
		assertEquals(SOME_MEMO, changes.get(MEMO));
	}

	@Test
	void updatesAutoRenewIfPresent() {
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenTxnCtx(EnumSet.of(AUTO_RENEW_PERIOD));

		subject.doStateTransition();

		verify(ledger).customize(argThat(TARGET::equals), captor.capture());
		final var changes = captor.getValue().getChanges();
		assertEquals(1, changes.changed().size());
		assertEquals(SOME_AUTO_RENEW_PERIOD, changes.get(AUTO_RENEW_PERIOD));
	}

	@Test
	void updatesKeyIfPresent() throws Throwable {
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenTxnCtx(EnumSet.of(KEY));

		subject.doStateTransition();

		verify(ledger).customize(argThat(TARGET::equals), captor.capture());
		final var changes = captor.getValue().getChanges();
		assertEquals(1, changes.changed().size());
		assertEquals(SOME_KEY, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
	}

	@Test
	void hasCorrectApplicability() {
		givenTxnCtx();

		assertTrue(subject.applicability().test(cryptoUpdateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void rejectsKeyWithBadEncoding() {
		rejectsKey(unmappableKey());
	}

	@Test
	void rejectsInvalidKey() {
		rejectsKey(emptyKey());
	}

	@Test
	void rejectsInvalidMemo() {
		givenTxnCtx(EnumSet.of(MEMO));
		given(validator.memoCheck(SOME_MEMO)).willReturn(MEMO_TOO_LONG);

		assertEquals(MEMO_TOO_LONG, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	@Test
	void rejectsInvalidAutoRenewPeriod() {
		givenTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	@Test
	void acceptsValidTxn() {
		givenTxnCtx();

		assertEquals(OK, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	@Test
	void rejectsDetachedAccount() {
		givenTxnCtx();
		given(ledger.isDetached(TARGET)).willReturn(true);

		subject.doStateTransition();

		verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void rejectsInvalidExpiry() {
		givenTxnCtx();
		given(validator.isValidExpiry(any())).willReturn(false);

		subject.doStateTransition();

		verify(txnCtx).setStatus(INVALID_EXPIRATION_TIME);
	}

	@Test
	void permitsDetachedIfOnlyExtendingExpiry() {
		givenTxnCtx(EnumSet.of(EXPIRY));
		given(ledger.isDetached(TARGET)).willReturn(true);

		subject.doStateTransition();

		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	void rejectsInvalidExpiryForDetached() {
		givenTxnCtx(EnumSet.of(EXPIRY), EnumSet.of(EXPIRY));
		given(ledger.isDetached(TARGET)).willReturn(true);
		given(ledger.expiry(TARGET)).willReturn(CUR_EXPIRY);

		subject.doStateTransition();

		verify(txnCtx).setStatus(EXPIRATION_REDUCTION_NOT_ALLOWED);
	}

	@Test
	void rejectsSmartContract() {
		givenTxnCtx();
		given(ledger.isSmartContract(TARGET)).willReturn(true);

		subject.doStateTransition();

		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	void preemptsMissingAccountException() {
		givenTxnCtx();
		given(ledger.exists(TARGET)).willReturn(false);

		subject.doStateTransition();

		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	void translatesMissingAccountException() {
		givenTxnCtx();
		willThrow(MissingEntityException.class).given(ledger).customize(any(), any());

		subject.doStateTransition();

		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	void translatesAccountIsDeletedException() {
		givenTxnCtx();
		willThrow(DeletedAccountException.class).given(ledger).customize(any(), any());

		subject.doStateTransition();

		verify(txnCtx).setStatus(ACCOUNT_DELETED);
	}

	@Test
	void translatesUnknownException() {
		givenTxnCtx();
		cryptoUpdateTxn = cryptoUpdateTxn.toBuilder()
				.setCryptoUpdateAccount(cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder().setKey(unmappableKey()))
				.build();
		given(accessor.getTxn()).willReturn(cryptoUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		subject.doStateTransition();

		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	private Key unmappableKey() {
		return Key.getDefaultInstance();
	}

	private Key emptyKey() {
		return Key.newBuilder().setThresholdKey(
				ThresholdKey.newBuilder()
						.setKeys(KeyList.getDefaultInstance())
						.setThreshold(0)
		).build();
	}

	private void rejectsKey(final Key key) {
		givenTxnCtx();
		cryptoUpdateTxn = cryptoUpdateTxn.toBuilder()
				.setCryptoUpdateAccount(cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder().setKey(key))
				.build();

		assertEquals(BAD_ENCODING, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	private void givenTxnCtx() {
		givenTxnCtx(EnumSet.copyOf(List.of(
				KEY,
				MEMO,
				PROXY,
				EXPIRY,
				IS_RECEIVER_SIG_REQUIRED,
				AUTO_RENEW_PERIOD)), EnumSet.noneOf(AccountProperty.class));
	}

	private void givenTxnCtx(final EnumSet<AccountProperty> updating) {
		givenTxnCtx(updating, EnumSet.noneOf(AccountProperty.class));
	}

	private void givenTxnCtx(
			final EnumSet<AccountProperty> updating,
			final EnumSet<AccountProperty> misconfiguring
	) {
		final var op = CryptoUpdateTransactionBody.newBuilder();
		if (updating.contains(SOME_MEMO)) {
			op.setMemo(StringValue.newBuilder().setValue(SOME_MEMO).build());
		}
		if (updating.contains(SOME_KEY)) {
			op.setKey(SOME_KEY);
		}
		if (updating.contains(SOME_PROXY)) {
			op.setProxyAccountID(SOME_PROXY);
		}
		if (updating.contains(EXPIRY)) {
			if (misconfiguring.contains(EXPIRY)) {
				op.setExpirationTime(Timestamp.newBuilder().setSeconds(CUR_EXPIRY - 1));
			} else {
				op.setExpirationTime(Timestamp.newBuilder().setSeconds(NEW_EXPIRY));
			}
		}
		if (updating.contains(IS_RECEIVER_SIG_REQUIRED)) {
			if (!useLegacyFields) {
				op.setReceiverSigRequiredWrapper(BoolValue.newBuilder().setValue(true));
			} else {
				op.setReceiverSigRequired(true);
			}
		}
		if (updating.contains(SOME_AUTO_RENEW_PERIOD)) {
			op.setAutoRenewPeriod(Duration.newBuilder().setSeconds(SOME_AUTO_RENEW_PERIOD));
		}
		if (updating.contains(MAX_AUTOMATIC_ASSOCIATIONS)) {
			op.setMaxAutomaticTokenAssociations(Int32Value.of(NEW_MAX_AUTOMATIC_ASSOCIATIONS));
		}
		op.setAccountIDToUpdate(TARGET);
		cryptoUpdateTxn = TransactionBody.newBuilder().setTransactionID(ourTxnId()).setCryptoUpdateAccount(op).build();
		given(accessor.getTxn()).willReturn(cryptoUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(ledger.exists(TARGET)).willReturn(true);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(PAYER)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(CONSENSUS_TIME.getEpochSecond()))
				.build();
	}

	private void withRubberstampingValidator() {
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(validator.isValidExpiry(any())).willReturn(true);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(validator.memoCheck(any())).willReturn(OK);
	}
}
