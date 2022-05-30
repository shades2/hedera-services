package com.hedera.services.ledger.properties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class PropertyChanges<P extends Enum<P> & BeanProperty<?>> {
	private final long[] longValues;
	private final Object[] values;
	private final List<P> changedProps;
	private final boolean[] present;

	public PropertyChanges(final Class<P> type) {
		final var n = type.getEnumConstants().length;
		values = new Object[n];
		present = new boolean[n];
		longValues = new long[n];
		changedProps = new ArrayList<>(n);
	}

	public boolean includes(final P property) {
		return present[property.ordinal()];
	}

	public void undo(final P property) {
		final var i = property.ordinal();
		if (present[i]) {
			present[i] = false;
			changedProps.remove(property);
		}
	}

	public void set(final P property, final Object value) {
		final var i = property.ordinal();
		if (property.isPrimitiveLong()) {
			longValues[i] = (long) value;
		} else {
			values[i] = value;
		}
		markChanged(property);
	}

	public Object get(final P property) {
		assertGettable(property);
		final var i = property.ordinal();
		if (property.isPrimitiveLong()) {
			return longValues[i];
		} else {
			return values[i];
		}
	}

	public void setLong(final P property, final long value) {
		if (!property.isPrimitiveLong()) {
			throw new IllegalArgumentException(property + " is not a long");
		}
		longValues[property.ordinal()] = value;
		markChanged(property);
	}

	public long getLong(final P property) {
		assertGettable(property);
		if (!property.isPrimitiveLong()) {
			throw new IllegalArgumentException(property + " is not a long");
		}
		return longValues[property.ordinal()];
	}

	public Set<P> changedSet() {
		if (changedProps.isEmpty()) {
			return Collections.emptySet();
		} else {
			return EnumSet.copyOf(changedProps);
		}
	}

	public List<P> changed() {
		return changedProps;
	}

	public void clear() {
		Arrays.fill(present, false);
		changedProps.clear();
	}

	private void assertGettable(final P property) {
		if (!present[property.ordinal()]) {
			throw new IllegalArgumentException("Changes don't include " + property);
		}
	}

	private void markChanged(final P property) {
		final var i = property.ordinal();
		if (!present[i]) {
			present[i] = true;
			changedProps.add(property);
		}
	}
}
