package com.hedera.services.setup;

import com.swirlds.common.FastCopyable;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class InfrastructureBundle {
	private final Set<InfrastructureType> types;
	private final Map<InfrastructureType, AtomicReference<?>> refs = new EnumMap<>(InfrastructureType.class);

	public InfrastructureBundle(final Collection<InfrastructureType> types) {
		this.types = includingDeps(types);
	}

	public void abInitio(final String dir) {
		inTopologicalOrder("Creating", type -> {
			final var instance = type.abInitio(dir, this);
			refs.put(type, new AtomicReference<>(instance));
		});
	}

	public void loadFrom(final String dir) {
		inTopologicalOrder("Loading", type -> {
			final var instance = type.fromStorage(dir, this);
			refs.put(type, new AtomicReference<>(instance));
		});
	}

	public void toStorage(final String dir) {
		refs.forEach((type, ref) -> {
			System.out.println("  -> Serializing " + type);
			type.toStorage(ref.get(), dir, this);
		});
	}

	public void copyInPlace() {
		refs.forEach((type, ref) -> {
			if (type.isFastCopyable()) {
				ref.set(((FastCopyable) ref.get()).copy());
			}
		});
	}

	public static long codeFor(final Collection<InfrastructureType> types) {
		int offset = 0;
		long ans = 0L;
		for (final var type : InfrastructureType.values()) {
			if (types.contains(type)) {
				ans |= 1L << offset++;
			}
		}
		return ans;
	}

	@SuppressWarnings("unchecked")
	public <T> Supplier<T> getterFor(final InfrastructureType type) {
		if (!types.contains(type)) {
			throw new IllegalArgumentException("This bundle doesn't include a " + type);
		}
		return () -> (T) refs.get(type).get();
	}

	@SuppressWarnings("unchecked")
	public <T> T get(final InfrastructureType type) {
		if (!types.contains(type)) {
			throw new IllegalArgumentException("This bundle doesn't include a " + type);
		}
		return (T) refs.get(type).get();
	}

	@SuppressWarnings("unchecked")
	public <T> void set(final InfrastructureType type, final T instance) {
		if (!types.contains(type)) {
			throw new IllegalArgumentException("This bundle doesn't include a " + type);
		}
		((AtomicReference<T>) refs.get(type)).set(instance);
	}

	public static Collection<InfrastructureType> allImplied(final Collection<InfrastructureType> types) {
		return includingDeps(types);
	}

	private static Set<InfrastructureType> includingDeps(final Collection<InfrastructureType> types) {
		final Set<InfrastructureType> included = EnumSet.noneOf(InfrastructureType.class);
		types.forEach(type -> ensureDeps(included, type));
		return included;
	}

	private static void ensureDeps(final Set<InfrastructureType> included, final InfrastructureType type) {
		if (included.contains(type)) {
			return;
		}
		included.add(type);
		type.dependencies().forEach(transitive -> ensureDeps(included, transitive));
	}

	private void inTopologicalOrder(String desc, final Consumer<InfrastructureType> action) {
		final var instantiated = EnumSet.noneOf(InfrastructureType.class);
		while (!instantiated.equals(types)) {
			final var added = EnumSet.noneOf(InfrastructureType.class);
			types.stream()
					.filter(type -> !instantiated.contains(type))
					.filter(type -> instantiated.containsAll(type.dependencies()))
					.forEach(type -> {
						System.out.println("  -> " + desc + " " + type);
						action.accept(type);
						added.add(type);
					});
			instantiated.addAll(added);
		}
	}
}
