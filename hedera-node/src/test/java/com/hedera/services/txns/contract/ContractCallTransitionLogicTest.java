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
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ForeignTransactionData;
import com.hederahashgraph.api.proto.java.ForeignTransactionType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.CommonUtils;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContractCallTransitionLogicTest {

	private String foreignTxHex;
	private long nonce;
	private int foreignTxPayloadLength;
	private int foreignTxPayloadStart;
	private ForeignTransactionType foreignTxType = ForeignTransactionType.UNRECOGNIZED;
	private ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();
	private int gas = 1_234;
	private long sent = 1_234L;
	private AccountID senderId;
	private ByteString functionParameters;

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
	@Mock
	private AliasManager aliasManager;

	private TransactionBody contractCallTxn;
	private final Instant consensusTime = Instant.now();
	private final Account senderAccount = new Account(new Id(0, 0, 1002));
	private final Account contractAccount = new Account(new Id(0, 0, 1006));
	ContractCallTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new ContractCallTransitionLogic(
				txnCtx, accountStore, worldState, recordService,
				evmTxProcessor, properties, codeCache, sigImpactHistorian, aliasManager);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(contractCallTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void verifyExternaliseContractResultCall() {
		// setup:
		givenValidTxnCtx();
		// and:
		given(accessor.getTxn()).willReturn(contractCallTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(accountStore.loadContract(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())))
				.willReturn(contractAccount);
		// and:
		var results = TransactionProcessingResult.successful(
				null, 1234L, 0L, 124L, Bytes.EMPTY, contractAccount.getId().asEvmAddress());
		given(evmTxProcessor.execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent, Bytes.EMPTY,
				txnCtx.consensusTime())).willReturn(results);
		given(worldState.persistProvisionalContractCreations()).willReturn(List.of(target));
		// when:
		subject.doStateTransition();

		// then:
		verify(recordService).externaliseEvmCallTransaction(any());
		verify(worldState).persistProvisionalContractCreations();
		verify(txnCtx).setTargetedContract(target);
	}

	@Test
	void verifyProcessorCallingWithCorrectCallData() {
		// setup:
		ByteString functionParams = ByteString.copyFromUtf8("0x00120");
		var op = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractCall(
						ContractCallTransactionBody.newBuilder()
								.setGas(gas)
								.setAmount(sent)
								.setFunctionParameters(functionParams)
								.setContractID(target));
		contractCallTxn = op.build();
		// and:
		given(accessor.getTxn()).willReturn(contractCallTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(accountStore.loadContract(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())))
				.willReturn(contractAccount);
		// and:
		var results = TransactionProcessingResult.successful(
				null, 1234L, 0L, 124L, Bytes.EMPTY, contractAccount.getId().asEvmAddress());
		given(evmTxProcessor.execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent,
				Bytes.fromHexString(CommonUtils.hex(functionParams.toByteArray())), txnCtx.consensusTime()))
				.willReturn(results);
		given(worldState.persistProvisionalContractCreations()).willReturn(List.of(target));
		// when:
		subject.doStateTransition();

		// then:
		verify(evmTxProcessor).execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent,
				Bytes.fromHexString(CommonUtils.hex(functionParams.toByteArray())), txnCtx.consensusTime());
		verify(sigImpactHistorian).markEntityChanged(target.getContractNum());
	}

	@Test
	void verifyProcessorCallingWithCorrectForeignCallData() throws DecoderException {
		// setup:
		prepForeign1559Tx();
		contractCallTxn = givenForeignTx().build();
		// and:
		given(accessor.getTxn()).willReturn(contractCallTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		// and:
		given(accountStore.loadAccount(new Id(senderId.getShardNum(), senderId.getRealmNum(), senderId.getAccountNum()))).willReturn(senderAccount);
		given(accountStore.loadContract(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())))
				.willReturn(contractAccount);
		Bytes callDataAsBytes = Bytes.fromHexString("0x071ddf7e");
		// and:
		var results = TransactionProcessingResult.successful(
				null, 1234L, 0L, 124L, Bytes.EMPTY, contractAccount.getId().asEvmAddress());
		given(evmTxProcessor.execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent,
				callDataAsBytes, txnCtx.consensusTime()))
				.willReturn(results);
		given(worldState.persistProvisionalContractCreations()).willReturn(List.of(target));
		// when:
		subject.doStateTransition();

		// then:
		verify(evmTxProcessor).execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent,
				callDataAsBytes, txnCtx.consensusTime());
		verify(sigImpactHistorian).markEntityChanged(target.getContractNum());
	}

	@Test
	void successfulPreFetch() {
		final var targetAlias = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e25");
		final var target = ContractID.newBuilder()
				.setEvmAddress(ByteString.copyFrom(targetAlias))
				.build();
		final var targetNum = EntityNum.fromLong(1234);
		final var txnBody = Mockito.mock(TransactionBody.class);
		final var ccTxnBody = Mockito.mock(ContractCallTransactionBody.class);

		given(accessor.getTxn()).willReturn(txnBody);
		given(txnBody.getContractCall()).willReturn(ccTxnBody);
		given(ccTxnBody.getContractID()).willReturn(target);
		given(aliasManager.lookupIdBy(target.getEvmAddress())).willReturn(targetNum);

		subject.preFetch(accessor);

		verify(codeCache).getIfPresent(targetNum.toEvmAddress());
	}

	@Test
	void codeCacheThrowingExceptionDuringGetDoesntPropagate() {
		TransactionBody txnBody = Mockito.mock(TransactionBody.class);
		ContractCallTransactionBody ccTxnBody = Mockito.mock(ContractCallTransactionBody.class);

		given(accessor.getTxn()).willReturn(txnBody);
		given(txnBody.getContractCall()).willReturn(ccTxnBody);
		given(ccTxnBody.getContractID()).willReturn(IdUtils.asContract("0.0.1324"));
		given(codeCache.getIfPresent(any(Address.class))).willThrow(new RuntimeException("oh no"));

		// when:
		assertDoesNotThrow(() -> subject.preFetch(accessor));
	}

	@Test
	void acceptsOkSyntax() {
		givenValidTxnCtx();
		given(properties.maxGas()).willReturn(gas + 1);
		// expect:
		assertEquals(OK, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	void providingGasOverLimitReturnsCorrectPrecheck() {
		givenValidTxnCtx();
		given(properties.maxGas()).willReturn(gas - 1);
		// expect:
		assertEquals(MAX_GAS_LIMIT_EXCEEDED,
				subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	void rejectsNegativeSend() {
		// setup:
		sent = -1;

		givenValidTxnCtx();
		// expect:
		assertEquals(CONTRACT_NEGATIVE_VALUE, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	void rejectsNegativeGas() {
		// setup:
		gas = -1;

		givenValidTxnCtx();

		// expect:
		assertEquals(CONTRACT_NEGATIVE_GAS, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	public void foreignTxHappyPath() throws DecoderException {
		prepForeign1559Tx();
		given(properties.maxGas()).willReturn(gas + 1);
		given(properties.getChainId()).willReturn(0x12a);

		contractCallTxn = givenForeignTx().build();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	public void foreignTxFormatError() throws DecoderException {
		prepForeign1559Tx();
		given(properties.maxGas()).willReturn(gas + 1);

		foreignTxHex =
				"02c87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";
		contractCallTxn = givenForeignTx().build();
		assertEquals(FOREIGN_TRANSACTION_FORMAT_ERROR, subject.semanticCheck().apply(contractCallTxn));

		foreignTxHex =
				"03c00000";
		contractCallTxn = givenForeignTx().build();
		assertEquals(FOREIGN_TRANSACTION_FORMAT_ERROR, subject.semanticCheck().apply(contractCallTxn));

		foreignTxHex =
				"02c00000";
		contractCallTxn = givenForeignTx().build();
		assertEquals(FOREIGN_TRANSACTION_FORMAT_ERROR, subject.semanticCheck().apply(contractCallTxn));

	}

	@Test
	public void foreignWrongChainId() throws DecoderException {
		prepForeign1559Tx();
		given(properties.maxGas()).willReturn(gas + 1);
		given(properties.getChainId()).willReturn(0x126);

		contractCallTxn = givenForeignTx().build();

		// expect:
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_CHAIN_ID, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	public void foreignWrongNonce() throws DecoderException {
		prepForeign1559Tx();
		given(properties.maxGas()).willReturn(gas + 1);
		given(properties.getChainId()).willReturn(0x12a);
		nonce = 55;

		contractCallTxn = givenForeignTx().build();

		// expect:
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_NONCE, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	public void foreignWrongGasLimit() throws DecoderException {
		prepForeign1559Tx();
		gas = 0x10000;
		given(properties.getChainId()).willReturn(0x12a);
		given(properties.maxGas()).willReturn(gas + 1);

		contractCallTxn = givenForeignTx().build();

		// expect:
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_GAS_LIMIT, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	public void foreignWrongReceiver() throws DecoderException {
		prepForeign1559Tx();
		given(properties.maxGas()).willReturn(gas + 1);
		given(properties.getChainId()).willReturn(0x12a);
		target = ContractID.newBuilder().setContractNum(1234).build();

		contractCallTxn = givenForeignTx().build();

		// expect:
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_RECEIVER_ADDRESS, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	public void foreignWrongAmount() throws DecoderException {
		prepForeign1559Tx();
		given(properties.maxGas()).willReturn(gas + 1);
		given(properties.getChainId()).willReturn(0x12a);
		sent = 1337;

		contractCallTxn = givenForeignTx().build();

		// expect:
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_AMOUNT, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	public void foreignWrongPayload() throws DecoderException {
		prepForeign1559Tx();
		given(properties.maxGas()).willReturn(gas + 1);
		given(properties.getChainId()).willReturn(0x12a);

		foreignTxPayloadStart = 33;
		contractCallTxn = givenForeignTx().build();
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_DATA, subject.semanticCheck().apply(contractCallTxn));

		foreignTxPayloadStart = 44;
		foreignTxPayloadLength = 2;
		contractCallTxn = givenForeignTx().build();
		assertEquals(FOREIGN_TRANSACTION_INCORRECT_DATA, subject.semanticCheck().apply(contractCallTxn));
		// expect:
	}

	// unchecked errors (because they are not thrown yet)
	// FOREIGN_TRANSACTION_INSUFFICIENT_FEE_LIMIT;
	// FOREIGN_TRANSACTION_INCORRECT_SENDER_ADDRESS;
	// FOREIGN_TRANSACTION_INVALID_SIGNATURE;

	private void prepForeign1559Tx() throws DecoderException {
		foreignTxHex =
				"02f86982012a0d2f2f831e84809400000000000000000000000000000000000003ef8084071ddf7ec001a05444c3a198cb2431b597a1e88fa32b583ed67de8ca13ab6e8bdb1282b0649e11a07e724c841a455e0f3c2089aa7c24da0a0af2664cf64b9a54dce33c2a5d8e5ba2";
		foreignTxType = ForeignTransactionType.ETHEREUM_EIP_1559;
		foreignTxPayloadStart = 36;
		foreignTxPayloadLength = 4;
		nonce = 13;

		senderId = AccountID.newBuilder().setAlias(ByteString.copyFrom(Hex.decodeHex(
				"302d300706052b8104000a032200033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d"))).build();
		target = EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(
				"00000000000000000000000000000000000003ef"));
		gas = 2_000_000;
		sent = 0L;
	}

	private TransactionBody.Builder givenForeignTx() throws DecoderException {
		ContractCallTransactionBody.Builder op = ContractCallTransactionBody.newBuilder()
				.setSenderID(senderId)
				.setContractID(target)
				.setGas(gas)
				.setAmount(sent);
		if (functionParameters != null) {
			op.setFunctionParameters(functionParameters);
		}
		return TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setForeignTransactionData(
						ForeignTransactionData.newBuilder()
								.setForeignTransactionBytes(ByteString.copyFrom(Hex.decodeHex(foreignTxHex)))
								.setForeignTransactionType(foreignTxType)
								.setPayloadStart(foreignTxPayloadStart)
								.setPayloadLength(foreignTxPayloadLength)
								.setNonce(nonce).build()
				)
				.setContractCall(op);

	}

	private void givenValidTxnCtx() {
		var op = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractCall(
						ContractCallTransactionBody.newBuilder()
								.setGas(gas)
								.setAmount(sent)
								.setContractID(target));
		contractCallTxn = op.build();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(senderAccount.getId().asGrpcAccount())
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
