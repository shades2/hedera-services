package com.hedera.services.store.contracts.precompile;
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

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_ALLOWANCE;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_APPROVE;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_BALANCE_OF_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_DECIMALS;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_ERC_TRANSFER;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_ERC_TRANSFER_FROM;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_GET_APPROVED;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_IS_APPROVED_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_NAME;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_OWNER_OF_NFT;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_SET_APPROVAL_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_SYMBOL;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TOKEN_URI_NFT;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TOTAL_SUPPLY_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.AMOUNT;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.accountId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.failResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidSigResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.precompiledContract;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.token;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenTransferChanges;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ERC20PrecompilesTest {
    @Mock
    private GlobalDynamicProperties dynamicProperties;
    @Mock
    private OptionValidator validator;
    @Mock
    private GasCalculator gasCalculator;
    @Mock
    private MessageFrame frame;
    @Mock
    private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock
    private RecordsHistorian recordsHistorian;
    @Mock
    private DecodingFacade decoder;
    @Mock
    private EncodingFacade encoder;
    @Mock
    private HTSPrecompiledContract.TokenStoreFactory tokenStoreFactory;
    @Mock
    private HTSPrecompiledContract.AccountStoreFactory accountStoreFactory;
    @Mock
    private SideEffectsTracker sideEffects;
    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock
    private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;
    @Mock
    private HederaStackedWorldStateUpdater worldUpdater;
    @Mock
    private WorldLedgers wrappedLedgers;
    @Mock
    private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts;
    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRels;
    @Mock
    private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;
    @Mock
    private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock
    private ExpiringCreations creator;
    @Mock
    private DissociationFactory dissociationFactory;
    @Mock
    private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock
    private FeeCalculator feeCalculator;
    @Mock
    private StateView stateView;
    @Mock
    private PrecompilePricingUtils precompilePricingUtils;
    @Mock
    private CryptoTransferTransactionBody cryptoTransferTransactionBody;
    @Mock
    private ImpliedTransfersMeta impliedTransfersMeta;
    @Mock
    private ImpliedTransfers impliedTransfers;
    @Mock
    private TransferLogic transferLogic;
    @Mock
    private HederaTokenStore hederaTokenStore;
    @Mock
    private HTSPrecompiledContract.TransferLogicFactory transferLogicFactory;
    @Mock
    private HTSPrecompiledContract.HederaTokenStoreFactory hederaTokenStoreFactory;
    @Mock
    private FeeObject mockFeeObject;
    @Mock
    private ContractAliases aliases;
    @Mock
    private UsagePricesProvider resourceCosts;
    @Mock
    private BlockValues blockValues;
    @Mock
    private SigImpactHistorian sigImpactHistorian;
    @Mock
    private CreateChecks createChecks;
    @Mock
    private EntityIdSource entityIdSource;
    @Mock
    private ApproveAllowanceChecks allowanceChecks;
    @Mock
    private AccountStore accountStore;
    @Mock
    private CryptoApproveAllowanceTransactionBody cryptoApproveAllowanceTransactionBody;
    @Mock
    private DeleteAllowanceChecks deleteAllowanceChecks;

    private HTSPrecompiledContract subject;
    private final EntityIdSource ids = NOOP_ID_SOURCE;
    private MockedStatic<EntityIdUtils> entityIdUtils;

    @BeforeEach
    void setUp() {
        subject = new HTSPrecompiledContract(
                validator, dynamicProperties, gasCalculator,
                sigImpactHistorian, recordsHistorian, sigsVerifier, decoder, encoder,
                syntheticTxnFactory, creator, dissociationFactory, impliedTransfersMarshal, () -> feeCalculator,
                stateView, precompilePricingUtils, resourceCosts, createChecks, entityIdSource, allowanceChecks,
                deleteAllowanceChecks);
        subject.setTransferLogicFactory(transferLogicFactory);
        subject.setTokenStoreFactory(tokenStoreFactory);
        subject.setHederaTokenStoreFactory(hederaTokenStoreFactory);
        subject.setAccountStoreFactory(accountStoreFactory);
        subject.setSideEffectsFactory(() -> sideEffects);
        entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils.when(() -> EntityIdUtils.accountIdFromEvmAddress(senderAddress)).thenReturn(sender);
        entityIdUtils.when(() -> EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(sender)).thenReturn(senderAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(receiver)).thenReturn(recipientAddress);
        entityIdUtils.when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray())).thenReturn(token);
        given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    @AfterEach
    void closeMocks() {
        entityIdUtils.close();
    }


    @Test
    void allowanceDisabled() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);

        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        Bytes pretendArgumentsApprove = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                Bytes.of(Integers.toBytes(ABI_ID_APPROVE)));

        // when:
        subject.prepareFields(frame);

        assertThrows(InvalidTransactionException.class, () -> subject.prepareComputation(pretendArgumentsApprove, a -> a));

        Bytes pretendArgumentsTransferFrom = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER_FROM)));

        // when:
        subject.prepareFields(frame);

        assertThrows(InvalidTransactionException.class, () -> subject.prepareComputation(pretendArgumentsTransferFrom, a -> a));

        Bytes pretendArgumentsAllowance = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                Bytes.of(Integers.toBytes(ABI_ID_ALLOWANCE)));


        // when:
        subject.prepareFields(frame);

        assertThrows(InvalidTransactionException.class, () -> subject.prepareComputation(pretendArgumentsAllowance, a -> a));

        Bytes pretendArgumentsApproveForAll = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                Bytes.of(Integers.toBytes(ABI_ID_SET_APPROVAL_FOR_ALL)));

        // when:
        subject.prepareFields(frame);

        assertThrows(InvalidTransactionException.class, () -> subject.prepareComputation(pretendArgumentsApproveForAll, a -> a));

        Bytes pretendArgumentsGetApproved = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                Bytes.of(Integers.toBytes(ABI_ID_GET_APPROVED)));

        // when:
        subject.prepareFields(frame);

        assertThrows(InvalidTransactionException.class, () -> subject.prepareComputation(pretendArgumentsGetApproved, a -> a));

        Bytes pretendArgumentsApprovedForAll = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                Bytes.of(Integers.toBytes(ABI_ID_IS_APPROVED_FOR_ALL)));


        // when:
        subject.prepareFields(frame);

        assertThrows(InvalidTransactionException.class, () -> subject.prepareComputation(pretendArgumentsApprovedForAll, a -> a));
    }

    @Test
    void invalidNestedFunctionSelector () {
        Bytes nestedPretendArguments = Bytes.of(0,0,0,0);
        Bytes pretendArguments = givenMinimalFrameContextWithoutParentUpdater(nestedPretendArguments);

        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        final var result = subject.compute(pretendArguments, frame);
        assertNull(result);
    }

    @Test
    void gasCalculationForReadOnlyMethod() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_NAME));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(encoder.encodeName(any())).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(TEST_CONSENSUS_TIME);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.compute(pretendArguments, frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void gasCalculationForModifyingMethod() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(TOKEN_TRANSFER_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody)).willReturn(OK);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any())).willReturn(true);
        given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(Mockito.anyBoolean(), any(), any(), any())).willReturn(true, true);

        given(hederaTokenStoreFactory.newHederaTokenStore(
                ids, validator, sideEffects, dynamicProperties, tokenRels, nfts, tokens
        )).willReturn(hederaTokenStore);

        given(transferLogicFactory.newLogic(
                accounts, nfts, tokenRels, hederaTokenStore,
                sideEffects,
                dynamicProperties,
                validator,
                null,
                recordsHistorian
        )).willReturn(transferLogic);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().setCryptoTransfer(cryptoTransferTransactionBody).build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(decoder.decodeERCTransfer(eq(nestedPretendArguments), any(), any(), any())).willReturn(
                Collections.singletonList(TOKEN_TRANSFER_WRAPPER));

        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(encoder.encodeEcFungibleTransfer(true)).willReturn(successResult);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(TEST_CONSENSUS_TIME);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.compute(pretendArguments, frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void name() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_NAME));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(encoder.encodeName(any())).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.shouldExportPrecompileResults()).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        ArgumentCaptor<EvmFnResult> captor = ArgumentCaptor.forClass(EvmFnResult.class);
        verify(mockRecordBuilder).setContractCallResult(captor.capture());
        assertEquals(0L, captor.getValue().getGas());
        assertEquals(0L, captor.getValue().getAmount());
        assertEquals(EvmFnResult.EMPTY, captor.getValue().getFunctionParameters());
    }

    @Test
    void symbol() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_SYMBOL));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(encoder.encodeSymbol(any())).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void decimals() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_DECIMALS));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);

        given(wrappedLedgers.decimalsOf(token)).willReturn(10);
        given(encoder.encodeDecimals(10)).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void totalSupply() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TOTAL_SUPPLY_TOKEN));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);

        given(wrappedLedgers.totalSupplyOf(token)).willReturn(10L);
        given(encoder.encodeTotalSupply(10L)).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void allowance() {
        TreeMap<FcTokenAllowanceId, Long> alowances = new TreeMap<>();
        alowances.put(FcTokenAllowanceId.from(EntityNum.fromLong(token.getTokenNum()), EntityNum.fromLong(receiver.getAccountNum())), 10L);
        
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ALLOWANCE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);


        given(accounts.contains(any())).willReturn(true);
        given(decoder.decodeTokenAllowance(eq(nestedPretendArguments), any())).willReturn(
                ALLOWANCE_WRAPPER);
        given(accounts.get(any(), any())).willReturn(alowances);
        given(encoder.encodeAllowance(10L)).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }


    @Test
    void balanceOf() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_BALANCE_OF_TOKEN));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);

        given(decoder.decodeBalanceOf(eq(nestedPretendArguments), any())).willReturn(BALANCE_OF_WRAPPER);
        given(wrappedLedgers.balanceOf(any(), any())).willReturn(10L);
        given(encoder.encodeBalance(10L)).willReturn(successResult);

        entityIdUtils.when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray())).thenReturn(token);
        entityIdUtils.when(() -> EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void allowanceValidation() {
        List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        List<TokenAllowance> tokenAllowances = new ArrayList<>();
        List<NftAllowance> nftAllowances = new ArrayList<>();
        Map<FcTokenAllowanceId, Long> allowances = Map.of(fungibleAllowanceId, 0L);
        
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);

        given(syntheticTxnFactory.createFungibleApproval(APPROVE_WRAPPER))
                .willReturn(mockSynthBodyBuilder);

        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);

        given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(true), any())).willReturn(
                APPROVE_WRAPPER);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(failResult, result);

    }

    @Test
    void approve() {
        List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        List<TokenAllowance> tokenAllowances = new ArrayList<>();
        List<NftAllowance> nftAllowances = new ArrayList<>();
        Map<FcTokenAllowanceId, Long> allowances = Map.of(fungibleAllowanceId, 0L);

        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);

        given(syntheticTxnFactory.createFungibleApproval(APPROVE_WRAPPER))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance()).willReturn(cryptoApproveAllowanceTransactionBody);

        given(accountStoreFactory.newAccountStore(validator, accounts)).willReturn(accountStore);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(new Account(accountId));

        given(allowanceChecks.allowancesValidation(
                cryptoAllowances, tokenAllowances, nftAllowances, new Account(accountId), stateView)).willReturn(OK);

        given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(true), any())).willReturn(
                APPROVE_WRAPPER);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(encoder.encodeApprove(true)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transfer() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(TOKEN_TRANSFER_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody)).willReturn(OK);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any())).willReturn(true);
        given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(Mockito.anyBoolean(), any(), any(), any())).willReturn(true, true);

        given(hederaTokenStoreFactory.newHederaTokenStore(
                ids, validator, sideEffects, dynamicProperties, tokenRels, nfts, tokens
        )).willReturn(hederaTokenStore);

        given(transferLogicFactory.newLogic(
                accounts, nfts, tokenRels, hederaTokenStore,
                sideEffects,
                dynamicProperties,
                validator,
                null,
                recordsHistorian
        )).willReturn(transferLogic);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().setCryptoTransfer(cryptoTransferTransactionBody).build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(decoder.decodeERCTransfer(eq(nestedPretendArguments), any(), any(), any())).willReturn(
                Collections.singletonList(TOKEN_TRANSFER_WRAPPER));

        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(encoder.encodeEcFungibleTransfer(true)).willReturn(successResult);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferFrom() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER_FROM));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(TOKEN_TRANSFER_FROM_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody)).willReturn(OK);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any())).willReturn(true);
        given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(Mockito.anyBoolean(), any(), any(), any())).willReturn(true, true);

        given(hederaTokenStoreFactory.newHederaTokenStore(
                ids, validator, sideEffects, dynamicProperties, tokenRels, nfts, tokens
        )).willReturn(hederaTokenStore);

        given(transferLogicFactory.newLogic(
                accounts, nfts, tokenRels, hederaTokenStore,
                sideEffects,
                dynamicProperties,
                validator,
                null,
                recordsHistorian
        )).willReturn(transferLogic);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().setCryptoTransfer(cryptoTransferTransactionBody).build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);

        given(decoder.decodeERCTransferFrom(eq(nestedPretendArguments), any(), eq(true), any(), any(), any()))
                .willReturn(Collections.singletonList(TOKEN_TRANSFER_FROM_WRAPPER));

        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(encoder.encodeEcFungibleTransfer(true)).willReturn(successResult);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferFails() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(TOKEN_TRANSFER_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody)).willReturn(OK);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any())).willReturn(false);
        given(hederaTokenStoreFactory.newHederaTokenStore(
                ids, validator, sideEffects, dynamicProperties, tokenRels, nfts, tokens
        )).willReturn(hederaTokenStore);

        given(creator.createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE)).willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().setCryptoTransfer(cryptoTransferTransactionBody).build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(decoder.decodeERCTransfer(eq(nestedPretendArguments), any(), any(), any())).willReturn(
                Collections.singletonList(TOKEN_TRANSFER_WRAPPER));

        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.computeGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidSigResult, result);
    }

    @Test
    void ownerOfNotSupported() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_OWNER_OF_NFT));
        Bytes pretendArguments = givenMinimalFrameContextWithoutParentUpdater(nestedPretendArguments);

        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        subject.prepareFields(frame);

        final var exception = assertThrows(InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArguments, a -> a));
        assertEquals(NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    @Test
    void tokenURINotSupported() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TOKEN_URI_NFT));
        Bytes pretendArguments = givenMinimalFrameContextWithoutParentUpdater(nestedPretendArguments);

        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        subject.prepareFields(frame);

        final var exception = assertThrows(InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArguments, a -> a));
        assertEquals(NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    private Bytes givenMinimalFrameContext(Bytes nestedArg) {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                nestedArg);
    }

    private Bytes givenMinimalFrameContextWithoutParentUpdater(Bytes nestedArg) {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                nestedArg);
    }

    private void givenLedgers() {
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

    public static final BalanceOfWrapper BALANCE_OF_WRAPPER = new BalanceOfWrapper(sender);

    public static final TokenAllowanceWrapper ALLOWANCE_WRAPPER = new TokenAllowanceWrapper(sender, receiver);

    public static final TokenTransferWrapper TOKEN_TRANSFER_WRAPPER = new TokenTransferWrapper(
            new ArrayList<>() {},
            List.of(new SyntheticTxnFactory.FungibleTokenTransfer(AMOUNT, false, token, null, receiver),
                    new SyntheticTxnFactory.FungibleTokenTransfer(-AMOUNT, false, token, sender, null))
    );

    public static final TokenTransferWrapper TOKEN_TRANSFER_FROM_WRAPPER = new TokenTransferWrapper(
            new ArrayList<>() {},
            List.of(new SyntheticTxnFactory.FungibleTokenTransfer(AMOUNT, true, token, null, receiver),
                    new SyntheticTxnFactory.FungibleTokenTransfer(-AMOUNT, true, token, sender, null))
    );

    private static final FcTokenAllowanceId fungibleAllowanceId =
            FcTokenAllowanceId.from(EntityNum.fromTokenId(token), EntityId.fromGrpcAccountId(receiver).asNum());

    public static final ApproveWrapper APPROVE_WRAPPER = new ApproveWrapper(token, receiver, BigInteger.ONE, BigInteger.ZERO, true);
}
