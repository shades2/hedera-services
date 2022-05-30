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
import com.hedera.services.ledger.properties.BeanProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.PropertyChanges;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;

import java.util.Map;

import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.ALIAS;
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
import static java.util.Collections.unmodifiableMap;

/**
 * Implements a fluent builder for defining a set of standard customizations
 * relevant to any account on a ledger, no matter the id, account, and
 * property types.
 *
 * @param <K>
 * 		the type of the id used by the ledger.
 * @param <A>
 * 		the type of the account stored in the ledger.
 * @param <P>
 * 		the type of the properties applicable to the account.
 * @param <T>
 * 		the type of a customizer appropriate to {@code K}, {@code A}, {@code P}.
 */
public abstract class AccountCustomizer<
		K,
		A,
		P extends Enum<P> & BeanProperty<A>, T extends AccountCustomizer<K, A, P, T>> {
	public enum Option {
		KEY,
		MEMO,
		PROXY,
		EXPIRY,
		IS_DELETED,
		IS_SMART_CONTRACT,
		AUTO_RENEW_PERIOD,
		IS_RECEIVER_SIG_REQUIRED,
		MAX_AUTOMATIC_ASSOCIATIONS,
		USED_AUTOMATIC_ASSOCIATIONS,
		ALIAS,
		AUTO_RENEW_ACCOUNT_ID
	}

	private final Map<Option, P> optionProperties;
	private final PropertyChanges<P> changes;
	private final ChangeSummaryManager<A, P> changeManager;

	protected abstract T self();

	protected AccountCustomizer(
			final Class<P> propertyType,
			final Map<Option, P> optionProperties,
			final ChangeSummaryManager<A, P> changeManager
	) {
		this.changeManager = changeManager;
		this.optionProperties = optionProperties;
		this.changes = new PropertyChanges<>(propertyType);
	}

	public PropertyChanges<P> getChanges() {
		return changes;
	}

	public Map<Option, P> getOptionProperties() {
		return unmodifiableMap(optionProperties);
	}

	public A customizing(final A account) {
		changeManager.persist(changes, account);
		return account;
	}

	public void customize(final K id, final TransactionalLedger<K, P, A> ledger) {
		changes.changed().forEach(property ->
				ledger.set(id, property, changes.get(property)));
	}

	public T key(final JKey option) {
		changes.set(optionProperties.get(KEY), option);
		return self();
	}

	public T memo(final String option) {
		changes.set(optionProperties.get(MEMO), option);
		return self();
	}

	public T proxy(final EntityId option) {
		if (option != null) {
			changes.set(optionProperties.get(PROXY), option);
		}
		return self();
	}

	public T expiry(final long option) {
		changes.set(optionProperties.get(EXPIRY), option);
		return self();
	}

	public T alias(final ByteString option) {
		changes.set(optionProperties.get(ALIAS), option);
		return self();
	}

	public T isDeleted(final boolean option) {
		changes.set(optionProperties.get(IS_DELETED), option);
		return self();
	}

	public T autoRenewPeriod(final long option) {
		changes.set(optionProperties.get(AUTO_RENEW_PERIOD), option);
		return self();
	}

	public T isSmartContract(final boolean option) {
		changes.set(optionProperties.get(IS_SMART_CONTRACT), option);
		return self();
	}

	public T isReceiverSigRequired(final boolean option) {
		changes.set(optionProperties.get(IS_RECEIVER_SIG_REQUIRED), option);
		return self();
	}

	public T maxAutomaticAssociations(final int option) {
		changes.set(optionProperties.get(MAX_AUTOMATIC_ASSOCIATIONS), option);
		return self();
	}

	public T usedAutomaticAssociations(final int option) {
		changes.set(optionProperties.get(USED_AUTOMATIC_ASSOCIATIONS), option);
		return self();
	}

	public T autoRenewAccount(final EntityId option) {
		if (option != null) {
			changes.set(optionProperties.get(AUTO_RENEW_ACCOUNT_ID), option);
		}
		return self();
	}
}
