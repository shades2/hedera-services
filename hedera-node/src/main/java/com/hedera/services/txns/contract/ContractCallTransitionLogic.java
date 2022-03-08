package com.hedera.services.txns.contract;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.CallEvmTxProcessor;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.PreFetchableTransition;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

import javax.inject.Inject;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_FORMAT_ERROR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_CHAIN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_DATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_GAS_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_NONCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_RECEIVER_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ContractCallTransitionLogic implements PreFetchableTransition {
	private static final Logger log = LogManager.getLogger(ContractCallTransitionLogic.class);

	private final AccountStore accountStore;
	private final TransactionContext txnCtx;
	private final HederaMutableWorldState worldState;
	private final TransactionRecordService recordService;
	private final CallEvmTxProcessor evmTxProcessor;
	private final GlobalDynamicProperties properties;
	private final CodeCache codeCache;
	private final AliasManager aliasManager;
	private final SigImpactHistorian sigImpactHistorian;

	@Inject
	public ContractCallTransitionLogic(
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final HederaWorldState worldState,
			final TransactionRecordService recordService,
			final CallEvmTxProcessor evmTxProcessor,
			final GlobalDynamicProperties properties,
			final CodeCache codeCache,
			final SigImpactHistorian sigImpactHistorian,
			final AliasManager aliasManager
	) {
		this.txnCtx = txnCtx;
		this.aliasManager = aliasManager;
		this.worldState = worldState;
		this.accountStore = accountStore;
		this.recordService = recordService;
		this.evmTxProcessor = evmTxProcessor;
		this.properties = properties;
		this.codeCache = codeCache;
		this.sigImpactHistorian = sigImpactHistorian;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		var contractCallTxn = txnCtx.accessor().getTxn();
		var op = contractCallTxn.getContractCall();
		final var target = targetOf(op);
		var senderAccountId = op.hasSenderID()
				? op.getSenderID()
				: contractCallTxn.getTransactionID().getAccountID();
		final Id senderId;
		if (senderAccountId.getAccountCase() == AccountID.AccountCase.ALIAS) {
			ByteString alias = senderAccountId.getAlias();
			EntityNum entityNum = aliasManager.lookupIdBy(alias);
			senderId = Id.fromGrpcAccount(entityNum.toGrpcAccountId());
		} else {
			senderId = Id.fromGrpcAccount(senderAccountId);
		}
		final var contractId = target.toId();

		/* --- Load the model objects --- */
		final var sender = accountStore.loadAccount(senderId);
		final var receiver = accountStore.loadContract(contractId);
		final Bytes callData;
		if (contractCallTxn.hasForeignTransactionData()) {
			var foreignTxData = contractCallTxn.getForeignTransactionData();
			var foreignTxBytes = foreignTxData.getForeignTransactionBytes().toByteArray();
			callData = Bytes.wrap(foreignTxBytes, foreignTxData.getPayloadStart(), foreignTxData.getPayloadLength());
		} else if (!op.getFunctionParameters().isEmpty()) {
			callData = Bytes.fromHexString(CommonUtils.hex(op.getFunctionParameters().toByteArray()));
		} else {
			callData = Bytes.EMPTY;
		}

		/* --- Do the business logic --- */
		final var result = evmTxProcessor.execute(
				sender,
				receiver.canonicalAddress(),
				op.getGas(),
				op.getAmount(),
				callData,
				txnCtx.consensusTime());

		/* --- Persist changes into state --- */
		final var createdContracts = worldState.persistProvisionalContractCreations();
		worldState.customizeSponsoredAccounts();
		result.setCreatedContracts(createdContracts);

		/* --- Externalise result --- */
		txnCtx.setTargetedContract(target.toGrpcContractID());
		for (final var createdContract : createdContracts) {
			sigImpactHistorian.markEntityChanged(createdContract.getContractNum());
		}
		recordService.externaliseEvmCallTransaction(result);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasContractCall;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validateSemantics;
	}

	private ResponseCodeEnum validateSemantics(final TransactionBody transactionBody) {
		var op = transactionBody.getContractCall();

		if (op.getGas() < 0) {
			return CONTRACT_NEGATIVE_GAS;
		}
		if (op.getAmount() < 0) {
			return CONTRACT_NEGATIVE_VALUE;
		}
		if (op.getGas() > properties.maxGas()) {
			return MAX_GAS_LIMIT_EXCEEDED;
		}


		if (transactionBody.hasForeignTransactionData()) {
			return switch (transactionBody.getForeignTransactionData().getForeignTransactionType()) {
				case ETHEREUM -> validateFrontierTxSemantics(transactionBody);
				// Why not 2930 transactions? Because I don't think any wallet "in the wild" makes EIP-2930 transactions
				// case ETHEREUM_EIP_2930 -> validateEIP2930TxSemantics(transactionBody);
				case ETHEREUM_EIP_1559 -> validateEIP1559TxSemantics(transactionBody);
				default -> FOREIGN_TRANSACTION_FORMAT_ERROR;
			};
		} else {
			return OK;
		}
	}

	private ResponseCodeEnum validateFrontierTxSemantics(final TransactionBody transactionBody) {
		var op = transactionBody.getContractCall();
		var foreignTxData = transactionBody.getForeignTransactionData();
		var foreignTxBytes = foreignTxData.getForeignTransactionBytes().toByteArray();
		try {
			var rlpList = RLPDecoder.RLP_STRICT.wrapItem(foreignTxBytes).asRLPList().elements();
			if (rlpList.size() != 9) {
				return FOREIGN_TRANSACTION_FORMAT_ERROR;
			}
			if (rlpList.get(0).asInt() != foreignTxData.getNonce()) {
				return FOREIGN_TRANSACTION_INCORRECT_NONCE;
			}
			if (rlpList.get(2).asInt() != op.getGas()) {
				return FOREIGN_TRANSACTION_INCORRECT_GAS_LIMIT;
			}

			if (Arrays.compare(rlpList.get(3).data(), addressOf(op.getContractID())) != 0) {
				return FOREIGN_TRANSACTION_INCORRECT_RECEIVER_ADDRESS;
			}
			var foreignTxAmount = rlpList.get(4).asBigInt();
			long hbarAmount = foreignTxAmount.divide(BigInteger.valueOf(10_000_000_000L)).longValueExact();
			if (hbarAmount != op.getAmount()) {
				return FOREIGN_TRANSACTION_INCORRECT_AMOUNT;
			}
			// because of the type byte the offeset is off by one
			var callData = rlpList.get(5).data();
			byte[] functionParams = new byte[foreignTxData.getPayloadLength()];
			System.arraycopy(foreignTxBytes, foreignTxData.getPayloadStart(), functionParams, 0,
					foreignTxData.getPayloadLength());
			if (!Arrays.equals(callData, functionParams)) {
				return FOREIGN_TRANSACTION_INCORRECT_DATA;
			}

			int v = rlpList.get(6).asInt();
			int chainId = ((v - 1) >> 1) - 17;
			if (chainId != properties.getChainId() && chainId != -4) {
				return FOREIGN_TRANSACTION_INCORRECT_CHAIN_ID;
			}

			//TODO check transaction fee limit based on gasPrice*gasLimit
			// return ResponseCodeEnum.FOREIGN_TRANSACTION_INSUFFICIENT_FEE_LIMIT;
			//TODO check sender
			// return ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_SENDER_ADDRESS;
			//TODO check signature
			// long recId = rlpList.get(9).asLong();
			// var r = rlpList.get(10).asBigInt();
			// var s = rlpList.get(11).asBigInt();
			// return ResponseCodeEnum.FOREIGN_TRANSACTION_INVALID_SIGNATURE;
			return OK;
		} catch (RuntimeException e) {
			return FOREIGN_TRANSACTION_FORMAT_ERROR;
		}
	}

	private ResponseCodeEnum validateEIP1559TxSemantics(final TransactionBody transactionBody) {
		var op = transactionBody.getContractCall();
		var foreignTxData = transactionBody.getForeignTransactionData();
		var foreignTxBytes = foreignTxData.getForeignTransactionBytes().toByteArray();
		try {
			var txSequence = RLPDecoder.RLP_STRICT.sequenceIterator(foreignTxBytes);
			var header = txSequence.next();
			if (header.asByte() != 0x02) {
				return FOREIGN_TRANSACTION_FORMAT_ERROR;
			}
			var rlpList = txSequence.next().asRLPList().elements();
			if (rlpList.size() != 12) {
				return FOREIGN_TRANSACTION_FORMAT_ERROR;
			}
			if (rlpList.get(0).asInt() != properties.getChainId()) {
				return FOREIGN_TRANSACTION_INCORRECT_CHAIN_ID;
			}

			if (rlpList.get(1).asInt() != foreignTxData.getNonce()) {
				return FOREIGN_TRANSACTION_INCORRECT_NONCE;
			}
			if (rlpList.get(4).asInt() != op.getGas()) {
				return FOREIGN_TRANSACTION_INCORRECT_GAS_LIMIT;
			}

			if (Arrays.compare(rlpList.get(5).data(), addressOf(op.getContractID())) != 0) {
				return FOREIGN_TRANSACTION_INCORRECT_RECEIVER_ADDRESS;
			}
			var amount = rlpList.get(6).asBigInt().divide(BigInteger.valueOf(10_000_000_000L)).longValueExact();
			if (amount != op.getAmount()) {
				return FOREIGN_TRANSACTION_INCORRECT_AMOUNT;
			}

			var rlpPayload = rlpList.get(7).data();
			// because of the type byte the offeset is off by one
			int index = com.google.common.primitives.Bytes.indexOf(foreignTxBytes, rlpPayload);
			if (index != foreignTxData.getPayloadStart() || rlpPayload.length != foreignTxData.getPayloadLength()) {
				return FOREIGN_TRANSACTION_INCORRECT_DATA;
			}
			//TODO check transaction fee limit based on gasPrice*gasLimit
			// return ResponseCodeEnum.FOREIGN_TRANSACTION_INSUFFICIENT_FEE_LIMIT;
			//TODO check sender
			// return ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_SENDER_ADDRESS;
			//TODO check signature
			// long recId = rlpList.get(9).asLong();
			// var r = rlpList.get(10).asBigInt();
			// var s = rlpList.get(11).asBigInt();
			// return ResponseCodeEnum.FOREIGN_TRANSACTION_INVALID_SIGNATURE;
			return OK;
		} catch (RuntimeException e) {
			return FOREIGN_TRANSACTION_FORMAT_ERROR;
		}
	}

	@Override
	public void preFetch(final TxnAccessor accessor) {
		final var op = accessor.getTxn().getContractCall();
		final var id = targetOf(op);
		final var address = id.toEvmAddress();

		try {
			codeCache.getIfPresent(address);
		} catch (Exception e) {
			log.warn("Exception while attempting to pre-fetch code for {}", address, e);
		}
	}

	private EntityNum targetOf(final ContractCallTransactionBody op) {
		final var idOrAlias = op.getContractID();
		return EntityIdUtils.unaliased(idOrAlias, aliasManager);
	}
	
	private byte[] addressOf(ContractID contractID) {
		if (contractID.getContractNum() == 0) {
			return contractID.getEvmAddress().toByteArray();
		} else {
			return EntityIdUtils.asEvmAddress(contractID);
		}
	}
}

