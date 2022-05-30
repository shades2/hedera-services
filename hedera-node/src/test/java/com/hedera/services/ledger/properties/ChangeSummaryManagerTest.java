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
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.TestAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.ledger.properties.TestAccountProperty.FLAG;
import static com.hedera.services.ledger.properties.TestAccountProperty.LONG;
import static com.hedera.services.ledger.properties.TestAccountProperty.OBJ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ChangeSummaryManagerTest {
	private static final ChangeSummaryManager<TestAccount, TestAccountProperty> subject = new ChangeSummaryManager<>();
	private static final PropertyChanges<TestAccountProperty> changes = new PropertyChanges<>(TestAccountProperty.class);

	@Mock
	private PropertyChangeObserver<Long, TestAccountProperty> observer;
	@Mock
	private TransactionalLedger<Long, TestAccountProperty, TestAccount> ledger;

	@BeforeEach
	private void setup() {
		changes.clear();
	}

	@Test
	void setsAllChangesAsExpected() {
		final var thing = new Object();
		changes.setLong(LONG, 5L);
		changes.set(OBJ, thing);

		subject.setAll(changes, ledger, 1L);

		verify(ledger).set(1L, OBJ, thing);
		verify(ledger).setLong(1L, LONG, 5L);
	}

	@Test
	void persistsExpectedChangesWithObserver() {
		final var id = 666L;
		final var thing = new Object();
		final var testAccount = new TestAccount(1L, thing, false);

		changes.setLong(LONG, 5L);
		changes.set(FLAG, true);
		subject.persistWithObserver(id, changes, testAccount, observer);

		assertEquals(new TestAccount(5L, thing, true), testAccount);
		verify(observer).newLongProperty(id, LONG, 5L);
		verify(observer).newProperty(id, FLAG, true);
		verifyNoMoreInteractions(observer);
	}

	@Test
	void persistsExpectedChanges() {
		final var thing = new Object();
		final var testAccount = new TestAccount(1L, thing, false);

		changes.setLong(LONG, 5L);
		changes.set(FLAG, true);
		subject.persist(changes, testAccount);

		assertEquals(new TestAccount(5L, thing, true), testAccount);
	}

	@Test
	void setsFlagWithPrimitiveArg() {
		changes.set(FLAG, true);

		assertEquals(Boolean.TRUE, changes.get(FLAG));
	}

	@Test
	void setsValueWithPrimitiveArg() {
		changes.setLong(LONG, 5L);

		assertEquals(5L, changes.get(LONG));
	}

	@Test
	void setsThing() {
		final var thing = new Object();
		changes.set(OBJ, thing);

		assertEquals(thing, changes.get(OBJ));
	}
}
