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

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.PropertyChanges;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;

import static com.hedera.services.ledger.properties.AccountProperty.ALIAS;
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

/**
 * Implements a fluent builder for defining a set of standard customizations
 * relevant to any account on a ledger, no matter the id, account, and
 * property types.
 *
 * @param <T>
 * 		the type of a customizer appropriate to {@code K}, {@code A}, {@code P}.
 */
public abstract class AccountCustomizer<T extends AccountCustomizer<T>> {
	private final PropertyChanges<AccountProperty> changes;

	private final ChangeSummaryManager<MerkleAccount, AccountProperty> changeManager;

	protected abstract T self();

	protected AccountCustomizer(final ChangeSummaryManager<MerkleAccount, AccountProperty> changeManager) {
		this.changeManager = changeManager;
		this.changes = new PropertyChanges<>(AccountProperty.class);
	}

	public PropertyChanges<AccountProperty> getChanges() {
		return changes;
	}

	public MerkleAccount customizing(final MerkleAccount account) {
		changeManager.persist(changes, account);
		return account;
	}

	public void customize(
			final AccountID id,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger
	) {
		changeManager.setAll(changes, ledger, id);
	}

	public T key(final JKey option) {
		changes.set(KEY, option);
		return self();
	}

	public T memo(final String option) {
		changes.set(MEMO, option);
		return self();
	}

	public T proxy(final EntityId option) {
		if (option != null) {
			changes.set(PROXY, option);
		}
		return self();
	}

	public T expiry(final long option) {
		changes.set(EXPIRY, option);
		return self();
	}

	public T alias(final ByteString option) {
		changes.set(ALIAS, option);
		return self();
	}

	public T isDeleted(final boolean option) {
		changes.set(IS_DELETED, option);
		return self();
	}

	public T autoRenewPeriod(final long option) {
		changes.set(AUTO_RENEW_PERIOD, option);
		return self();
	}

	public T isSmartContract(final boolean option) {
		changes.set(IS_SMART_CONTRACT, option);
		return self();
	}

	public T isReceiverSigRequired(final boolean option) {
		changes.set(IS_RECEIVER_SIG_REQUIRED, option);
		return self();
	}

	public T maxAutomaticAssociations(final int option) {
		changes.set(MAX_AUTOMATIC_ASSOCIATIONS, option);
		return self();
	}

	public T usedAutomaticAssociations(final int option) {
		changes.set(USED_AUTOMATIC_ASSOCIATIONS, option);
		return self();
	}

	public T autoRenewAccount(final EntityId option) {
		if (option != null) {
			changes.set(AUTO_RENEW_ACCOUNT_ID, option);
		}
		return self();
	}
}
