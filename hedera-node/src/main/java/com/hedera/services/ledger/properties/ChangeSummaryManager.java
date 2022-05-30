package com.hedera.services.ledger.properties;

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

import com.hedera.services.ledger.PropertyChangeObserver;

/**
 * Minimal implementation of a helper that manages summary changesets.
 * An extension point for possible future performance optimizations.
 *
 * @param <A>
 * 		the type of account being changed.
 * @param <P>
 * 		the property family whose changesets are to be summarized.
 */
public final class ChangeSummaryManager<A, P extends Enum<P> & BeanProperty<A>> {
	/**
	 * Flush a change set to a given object.
	 *
	 * @param changes
	 * 		the summary of changes made to the relevant property family
	 * @param account
	 * 		the account to receive the net changes
	 */
	public void persist(final PropertyChanges<P> changes, final A account) {
		changes.changed().forEach(property -> {
			if (property.isPrimitiveLong()) {
				property.longSetter().accept(account, changes.getLong(property));
			} else {
				property.setter().accept(account, changes.get(property));
			}
		});
	}

	/**
	 * Flush a changeset summary to a given object, notifying the given observer of each change.
	 *
	 * @param id
	 * 		the id to communicate to the observer
	 * @param changes
	 * 		the summary of changes made to the relevant property family.
	 * @param account
	 * 		the account to receive the net changes
	 * @param changeObserver
	 * 		the observer to be notified of the changes
	 * @param <K>
	 * 		the type of id used to identify this account
	 */
	public <K> void persistWithObserver(
			final K id,
			final PropertyChanges<P> changes,
			final A account,
			final PropertyChangeObserver<K, P> changeObserver
	) {
		changes.changed().forEach(property -> {
			if (property.isPrimitiveLong()) {
				final var newValue = changes.getLong(property);
				property.longSetter().accept(account, newValue);
				changeObserver.newLongProperty(id, property, newValue);
			} else {
				final var newValue = changes.get(property);
				property.setter().accept(account, newValue);
				changeObserver.newProperty(id, property, newValue);
			}
		});
	}
}
