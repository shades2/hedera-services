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
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractKeySerializer;
import com.hedera.services.state.virtual.ContractKeySupplier;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.IterableContractValueSupplier;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.tree.MerkleBinaryTree;
import com.swirlds.merkle.tree.MerkleTreeInternalNode;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import com.swirlds.virtualmap.internal.merkle.VirtualInternalNode;
import com.swirlds.virtualmap.internal.merkle.VirtualLeafNode;
import com.swirlds.virtualmap.internal.merkle.VirtualMapState;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;

public class Constructables {
	private Constructables() {
		throw new UnsupportedOperationException();
	}

	public static void registerForAccounts() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccountState.class, MerkleAccountState::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccountTokens.class, MerkleAccountTokens::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCQueue .class, FCQueue::new));
	}

	public static void registerForContractStorage() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(ContractKey.class, ContractKey::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(ContractKeySerializer.class, ContractKeySerializer::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(ContractKeySupplier.class, ContractKeySupplier::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(IterableContractValue.class, IterableContractValue::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(IterableContractValueSupplier.class, IterableContractValueSupplier::new));
	}

	public static void registerForMerkleMap() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleMap.class, MerkleMap::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleBinaryTree.class, MerkleBinaryTree::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleLong.class, MerkleLong::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleTreeInternalNode.class, MerkleTreeInternalNode::new));
	}

	public static void registerForVirtualMap() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(Hash.class, Hash::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualMap.class, VirtualMap::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualMapState.class, VirtualMapState::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualInternalNode.class, VirtualInternalNode::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualLeafNode.class, VirtualLeafNode::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualRootNode.class, VirtualRootNode::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualNodeCache.class, VirtualNodeCache::new));
	}

	public static void registerForJasperDb() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(JasperDbBuilder.class, JasperDbBuilder::new));
	}
}
