package com.hedera.services.state.virtual;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.virtualmap.VirtualMap;

import java.io.File;
import java.io.IOException;

public class VmResurrection {
	private static final String KNOWN_LOC = "hedera-node/data/saved/com.hedera.services.ServicesMain/" +
			"0/123/4435/smartContractKvStore.vmap";
	private static final short CURRENT_SERIALIZATION_VERSION = 1;
	private static final long MAX_STORAGE_ENTRIES = 500_000_000;
	private static final long MAX_IN_MEMORY_INTERNAL_HASHES = 0;

	public static void main(String... args) throws IOException, ConstructableRegistryException {
		ConstructableRegistry.registerConstructables("com.swirlds");
		ConstructableRegistry.registerConstructable(new ClassConstructorPair(ContractKeySupplier.class, ContractKeySupplier::new));
		ConstructableRegistry.registerConstructable(new ClassConstructorPair(ContractValueSupplier.class, ContractValueSupplier::new));
		ConstructableRegistry.registerConstructable(new ClassConstructorPair(ContractKeySerializer.class, ContractKeySerializer::new));

		final VirtualMap<ContractKey, ContractValue> vm = new VirtualMap<>();

		vm.loadFromFile(new File(KNOWN_LOC));

		System.out.println("Resurrected " + vm.size() + " nodes");
	}
}
