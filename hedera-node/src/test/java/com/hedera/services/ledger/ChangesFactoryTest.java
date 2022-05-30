package com.hedera.services.ledger;

import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.PropertyChanges;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChangesFactoryTest {
	private ChangesFactory<AccountProperty> subject = new ChangesFactory<>(AccountProperty.class);

	@Test
	void allocatesNewChangesIfNeeded() {
		assertInstanceOf(PropertyChanges.class, subject.get());
		assertEquals(1, subject.getChanges().size());
		assertEquals(1, subject.getNextChanges());
	}

	@Test
	void reusesChangesWhenAppropriate() {
		final var changes = subject.get();
		changes.set(AccountProperty.MEMO, "Hi");
		subject.reset();

		final var otherChanges = subject.get();
		assertSame(changes, otherChanges);
		assertEquals(List.of(), otherChanges.changed());
	}
}