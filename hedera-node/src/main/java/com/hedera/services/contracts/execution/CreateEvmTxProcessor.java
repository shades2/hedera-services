package com.hedera.services.contracts.execution;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.txns.contract.helpers.StorageExpiry;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Extension of the base {@link EvmTxProcessor} that provides interface for executing
 * {@link com.hederahashgraph.api.proto.java.ContractCreateTransactionBody} transactions
 */
@Singleton
public class CreateEvmTxProcessor extends EvmTxProcessor {
	private final CodeCache codeCache;
	private final StorageExpiry storageExpiry;

	@Inject
	public CreateEvmTxProcessor(
			final HederaMutableWorldState worldState,
			final LivePricesSource livePricesSource,
			final CodeCache codeCache,
			final GlobalDynamicProperties globalDynamicProperties,
			final GasCalculator gasCalculator,
			final Set<Operation> hederaOperations,
			final Map<String, PrecompiledContract> precompiledContractMap,
			final StorageExpiry storageExpiry,
			final InHandleBlockMetaSource blockMetaSource
	) {
		super(
				worldState,
				livePricesSource,
				globalDynamicProperties,
				gasCalculator,
				hederaOperations,
				precompiledContractMap,
				blockMetaSource);
		this.codeCache = codeCache;
		this.storageExpiry = storageExpiry;
	}

	public TransactionProcessingResult execute(
			final Account sender,
			final Address receiver,
			final long providedGasLimit,
			final long value,
			final Bytes code,
			final Instant consensusTime,
			final long hapiExpiry
	) {
		final long gasPrice = gasPriceTinyBarsGiven(consensusTime, false);

		return super.execute(
				sender,
				receiver,
				gasPrice,
				providedGasLimit,
				value,
				code,
				true,
				consensusTime,
				false,
				storageExpiry.hapiCreationOracle(hapiExpiry),
				receiver,
				null,
				0,
				null);
	}

	public TransactionProcessingResult executeEth(
			final Account sender,
			final Address receiver,
			final long providedGasLimit,
			final long value,
			final Bytes code,
			final Instant consensusTime,
			final long hapiExpiry,
			final Account relayer,
			final BigInteger providedMaxGasPrice,
			final long maxGasAllowance
	) {
		final long gasPrice = gasPriceTinyBarsGiven(consensusTime, true);

		return super.execute(
				sender,
				receiver,
				gasPrice,
				providedGasLimit,
				value,
				code,
				true,
				consensusTime,
				false,
				storageExpiry.hapiCreationOracle(hapiExpiry),
				receiver,
				providedMaxGasPrice,
				maxGasAllowance,
				relayer);
	}

	@Override
	protected HederaFunctionality getFunctionType() {
		return HederaFunctionality.ContractCreate;
	}

	@Override
	protected MessageFrame buildInitialFrame(
			final MessageFrame.Builder commonInitialFrame,
			final Address to,
			final Bytes payload,
			final long value) {
		codeCache.invalidate(to);

		return commonInitialFrame
				.type(MessageFrame.Type.CONTRACT_CREATION)
				.address(to)
				.contract(to)
				.inputData(Bytes.EMPTY)
				.code(Code.createLegacyCode(payload, Hash.hash(payload)))
				.build();
	}
}
