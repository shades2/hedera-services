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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.CallEvmTxProcessor;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractWrappedCallTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.WrappedTransactionType;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_FORMAT_ERROR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_CHAIN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_DATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_GAS_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_NONCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_RECEIVER_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FOREIGN_TRANSACTION_INCORRECT_SENDER_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContractWrappedCallTransitionLogicTest {
	final private static byte[] REFERENCE_TEST_EIP_2930_COINBASE_T1_D0G0V0 =
			Hex.decode(
					"f887" + // list of items 135 bytes long
					"01" + // nonce
					"8203e8" + // gas price
					"8402625a00" + // gas limit
					"94000000000000000000000000000000000000c0de" + // to
					"80" + //value
					"a4693c61390000000000000000000000000000000000000000000000000000000000000000" + // call data
					"1c" + // v
					"a04d353bbce5f27c32e87aeb2e22b4ef07f5f6eb2bb1b84fc679fdd3ec31fad618" + // r
					"a04d4ba45d24e79886b43f7386a328918a68b89a6bcf53af2c3884fc5f27b1c4e3"); // s
	final private static byte[] REFERENCE_TEST_EIP_2930_COINBASE_T1_D1G0V0 =
			Hex.decode(
					"01" + // tx Type 1 "EIP 2930" or "Berlin"
					"f8a0" + // list of items 160 bytes long
					"01" + // chainID
					"01" + // nonce
					"8203e8" + // gas price
					"8402625a00" + // gas limit
					"94000000000000000000000000000000000000c0de" + // to
					"80" + // value
					"a4693c61390000000000000000000000000000000000000000000000000000000000000000" + // call data
					"d7d694000000000000000000000000000000000000ba5ec0" + // access list [[0xba5e, []]]
					"80" + // recID
					"a08997fab173d7bd66a2ab6d24b6611574d1399451748ebbeaa0b326eca7cc6837" + // r
					"a01c9105bd8e3aed1138ed54d00154cd61f3e70dd70dd89752f5d62d8f18daa0e6"); // s
	final private static byte[] REFERENCE_TEST_EIP_2930_COINBASE_T2_D0G0V0 =
			Hex.decode(
					"02" + // type 2 "EIP 1559" or "Fee Market" or "London"
					"f8a1" + // list of items 161 bytes long
					"01" + // chain ID
					"01" + // nonce
					"64" + // max priority fee
					"822710" + // max gas price
					"8402625a00" + // gas limit
					"94000000000000000000000000000000000000c0de" + // to
					"80" +// value
					"a4693c61390000000000000000000000000000000000000000000000000000000000000000" + // call data
					"d7d694000000000000000000000000000000000000ba5ec0" + // access list
					"80" + // recID
					"a0d5a3052a8cc387f35ffdebe832afdfdd45980cdcbb9b926dee7fb9b1d9a4ee08" + // r
					"a06210d404eb6aecd05bc214b120959a71ede9fadcae4b769c96c58bffa216f205"); // s

	final private ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();

	final private ContractID target0xC0DE = ContractID.newBuilder().setContractNum(0xc0de).build();

	private final Instant consensusTime = Instant.now();
	private final Account senderAccount = new Account(new Id(0, 0, 1002));
	private final Account contractAccount = new Account(new Id(0, 0, 1006));
	private final Bytes CALLDATA_F_UINT256_ZERO = Bytes.fromHexString(
			"0x693c61390000000000000000000000000000000000000000000000000000000000000000");
	ContractWrappedCallTransitionLogic subject;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private AccountStore accountStore;
	@Mock
	private HederaWorldState worldState;
	@Mock
	private TransactionRecordService recordService;
	@Mock
	private CallEvmTxProcessor evmTxProcessor;
	@Mock
	private GlobalDynamicProperties properties;
	@Mock
	private CodeCache codeCache;
	@Mock
	private SigImpactHistorian sigImpactHistorian;

	@BeforeEach
	private void setup() {
		subject = new ContractWrappedCallTransitionLogic(
				txnCtx, accountStore, worldState,
				recordService, evmTxProcessor, properties, codeCache, sigImpactHistorian);
	}

//	@Test
//	void hasCorrectApplicability() {
//		givenValidTxnCtx();
//
//		// expect:
//		assertTrue(subject.applicability().test(contractCallTxn));
//		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
//	}

	@NotNull
	private TransactionBody.Builder correctFrontierTransaction() {
		return TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractWrappedCall(
						ContractWrappedCallTransactionBody.newBuilder()
								.setWrappedTransactionType(WrappedTransactionType.ETHEREUM_FRONTIER)
								.setForeignTransactionBytes(
										ByteString.copyFrom(REFERENCE_TEST_EIP_2930_COINBASE_T1_D0G0V0))
								.setNonce(1)
								.setGas(40_000_000L)
								.setContractID(target0xC0DE)
								.setAmount(0)
								.setFunctionParameterStart(34)
								.setFunctionParameterLength(36)
								.setSenderID(senderAccount.getId().asGrpcAccount()));
	}

	@NotNull
	private TransactionBody.Builder correctBerlinTransaction() {
		return TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractWrappedCall(
						ContractWrappedCallTransactionBody.newBuilder()
								.setWrappedTransactionType(WrappedTransactionType.ETHEREUM_FRONTIER)
								.setForeignTransactionBytes(
										ByteString.copyFrom(REFERENCE_TEST_EIP_2930_COINBASE_T1_D1G0V0))
								.setNonce(1)
								.setGas(40_000_000L)
								.setContractID(target0xC0DE)
								.setAmount(0)
								.setFunctionParameterStart(36)
								.setFunctionParameterLength(36)
								.setSenderID(senderAccount.getId().asGrpcAccount()));
	}

	@NotNull
	private TransactionBody.Builder correctLondonTransaction() {
		return TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractWrappedCall(
						ContractWrappedCallTransactionBody.newBuilder()
								.setWrappedTransactionType(WrappedTransactionType.ETHEREUM_FRONTIER)
								.setForeignTransactionBytes(
										ByteString.copyFrom(REFERENCE_TEST_EIP_2930_COINBASE_T2_D0G0V0))
								.setNonce(1)
								.setGas(40_000_000L)
								.setContractID(target0xC0DE)
								.setAmount(0)
								.setFunctionParameterStart(37)
								.setFunctionParameterLength(36)
								.setSenderID(senderAccount.getId().asGrpcAccount()));
	}

	private TransactionBody manipulateTransaction(TransactionBody.Builder transactionBodyBuilder,
			Consumer<TransactionBody.Builder> manipulation) {
		manipulation.accept(transactionBodyBuilder);
		return transactionBodyBuilder.build();
	}

	@Test
	void verifyPrecheckWithValidData() {
		given(properties.maxGas()).willReturn(50_000_000);
		given(properties.getChainId()).willReturn(1);

		assertEquals(OK, subject.semanticCheck().apply(correctFrontierTransaction().build()));
		assertEquals(OK, subject.semanticCheck().apply(correctBerlinTransaction().build()));
		assertEquals(OK, subject.semanticCheck().apply(correctLondonTransaction().build()));
	}

	@Test
	void verifyPrecheckBadFormatError() {
		given(properties.maxGas()).willReturn(50_000_000);

		Consumer<TransactionBody.Builder> badTxBody =
				(TransactionBody.Builder builder) -> builder.getContractWrappedCallBuilder().setForeignTransactionBytes(
						ByteString.copyFromUtf8("Bad Transaction"));

		assertEquals(FOREIGN_TRANSACTION_FORMAT_ERROR,
				subject.semanticCheck().apply(manipulateTransaction(correctFrontierTransaction(), badTxBody)));
		assertEquals(FOREIGN_TRANSACTION_FORMAT_ERROR,
				subject.semanticCheck().apply(manipulateTransaction(correctBerlinTransaction(), badTxBody)));
		assertEquals(FOREIGN_TRANSACTION_FORMAT_ERROR,
				subject.semanticCheck().apply(manipulateTransaction(correctLondonTransaction(), badTxBody)));
	}

	@Test
	void verifyPrecheckBadChainId() {
		given(properties.maxGas()).willReturn(50_000_000);
		given(properties.getChainId()).willReturn(0x1337);

		// tx has "any chain" baked in
		//assertEquals(FOREIGN_TRANSACTION_INCORRECT_CHAIN_ID,
		//		subject.semanticCheck().apply(correctFrontierTransaction().build()));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_CHAIN_ID,
				subject.semanticCheck().apply(correctBerlinTransaction().build()));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_CHAIN_ID,
				subject.semanticCheck().apply(correctLondonTransaction().build()));
	}

	@Test
	void verifyPrecheckBadNonce() {
		given(properties.maxGas()).willReturn(50_000_000);
		given(properties.getChainId()).willReturn(1);

		Consumer<TransactionBody.Builder> wrongNonce =
				(TransactionBody.Builder builder) -> builder.getContractWrappedCallBuilder().setNonce(0x1337);

		assertEquals(FOREIGN_TRANSACTION_INCORRECT_NONCE,
				subject.semanticCheck().apply(manipulateTransaction(correctFrontierTransaction(), wrongNonce)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_NONCE,
				subject.semanticCheck().apply(manipulateTransaction(correctBerlinTransaction(), wrongNonce)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_NONCE,
				subject.semanticCheck().apply(manipulateTransaction(correctLondonTransaction(), wrongNonce)));
	}

	@Test
	void verifyPrecheckBadGasLimit() {
		given(properties.maxGas()).willReturn(50_000_000);
		given(properties.getChainId()).willReturn(1);

		Consumer<TransactionBody.Builder> wrongGas =
				(TransactionBody.Builder builder) -> builder.getContractWrappedCallBuilder().setGas(0x1337);

		assertEquals(FOREIGN_TRANSACTION_INCORRECT_GAS_LIMIT,
				subject.semanticCheck().apply(manipulateTransaction(correctFrontierTransaction(), wrongGas)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_GAS_LIMIT,
				subject.semanticCheck().apply(manipulateTransaction(correctBerlinTransaction(), wrongGas)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_GAS_LIMIT,
				subject.semanticCheck().apply(manipulateTransaction(correctLondonTransaction(), wrongGas)));
	}

	//TODO FEE Limit Testing

	@Test
	@Disabled("We need to do more exhaustive contract matching")
	void verifyPrecheckBadReceiver() {
		given(properties.maxGas()).willReturn(50_000_000);
		given(properties.getChainId()).willReturn(1);

		Consumer<TransactionBody.Builder> wrongReceiver =
				(TransactionBody.Builder builder) -> builder.getContractWrappedCallBuilder().setContractID(
						ContractID.newBuilder().setContractNum(0x1337));

		assertEquals(FOREIGN_TRANSACTION_INCORRECT_RECEIVER_ADDRESS,
				subject.semanticCheck().apply(manipulateTransaction(correctFrontierTransaction(), wrongReceiver)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_RECEIVER_ADDRESS,
				subject.semanticCheck().apply(manipulateTransaction(correctBerlinTransaction(), wrongReceiver)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_RECEIVER_ADDRESS,
				subject.semanticCheck().apply(manipulateTransaction(correctLondonTransaction(), wrongReceiver)));
	}

	@Test
	void verifyPrecheckBadAmount() {
		given(properties.maxGas()).willReturn(50_000_000);
		given(properties.getChainId()).willReturn(1);

		Consumer<TransactionBody.Builder> wrongAmount =
				(TransactionBody.Builder builder) -> builder.getContractWrappedCallBuilder().setAmount(0x1337);

		assertEquals(FOREIGN_TRANSACTION_INCORRECT_AMOUNT,
				subject.semanticCheck().apply(manipulateTransaction(correctFrontierTransaction(), wrongAmount)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_AMOUNT,
				subject.semanticCheck().apply(manipulateTransaction(correctBerlinTransaction(), wrongAmount)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_AMOUNT,
				subject.semanticCheck().apply(manipulateTransaction(correctLondonTransaction(), wrongAmount)));
	}

	@Test
	void verifyPrecheckBadDataOffset() {
		given(properties.maxGas()).willReturn(50_000_000);
		given(properties.getChainId()).willReturn(1);

		Consumer<TransactionBody.Builder> wrongDataStart =
				(TransactionBody.Builder builder) -> builder.getContractWrappedCallBuilder().setFunctionParameterStart(
						0x1337);

		assertEquals(FOREIGN_TRANSACTION_INCORRECT_DATA,
				subject.semanticCheck().apply(manipulateTransaction(correctFrontierTransaction(), wrongDataStart)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_DATA,
				subject.semanticCheck().apply(manipulateTransaction(correctBerlinTransaction(), wrongDataStart)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_DATA,
				subject.semanticCheck().apply(manipulateTransaction(correctLondonTransaction(), wrongDataStart)));
	}

	@Test
	void verifyPrecheckBadDataLength() {
		given(properties.maxGas()).willReturn(50_000_000);
		given(properties.getChainId()).willReturn(1);

		Consumer<TransactionBody.Builder> wrongDataLength =
				(TransactionBody.Builder builder) -> builder.getContractWrappedCallBuilder().setFunctionParameterLength(
						0x1337);

		assertEquals(FOREIGN_TRANSACTION_INCORRECT_DATA,
				subject.semanticCheck().apply(manipulateTransaction(correctFrontierTransaction(), wrongDataLength)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_DATA,
				subject.semanticCheck().apply(manipulateTransaction(correctBerlinTransaction(), wrongDataLength)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_DATA,
				subject.semanticCheck().apply(manipulateTransaction(correctLondonTransaction(), wrongDataLength)));
	}

	@Test
	@Disabled("We need to do more exhaustive contract matching")
	void verifyPrecheckBadSender() {
		given(properties.maxGas()).willReturn(50_000_000);
		given(properties.getChainId()).willReturn(1);

		Consumer<TransactionBody.Builder> wrongSender =
				(TransactionBody.Builder builder) -> builder.getContractWrappedCallBuilder().setSenderID(
						AccountID.newBuilder().setAccountNum(0x1337));

		assertEquals(FOREIGN_TRANSACTION_INCORRECT_SENDER_ADDRESS,
				subject.semanticCheck().apply(manipulateTransaction(correctFrontierTransaction(), wrongSender)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_SENDER_ADDRESS,
				subject.semanticCheck().apply(manipulateTransaction(correctBerlinTransaction(), wrongSender)));
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_SENDER_ADDRESS,
				subject.semanticCheck().apply(manipulateTransaction(correctLondonTransaction(), wrongSender)));
	}

	@Test
	void verifyExternaliseContractResultCall() {
		// setup:
		given(accessor.getTxn()).willReturn(correctFrontierTransaction().build(), correctBerlinTransaction().build(),
				correctLondonTransaction().build());
		given(txnCtx.accessor()).willReturn(accessor);
		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(accountStore.loadContract(new Id(target0xC0DE.getShardNum(), target0xC0DE.getRealmNum(),
				target0xC0DE.getContractNum())))
				.willReturn(contractAccount);
		// and:
		var results = TransactionProcessingResult.successful(
				null, 40_000_000L, 0L, 124L, Bytes.EMPTY, contractAccount.getId().asEvmAddress());
		given(evmTxProcessor.execute(
				senderAccount, contractAccount.getId().asEvmAddress(), 40_000_000L, 0,
				CALLDATA_F_UINT256_ZERO, txnCtx.consensusTime())
		).willReturn(results).willReturn(results);
		given(worldState.persistProvisionalContractCreations()).willReturn(List.of());
		// when:
		subject.doStateTransition();

		// then:
		verify(recordService).externaliseEvmCallTransaction(any());
		verify(worldState).persistProvisionalContractCreations();
	}

	@Test
	void verifyProcessorCallingWithCorrectCallData() {

		// setup:
		var op = correctLondonTransaction();
		TransactionBody contractCallTxn = op.build();
		// and:
		given(accessor.getTxn()).willReturn(contractCallTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(accountStore.loadContract(any())).willReturn(contractAccount);
		// and:
		var results = TransactionProcessingResult.successful(
				null, 1234L, 0L, 124L, Bytes.EMPTY, contractAccount.getId().asEvmAddress());
		given(evmTxProcessor.execute(senderAccount, contractAccount.getId().asEvmAddress(), 40_000_000L, 0L,
				CALLDATA_F_UINT256_ZERO, txnCtx.consensusTime()))
				.willReturn(results);
		given(worldState.persistProvisionalContractCreations()).willReturn(List.of(target));
		// when:
		subject.doStateTransition();

		// then:
		verify(evmTxProcessor).execute(senderAccount, contractAccount.getId().asEvmAddress(), 40_000_000, 0,
				CALLDATA_F_UINT256_ZERO, txnCtx.consensusTime());
		verify(sigImpactHistorian).markEntityChanged(target.getContractNum());
	}

	@Test
	void successfulPreFetch() {
		TransactionBody txnBody = Mockito.mock(TransactionBody.class);
		ContractWrappedCallTransactionBody ccTxnBody = Mockito.mock(ContractWrappedCallTransactionBody.class);

		given(accessor.getTxn()).willReturn(txnBody);
		given(txnBody.getContractWrappedCall()).willReturn(ccTxnBody);
		given(ccTxnBody.getContractID()).willReturn(ContractID.getDefaultInstance());

		// when:
		subject.preFetch(accessor);

		// expect:
		verify(codeCache).getIfPresent(any(Address.class));
	}

	@Test
	void codeCacheThrowsExceptionDuringGet() {
		TransactionBody txnBody = Mockito.mock(TransactionBody.class);
		ContractWrappedCallTransactionBody ccTxnBody = Mockito.mock(ContractWrappedCallTransactionBody.class);

		given(accessor.getTxn()).willReturn(txnBody);
		given(txnBody.getContractWrappedCall()).willReturn(ccTxnBody);
		given(ccTxnBody.getContractID()).willReturn(ContractID.getDefaultInstance());
		given(codeCache.getIfPresent(any(Address.class))).willThrow(new RuntimeException("oh no"));

		// when:
		subject.preFetch(accessor);

		// expect:
		verify(codeCache).getIfPresent(any(Address.class));
	}

	@Test
	void providingGasOverLimitReturnsCorrectPrecheck() {
		given(properties.maxGas()).willReturn(4_000_000);

		assertEquals(MAX_GAS_LIMIT_EXCEEDED,
				subject.semanticCheck().apply(correctFrontierTransaction().build()));
		assertEquals(MAX_GAS_LIMIT_EXCEEDED,
				subject.semanticCheck().apply(correctBerlinTransaction().build()));
		assertEquals(MAX_GAS_LIMIT_EXCEEDED,
				subject.semanticCheck().apply(correctLondonTransaction().build()));
	}

//	@Test
//	void rejectsNegativeSend() {
//		// setup:
//		sent = -1;
//
//		givenValidTxnCtx();
//		// expect:
//		assertEquals(CONTRACT_NEGATIVE_VALUE, subject.semanticCheck().apply(contractCallTxn));
//	}

	@Test
	void rejectsNegativeGas() {

		Consumer<TransactionBody.Builder> negativeAmount =
				(TransactionBody.Builder builder) -> builder.getContractWrappedCallBuilder().setAmount(-1);

		assertEquals(CONTRACT_NEGATIVE_VALUE,
				subject.semanticCheck().apply(manipulateTransaction(correctFrontierTransaction(), negativeAmount)));
		assertEquals(CONTRACT_NEGATIVE_VALUE,
				subject.semanticCheck().apply(manipulateTransaction(correctBerlinTransaction(), negativeAmount)));
		assertEquals(CONTRACT_NEGATIVE_VALUE,
				subject.semanticCheck().apply(manipulateTransaction(correctLondonTransaction(), negativeAmount)));
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(senderAccount.getId().asGrpcAccount())
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
