package com.hedera.services.ledger.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

class BeanPropertyTest {
	@Test
	void primitiveSpecializationsUnsupportedByDefault() {
		final var subject = mock(BeanProperty.class);

		doCallRealMethod().when(subject).longGetter();
		doCallRealMethod().when(subject).longSetter();
		doCallRealMethod().when(subject).isPrimitiveLong();

		assertThrows(UnsupportedOperationException.class, subject::longGetter);
		assertThrows(UnsupportedOperationException.class, subject::longSetter);
		assertFalse(subject.isPrimitiveLong());
	}
}