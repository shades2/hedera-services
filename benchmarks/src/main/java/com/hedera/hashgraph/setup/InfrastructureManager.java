package com.hedera.hashgraph.setup;

/*-
 * ‌
 * Hedera Services JMH benchmarks
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.state.virtual.VirtualMapFactory.JasperDbBuilderFactory;
import com.hedera.services.utils.EntityNum;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

import java.io.File;
import java.nio.file.Paths;

public class InfrastructureManager {
	private static final String SAVED_STORAGE_DIR = "databases";
	private static final String WORKING_STORAGE_DIR = "database";

	public static StorageInfrastructure newInfrastructure() {
		final var jdbBuilderFactory = new JasperDbBuilderFactory() {
			@Override
			@SuppressWarnings({"rawtypes", "unchecked"})
			public <K extends VirtualKey<K>, V extends VirtualValue> JasperDbBuilder<K, V> newJdbBuilder() {
				return new JasperDbBuilder().storageDir(Paths.get(WORKING_STORAGE_DIR));
			}
		};
		final var vmFactory = new VirtualMapFactory(jdbBuilderFactory);
		final var storage = vmFactory.newVirtualizedStorage();
		final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
		return StorageInfrastructure.from(accounts, storage);
	}

	public static StorageInfrastructure infrastructureWith(final int initNumContracts, final int initNumKvPairs) {
		throw new AssertionError("Not implemented");
	}

	public static boolean hasSavedStorageWith(final int initNumContracts, final int initNumKvPairs) {
		final var f = new File(vmLocFor(initNumContracts, initNumKvPairs));
		return f.exists();
	}

	private static String vmLocFor(final int initNumContracts, final int initNumKvPairs) {
		return storageLocFor(initNumContracts, initNumKvPairs) + File.separator + "smartContractKvStore.vmap";
	}

	private static String storageLocFor(final int initNumContracts, final int initNumKvPairs) {
		return SAVED_STORAGE_DIR + File.separator
				+ "contracts" + initNumContracts + "_"
				+ "kvPairs" + initNumKvPairs;
	}
}
