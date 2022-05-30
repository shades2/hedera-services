package com.hedera.services.ledger;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.ledger.properties.BeanProperty;
import com.hedera.services.ledger.properties.PropertyChanges;

import java.util.ArrayList;
import java.util.List;

public class ChangesFactory<P extends Enum<P> & BeanProperty<?>> {
	private static final int DEFAULT_CAPACITY = 128;

	private int nextChanges = 0;
	private List<PropertyChanges<P>> changes = new ArrayList<>(DEFAULT_CAPACITY);

	private final Class<P> type;

	public ChangesFactory(Class<P> type) {
		this.type = type;
	}

	public PropertyChanges<P> get() {
		final PropertyChanges<P> toUse;
		if (nextChanges == changes.size()) {
			toUse = new PropertyChanges<>(type);
			changes.add(toUse);
			nextChanges++;
		} else {
			toUse = changes.get(nextChanges++);
			toUse.clear();
		}
		return toUse;

	}

	public void reset() {
		nextChanges = 0;
	}

	@VisibleForTesting
	List<PropertyChanges<P>> getChanges() {
		return changes;
	}

	@VisibleForTesting
	int getNextChanges() {
		return nextChanges;
	}
}
