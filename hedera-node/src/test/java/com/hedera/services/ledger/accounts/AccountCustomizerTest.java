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
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hedera.services.ledger.properties.AccountProperty.USED_AUTOMATIC_ASSOCIATIONS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class AccountCustomizerTest {
	private HederaAccountCustomizer subject;

	@BeforeEach
	private void setupWithLiveChangeManager() {
		subject = new HederaAccountCustomizer();
	}

	@Test
	void testChanges() {
		subject
				.isDeleted(false)
				.expiry(100L)
				.memo("memo")
				.customizing(new MerkleAccount());

		assertNotNull(subject.getChanges());
		assertEquals(3, subject.getChanges().changed().size());
	}

	@Test
	void directlyCustomizesAnAccount() {
		final var ta = subject.isDeleted(true)
				.expiry(55L)
				.memo("Something!")
				.customizing(new MerkleAccount());

		assertEquals(55L, ta.getExpiry());
		assertTrue(ta.isDeleted());
		assertEquals("Something!", ta.getMemo());
	}

	@Test
	@SuppressWarnings("unchecked")
	void setsCustomizedProperties() {
		final var id = AccountID.newBuilder().setAccountNum(1L).build();
		final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger = mock(TransactionalLedger.class);
		final var customMemo = "alpha bravo charlie";
		final var customIsReceiverSigRequired = true;

		subject
				.isReceiverSigRequired(customIsReceiverSigRequired)
				.memo(customMemo);
		subject.customize(id, ledger);

		verify(ledger).set(id, MEMO, customMemo);
		verify(ledger).set(id, IS_RECEIVER_SIG_REQUIRED, customIsReceiverSigRequired);
	}

	@Test
	void changesExpectedKeyProperty() {
		final var key = new JKeyList();

		subject.key(key);

		assertEquals(Set.of(KEY), subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedMemoProperty() {
		final var memo = "standardization ftw?";

		subject.memo(memo);

		assertEquals(Set.of(MEMO), subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedProxyProperty() {
		final var proxy = new EntityId();

		subject.proxy(proxy);

		assertEquals(Set.of(PROXY), subject.getChanges().changedSet());
	}

	@Test
	void nullProxyAndAutoRenewAreNoops() {
		subject.proxy(null);
		subject.autoRenewAccount(null);
		assertTrue(subject.getChanges().changed().isEmpty());
	}

	@Test
	void changesExpectedAutoRenewAccountProperty() {
		final var autoRenewId = new EntityId();

		subject.autoRenewAccount(autoRenewId);

		assertEquals(
				Set.of(AUTO_RENEW_ACCOUNT_ID),
				subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedExpiryProperty() {
		final Long expiry = 1L;

		subject.expiry(expiry.longValue());

		assertEquals(Set.of(EXPIRY), subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedAutoRenewProperty() {
		final Long autoRenew = 1L;

		subject.autoRenewPeriod(autoRenew.longValue());

		assertEquals(
				Set.of(AUTO_RENEW_PERIOD),
				subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedIsSmartContractProperty() {
		final Boolean isSmartContract = Boolean.TRUE;

		subject.isSmartContract(isSmartContract.booleanValue());

		assertEquals(
				Set.of(IS_SMART_CONTRACT),
				subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedIsDeletedProperty() {
		final Boolean isDeleted = Boolean.TRUE;

		subject.isDeleted(isDeleted.booleanValue());

		assertEquals(Set.of(IS_DELETED), subject.getChanges().changedSet());
	}

	@Test
	void changesExpectedReceiverSigRequiredProperty() {
		final Boolean isSigRequired = Boolean.FALSE;

		subject.isReceiverSigRequired(isSigRequired);

		assertEquals(
				Set.of(IS_RECEIVER_SIG_REQUIRED),
				subject.getChanges().changedSet());
	}

	@Test
	void changesAutoAssociationFieldsAsExpected() {
		final int maxAutoAssociations = 1234;
		final int alreadyUsedAutoAssociations = 123;

		subject.maxAutomaticAssociations(maxAutoAssociations);
		subject.usedAutomaticAssociations(alreadyUsedAutoAssociations);

		assertEquals(
				Set.of(MAX_AUTOMATIC_ASSOCIATIONS, USED_AUTOMATIC_ASSOCIATIONS),
				subject.getChanges().changedSet());
	}
}
