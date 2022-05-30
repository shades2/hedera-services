package com.hedera.services.ledger.properties;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyChangesTest {
	private PropertyChanges<AccountProperty> subject = new PropertyChanges<>(AccountProperty.class);

	@Test
	void canGetAndSetObjectPropsIfValid() {
		final var memo = "Goodbye, and keep cold";
		subject.set(MEMO, memo);
		assertEquals(memo, subject.get(MEMO));
		assertEquals(List.of(MEMO), subject.changed());
		subject.clear();
		assertEquals(List.of(), subject.changed());
		assertThrows(IllegalArgumentException.class, () -> subject.get(MEMO));
	}

	@Test
	void canGetAndSetLongPropsBothBoxedAndUnboxed() {
		final var balance = 123_456L;
		subject.set(BALANCE, balance);
		assertEquals(balance, subject.get(BALANCE));
		subject.clear();
		subject.setLong(BALANCE, balance);
		assertEquals(balance, subject.getLong(BALANCE));
	}

	@Test
	void cannotGetOrSetNonLongPropsAsSuch() {
		final var memo = "Goodbye, and keep cold";
		assertThrows(IllegalArgumentException.class, () -> subject.setLong(MEMO, 123L));
		subject.set(MEMO, memo);
		assertThrows(IllegalArgumentException.class, () -> subject.getLong(MEMO));
	}

	@Test
	void includesIfSetAndNotUndone() {
		final var balance = 123_456L;
		subject.setLong(BALANCE, balance);
		assertTrue(subject.includes(BALANCE));
		subject.undo(BALANCE);
		subject.undo(BALANCE);
		subject.undo(BALANCE);
		assertFalse(subject.includes(BALANCE));
		assertTrue(subject.changed().isEmpty());
	}

	@Test
	void canGetEmptyChangeSet() {
		assertTrue(subject.changedSet().isEmpty());
		subject.setLong(BALANCE, 123L);
		assertEquals(EnumSet.of(BALANCE), subject.changedSet());
	}
}