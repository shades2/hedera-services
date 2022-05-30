package com.hedera.services.ledger.properties;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}