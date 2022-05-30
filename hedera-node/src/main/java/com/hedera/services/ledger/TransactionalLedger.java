package com.hedera.services.ledger;

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

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.properties.BeanProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.PropertyChanges;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.MiscUtils.readableProperty;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static java.util.stream.Collectors.joining;

/**
 * Provides a ledger with transactional semantics. Changes during a transaction
 * are summarized in per-entity change sets, which are then either saved to a
 * backing store when the transaction is committed; or dropped with no effects
 * upon a rollback.
 *
 * @param <K>
 * 		the type of id used by the ledger
 * @param <P>
 * 		the family of properties associated to entities in the ledger
 * @param <A>
 * 		the type of ledger entity
 */
public class TransactionalLedger<K, P extends Enum<P> & BeanProperty<A>, A> implements BackingStore<K, A> {
	private static final Logger log = LogManager.getLogger(TransactionalLedger.class);

	public static final int MAX_ENTITIES_CONCEIVABLY_TOUCHED_IN_LEDGER_TXN = 42;

	private final P[] allProps;
	private final Set<K> deadKeys = new HashSet<>();
	private final List<K> createdKeys = new ArrayList<>(MAX_ENTITIES_CONCEIVABLY_TOUCHED_IN_LEDGER_TXN);
	private final List<K> changedKeys = new ArrayList<>(MAX_ENTITIES_CONCEIVABLY_TOUCHED_IN_LEDGER_TXN);
	private final List<K> removedKeys = new ArrayList<>(MAX_ENTITIES_CONCEIVABLY_TOUCHED_IN_LEDGER_TXN);
	private final Map<K, PropertyChanges<P>> changes = new HashMap<>();

	private final Class<P> propertyType;
	private final Supplier<A> newEntity;
	private final Consumer<K> finalizeAction;
	private final ChangesFactory<P> changesFactory;
	private final BackingStore<K, A> entities;
	private final ChangeSummaryManager<A, P> changeManager;
	private final TransactionalLedger<K, P, A> entitiesLedger;

	private boolean isInTransaction = false;
	private boolean changesFactoryManager = false;
	private Consumer<K> previewAction = null;
	private Function<K, String> keyToString = null;
	private EntityChangeSet<K, A, P> pendingChanges = null;
	private CommitInterceptor<K, A, P> commitInterceptor = null;
	private PropertyChangeObserver<K, P> propertyChangeObserver = null;

	public TransactionalLedger(
			final Class<P> propertyType,
			final Supplier<A> newEntity,
			final BackingStore<K, A> entities,
			final ChangeSummaryManager<A, P> changeManager
	) {
		this(propertyType, newEntity, entities, new ChangesFactory<>(propertyType), changeManager);
		changesFactoryManager = true;
	}

	public TransactionalLedger(
			final Class<P> propertyType,
			final Supplier<A> newEntity,
			final BackingStore<K, A> entities,
			final ChangesFactory<P> changesFactory,
			final ChangeSummaryManager<A, P> changeManager
	) {
		this.entities = entities;
		this.allProps = propertyType.getEnumConstants();
		this.newEntity = newEntity;
		this.propertyType = propertyType;
		this.changeManager = changeManager;
		this.changesFactory = changesFactory;

		if (entities instanceof TransactionalLedger) {
			this.entitiesLedger = (TransactionalLedger<K, P, A>) entities;
		} else {
			this.entitiesLedger = null;
		}

		this.finalizeAction = id -> entities.put(id, getFinalized(id));
	}

	/**
	 * Constructs an active ledger backed by the given source ledger.
	 *
	 * @param sourceLedger
	 * 		the ledger to wrap
	 * @param <K>
	 * 		the type of id used by the ledger
	 * @param <P>
	 * 		the family of properties associated to entities in the ledger
	 * @param <A>
	 * 		the type of ledger entity
	 * @return an active ledger wrapping the source
	 */
	public static <K, P extends Enum<P> & BeanProperty<A>, A> TransactionalLedger<K, P, A> activeLedgerWrapping(
			@NotNull final TransactionalLedger<K, P, A> sourceLedger
	) {
		final var wrapper = new TransactionalLedger<>(
				sourceLedger.getPropertyType(),
				sourceLedger.getNewEntity(),
				sourceLedger,
				sourceLedger.getChangesFactory(),
				sourceLedger.getChangeManager());
		wrapper.begin();
		return wrapper;
	}

	public boolean isInTransaction() {
		return isInTransaction;
	}

	public String changeSetSoFar() {
		final var desc = new StringBuilder("{");
		final var isFirstChange = new AtomicBoolean(true);
		changes.forEach((id, value) -> {
			if (!isFirstChange.get()) {
				desc.append(", ");
			}
			final var accountInDeadAccounts = deadKeys.contains(id) ? "*DEAD* " : "";
			final var accountNotInDeadAccounts = deadKeys.contains(id) ? "*NEW -> DEAD* " : "*NEW* ";
			final var prefix = entities.contains(id)
					? accountInDeadAccounts
					: accountNotInDeadAccounts;
			desc.append(prefix)
					.append(readable(id))
					.append(": [");
			desc.append(
					value.changed().stream()
							.map(prop -> String.format("%s -> %s", prop, readableProperty(value.get(prop))))
							.collect(joining(", ")));
			desc.append("]");
			isFirstChange.set(false);
		});
		deadKeys.stream()
				.filter(id -> !changes.containsKey(id))
				.forEach(id -> {
					if (!isFirstChange.get()) {
						desc.append(", ");
					}
					desc.append("*DEAD* ").append(readable(id));
					isFirstChange.set(false);
				});
		return desc.append("}").toString();
	}

	public ResponseCodeEnum validate(final K id, final LedgerCheck<A, P> ledgerCheck) {
		if (!exists(id)) {
			return INVALID_ACCOUNT_ID;
		}
		final var changeSet = changes.get(id);
		if (entitiesLedger == null) {
			final var getterTarget = toGetterTarget(id);
			return ledgerCheck.checkUsing(getterTarget, changeSet);
		} else {
			// If we are backed by a ledger, it is far more efficient to source properties
			// by using get(), since each call to a ledger's getRef() creates a new entity
			// and sets all its properties.
			final var extantProps = extantLedgerPropsFor(id);
			return ledgerCheck.checkUsing(extantProps, changeSet);
		}
	}

	public void setKeyToString(final Function<K, String> keyToString) {
		this.keyToString = keyToString;
	}

	public void setCommitInterceptor(final CommitInterceptor<K, A, P> commitInterceptor) {
		this.commitInterceptor = commitInterceptor;
		pendingChanges = new EntityChangeSet<>();
		previewAction = id -> {
			final var entity = entities.contains(id) ? entities.getImmutableRef(id) : null;
			pendingChanges.include(id, entity, changes.get(id));
		};
	}

	public void setPropertyChangeObserver(final PropertyChangeObserver<K, P> propertyChangeObserver) {
		this.propertyChangeObserver = propertyChangeObserver;
	}

	public void begin() {
		ensureNotInTxn();
		isInTransaction = true;
		if (pendingChanges != null) {
			pendingChanges.clear();
		}
	}

	public void undoChangesOfType(List<P> properties) {
		throwIfNotInTxn();
		if (!changedKeys.isEmpty()) {
			for (var key : changedKeys) {
				final var changeSet = changes.get(key);
				if (changeSet != null) {
					for (final var property : properties) {
						changeSet.undo(property);
					}
				}
			}
		}
	}

	public void undoCreations() {
		if (!isInTransaction) {
			throw new IllegalStateException("Cannot undo created keys, no transaction is active");
		}
		createdKeys.clear();
	}

	public void rollback() {
		if (!isInTransaction) {
			throw new IllegalStateException("Cannot perform rollback, no transaction is active");
		}

		changes.clear();
		deadKeys.clear();
		changedKeys.clear();
		createdKeys.clear();
		removedKeys.clear();

		isInTransaction = false;
	}

	public void commit() {
		throwIfNotInTxn();

		try {
			if (commitInterceptor != null) {
				computePendingChanges();
				commitInterceptor.preview(pendingChanges);
				flushPendingChanges();
			} else {
				flushListed(changedKeys);
				flushListed(createdKeys);
			}

			changes.clear();

			if (!deadKeys.isEmpty()) {
				if (commitInterceptor == null || !commitInterceptor.completesPendingRemovals()) {
					removedKeys.forEach(entities::remove);
				}
				deadKeys.clear();
				removedKeys.clear();
			}

			isInTransaction = false;
		} catch (Exception e) {
			String changeDesc = "<N/A>";
			try {
				changeDesc = changeSetSoFar();
			} catch (Exception f) {
				log.warn("Unable to describe pending change set", f);
			}
			log.error("Catastrophic failure during commit of {}", changeDesc);
			throw e;
		}
	}

	// --- TransactionalLedger API ---
	/**
	 * Indicates whether an account is present (in either a saved or transient
	 * state---either is considered extant).
	 *
	 * @param id the id of the relevant account.
	 * @return whether the account is present.
	 */
	public boolean exists(final K id) {
		return existsOrIsPendingCreation(id) && !isZombie(id);
	}

	/**
	 * Indicates whether an account is present solely in a transient state.
	 *
	 * @param id the id of the relevant account.
	 * @return whether the account has no saved state, only transient.
	 */
	public boolean existsPending(final K id) {
		return isPendingCreation(id);
	}

	/**
	 * Sets value of a given property to a given value for the specified account.
	 *
	 * @param id the id of the account to update.
	 * @param property the property to change.
	 * @param value the new value of the property.
	 */
	public void set(final K id, final P property, final Object value) {
		assertIsSettable(id);
		changes.computeIfAbsent(id, ignore -> {
			changedKeys.add(id);
			return changesFactory.get();
		}).set(property, value);
	}

	/**
	 * Sets value of a given long property to a given value for the specified account.
	 *
	 * @param id the id of the account to update
	 * @param property the long property to change
	 * @param value the new value
	 */
	public void setLong(final K id, final P property, final long value) {
		assertIsSettable(id);
		throw new AssertionError("Not implemented");
	}

	/**
	 * Gets the current property value of the specified account. This value
	 * need not be persisted to a durable backing store.
	 *
	 * @param id the id of the relevant account.
	 * @param property which property to fetch.
	 * @return the value of the property.
	 */
	public Object get(final K id, final P property) {
		throwIfMissing(id);
		final var changeSet = changes.get(id);
		if (changeSet != null && changeSet.includes(property)) {
			return changeSet.get(property);
		} else {
			if (entitiesLedger == null) {
				return property.getter().apply(toGetterTarget(id));
			} else {
				/* If we are backed by a ledger, it is far more efficient to source properties
				 * by using get(), since each call to a ledger's getRef() creates a new entity
				 * and sets all its properties. */
				return entitiesLedger.contains(id)
						? entitiesLedger.get(id, property)
						: property.getter().apply(newEntity.get());
			}
		}
	}

	/**
	 * Creates an new account with the given id and all default property values.
	 *
	 * @param id the id to use for the new account.
	 */
	public void create(final K id) {
		assertIsCreatable(id);
		changes.put(id, changesFactory.get());
		createdKeys.add(id);
	}

	/**
	 * Forgets everything about the account with the given id.
	 *
	 * @param id the id of the account to be forgotten.
	 */
	public void destroy(final K id) {
		throwIfNotInTxn();
		if (!deadKeys.contains(id)) {
			deadKeys.add(id);
			removedKeys.add(id);
		}
	}

	// --- BackingStore implementation ---
	/**
	 * {@inheritDoc}
	 */
	@Override
	public A getRef(final K id) {
		if (!exists(id)) {
			return null;
		}
		// Create a new entity, then set its properties based on the combination of
		// the existing (or default) entity's properties, plus the current change set
		final var entity = newEntity.get();
		if (entitiesLedger == null) {
			final var getterTarget = toGetterTarget(id);
			setPropsWithSource(id, entity, prop -> prop.getter().apply(getterTarget));
		} else {
			setPropsWithSource(id, entity, extantLedgerPropsFor(id));
		}
		return entity;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public A getImmutableRef(final K id) {
		return getRef(id);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put(final K id, final A entity) {
		throwIfNotInTxn();
		if (isZombie(id)) {
			deadKeys.remove(id);
			removedKeys.remove(id);
		}
		// The ledger wrapping us may have created an entity we don't have, so catch up on that if necessary;
		// note this differs from the semantics of set() above, which throws if the target entity is missing
		if (!exists(id)) {
			create(id);
		}
		// Now accumulate the entire change-set represented by the received entity
		for (final var prop : allProps) {
			set(id, prop, prop.getter().apply(entity));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove(final K id) {
		destroy(id);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean contains(final K id) {
		return exists(id);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<K> idSet() {
		return entities.idSet();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long size() {
		return entities.size();
	}

	// --- Internal helpers ---
	A getFinalized(final K id) {
		final A entity = entities.contains(id) ? entities.getRef(id) : newEntity.get();
		return finalized(id, entity, changes.get(id));
	}

	ChangesFactory<P> getChangesFactory() {
		return changesFactory;
	}

	ChangeSummaryManager<A, P> getChangeManager() {
		return changeManager;
	}

	Supplier<A> getNewEntity() {
		return newEntity;
	}

	Class<P> getPropertyType() {
		return propertyType;
	}

	private A finalized(final K id, final A entity, @Nullable final PropertyChanges<P> changes) {
		if (changes != null) {
			if (propertyChangeObserver != null) {
				changeManager.persistWithObserver(id, changes, entity, propertyChangeObserver);
			} else {
				changeManager.persist(changes, entity);
			}
		}
		return entity;
	}

	private void setPropsWithSource(final K id, final A entity, final Function<P, Object> extantProps) {
		final var changeSet = changes.get(id);
		for (final var prop : allProps) {
			if (changeSet != null && changeSet.includes(prop)) {
				prop.setter().accept(entity, changeSet.get(prop));
			} else {
				prop.setter().accept(entity, extantProps.apply(prop));
			}
		}
	}

	private Function<P, Object> extantLedgerPropsFor(final K id) {
		Objects.requireNonNull(entitiesLedger);
		return entitiesLedger.contains(id)
				? prop -> entitiesLedger.get(id, prop)
				: newDefaultPropertySource();
	}

	private void throwIfNotInTxn() {
		if (!isInTransaction) {
			throw new IllegalStateException("No active transaction");
		}
	}

	private void ensureNotInTxn() {
		if (isInTransaction) {
			log.warn("Ledger with property type {} still in transaction at begin()", propertyType::getSimpleName);
			rollback();
		}
	}

	private void computePendingChanges() {
		computeForRetainedIn(changedKeys, previewAction);
		computeForRetainedIn(createdKeys, previewAction);
		if (!removedKeys.isEmpty()) {
			computeRemovals();
		}
	}

	private void computeRemovals() {
		for (final var id : removedKeys) {
			final var entity = entities.getImmutableRef(id);
			// Ignore entities that were created and destroyed within the transaction
			if (entity != null) {
				pendingChanges.includeRemoval(id, entity);
			}
		}
	}

	private void flushPendingChanges() {
		for (int i = 0, n = pendingChanges.retainedSize(); i < n; i++) {
			final var id = pendingChanges.id(i);
			final var cachedEntity = pendingChanges.entity(i);
			final var entity = (cachedEntity == null) ? newEntity.get() : entities.getRef(id);
			entities.put(id, finalized(id, entity, pendingChanges.changes(i)));
		}
		createdKeys.clear();
		changedKeys.clear();
	}

	private void flushListed(final List<K> l) {
		if (computeForRetainedIn(l, finalizeAction)) {
			l.clear();
		}
	}

	private boolean computeForRetainedIn(final List<K> l, final Consumer<K> action) {
		if (l.isEmpty()) {
			return false;
		}
		for (final var key : l) {
			if (!deadKeys.contains(key)) {
				action.accept(key);
			}
		}
		return true;
	}

	private A toGetterTarget(K id) {
		return isPendingCreation(id) ? newEntity.get() : entities.getImmutableRef(id);
	}

	private boolean isPendingCreation(K id) {
		return !entities.contains(id) && changes.containsKey(id);
	}

	private void assertIsSettable(K id) {
		throwIfNotInTxn();
		throwIfMissing(id);
	}

	private void assertIsCreatable(K id) {
		if (!isInTransaction) {
			throw new IllegalStateException("No active transaction");
		}
		if (existsOrIsPendingCreation(id)) {
			throw new IllegalArgumentException("An entity already exists with key '" + id + "'");
		}
	}

	private void throwIfMissing(K id) {
		if (!exists(id)) {
			throw new MissingAccountException(id);
		}
	}

	private boolean existsOrIsPendingCreation(K id) {
		return entities.contains(id) || changes.containsKey(id);
	}

	private boolean isZombie(K id) {
		return deadKeys.contains(id);
	}

	private Function<P, Object> newDefaultPropertySource() {
		final var defaultEntity = newEntity.get();
		return prop -> prop.getter().apply(defaultEntity);
	}

	private String readable(final K id) {
		return (keyToString == null) ? readableId(id) : keyToString.apply(id);
	}

	// --- Only used by unit tests ---
	@VisibleForTesting
	public TransactionalLedger<K, P, A> getEntitiesLedger() {
		return entitiesLedger;
	}

	@VisibleForTesting
	public CommitInterceptor<K, A, P> getCommitInterceptor() {
		return commitInterceptor;
	}

	@VisibleForTesting
	EntityChangeSet<K, A, P> getPendingChanges() {
		return pendingChanges;
	}

	@VisibleForTesting
	Consumer<K> getPreviewAction() {
		return previewAction;
	}

	@VisibleForTesting
	List<K> getCreatedKeys() {
		return createdKeys;
	}

	@VisibleForTesting
	List<K> getChangedKeys() {
		return changedKeys;
	}

	@VisibleForTesting
	Map<K, PropertyChanges<P>> getChanges() {
		return changes;
	}

	@VisibleForTesting
	boolean isChangesFactoryManager() {
		return changesFactoryManager;
	}
}