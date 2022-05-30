package com.hedera.services.ledger.accounts;

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

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.TestAccountProperty;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.submerkle.EntityId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.EXPIRY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.IS_DELETED;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.KEY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.MEMO;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.PROXY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.TestAccountProperty.FLAG;
import static com.hedera.services.ledger.properties.TestAccountProperty.OBJ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AccountCustomizerTest {
	private TestAccountCustomizer subject;
	private ChangeSummaryManager<TestAccount, TestAccountProperty> changeManager;

	private void setupWithMockChangeManager() {
		changeManager = mock(ChangeSummaryManager.class);
		subject = new TestAccountCustomizer(changeManager);
	}

	private void setupWithLiveChangeManager() {
		subject = new TestAccountCustomizer(new ChangeSummaryManager<>());
	}

	@Test
	void testChanges() {
		setupWithLiveChangeManager();
		subject
				.isDeleted(false)
				.expiry(100L)
				.memo("memo")
				.customizing(new TestAccount());

		assertNotNull(subject.getChanges());
		assertEquals(3, subject.getChanges().changed().size());
	}

	@Test
	void directlyCustomizesAnAccount() {
		setupWithLiveChangeManager();

		final var ta = subject.isDeleted(true)
				.expiry(55L)
				.memo("Something!")
				.customizing(new TestAccount());

		assertEquals(55L, ta.value);
		assertTrue(ta.flag);
		assertEquals("Something!", ta.thing);
	}

	@Test
	void setsCustomizedProperties() {
		setupWithLiveChangeManager();
		final var id = 1L;
		final TransactionalLedger<Long, TestAccountProperty, TestAccount> ledger = mock(TransactionalLedger.class);
		final var customMemo = "alpha bravo charlie";
		final var customIsReceiverSigRequired = true;

		subject
				.isReceiverSigRequired(customIsReceiverSigRequired)
				.memo(customMemo);
		subject.customize(id, ledger);

		verify(ledger).set(id, OBJ, customMemo);
		verify(ledger).set(id, FLAG, customIsReceiverSigRequired);
	}

	@Test
	void changesExpectedKeyProperty() {
		setupWithMockChangeManager();
		final var key = new JKeyList();

		subject.key(key);

		assertEquals(Set.of(TestAccountCustomizer.OPTION_PROPERTIES.get(KEY)), subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedMemoProperty() {
		setupWithMockChangeManager();
		final var memo = "standardization ftw?";

		subject.memo(memo);

		assertEquals(Set.of(TestAccountCustomizer.OPTION_PROPERTIES.get(MEMO)), subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedProxyProperty() {
		setupWithMockChangeManager();
		final var proxy = new EntityId();

		subject.proxy(proxy);

		assertEquals(Set.of(TestAccountCustomizer.OPTION_PROPERTIES.get(PROXY)), subject.getChanges().changedSet());
	}

	@Test
	void nullProxyAndAutoRenewAreNoops() {
		setupWithMockChangeManager();

		subject.proxy(null);
		subject.autoRenewAccount(null);

		verifyNoInteractions(changeManager);
	}

	@Test
	void changesExpectedAutoRenewAccountProperty() {
		setupWithMockChangeManager();
		final var autoRenewId = new EntityId();

		subject.autoRenewAccount(autoRenewId);

		assertEquals(
				Set.of(TestAccountCustomizer.OPTION_PROPERTIES.get(AUTO_RENEW_ACCOUNT_ID)),
				subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedExpiryProperty() {
		setupWithMockChangeManager();
		final Long expiry = 1L;

		subject.expiry(expiry.longValue());

		assertEquals(Set.of(TestAccountCustomizer.OPTION_PROPERTIES.get(EXPIRY)), subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedAutoRenewProperty() {
		setupWithMockChangeManager();
		final Long autoRenew = 1L;

		subject.autoRenewPeriod(autoRenew.longValue());

		assertEquals(
				Set.of(TestAccountCustomizer.OPTION_PROPERTIES.get(AUTO_RENEW_PERIOD)),
				subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedIsSmartContractProperty() {
		setupWithMockChangeManager();
		final Boolean isSmartContract = Boolean.TRUE;

		subject.isSmartContract(isSmartContract.booleanValue());

		assertEquals(
				Set.of(TestAccountCustomizer.OPTION_PROPERTIES.get(IS_SMART_CONTRACT)),
				subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedIsDeletedProperty() {
		setupWithMockChangeManager();
		final Boolean isDeleted = Boolean.TRUE;

		subject.isDeleted(isDeleted.booleanValue());

		assertEquals(Set.of(TestAccountCustomizer.OPTION_PROPERTIES.get(IS_DELETED)), subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedReceiverSigRequiredProperty() {
		setupWithMockChangeManager();
		final Boolean isSigRequired = Boolean.FALSE;

		subject.isReceiverSigRequired(isSigRequired);

		assertEquals(
				Set.of(TestAccountCustomizer.OPTION_PROPERTIES.get(IS_RECEIVER_SIG_REQUIRED)),
				subject.getChanges().changedSet());
	}

	@Test
	void changesAutoAssociationFieldsAsExpected() {
		setupWithMockChangeManager();
		final int maxAutoAssociations = 1234;
		final int alreadyUsedAutoAssociations = 123;

		subject.maxAutomaticAssociations(maxAutoAssociations);
		subject.usedAutomaticAssociations(alreadyUsedAutoAssociations);

		assertEquals(
				Set.of(TestAccountCustomizer.OPTION_PROPERTIES.get(MAX_AUTOMATIC_ASSOCIATIONS)),
				subject.getChanges().changedSet());
		assertEquals(
				Set.of(TestAccountCustomizer.OPTION_PROPERTIES.get(USED_AUTOMATIC_ASSOCIATIONS)),
				subject.getChanges().changedSet());
	}
}
