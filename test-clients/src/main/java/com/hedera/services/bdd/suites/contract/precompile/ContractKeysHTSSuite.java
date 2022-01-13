package com.hedera.services.bdd.suites.contract.precompile;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BURN_TOKEN_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MINT_TOKEN_ORDINARY_CALL;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SINGLE_TOKEN_ASSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SINGLE_TOKEN_DISSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TRANSFER_NFT_ORDINARY_CALL;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class ContractKeysHTSSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ContractKeysHTSSuite.class);
    private static final String TOKEN_TREASURY = "treasury";

    private static final String NFT = "nft";
    private static final String CONTRACT = "theContract";

    private static final String ACCOUNT = "sender";
    private static final String RECEIVER = "receiver";

    private static final String UNIVERSAL_KEY = "multipurpose";

    private static final KeyShape CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, KeyShape.CONTRACT);
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);

    public static void main(String... args) {
        new ContractKeysHTSSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunAsync() {
        return true;
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return allOf(
                HSCS_KEY_1(),
                HSCS_KEY_2(),
                HSCS_KEY_3()
        );
    }

    List<HapiApiSpec> HSCS_KEY_1() {
        return List.of(
                callForMintWithContractKey(),
                callForTransferWithContractKey(),
                callForAssociateWithContractKey(),
                callForDissociateWithContractKey(),
                callForBurnWithContractKey()
        );
    }

    List<HapiApiSpec> HSCS_KEY_2() {
        return List.of();
    }

    List<HapiApiSpec> HSCS_KEY_3() {
        return List.of(
                callForMintWithDelegateContractKey(),
                callForTransferWithDelegateContractKey(),
                callForAssociateWithDelegateContractKey(),
                callForDissociateWithDelegateContractKey(),
                callForBurnWithDelegateContractKey()
        );
    }

    private HapiApiSpec callForMintWithContractKey() {
        final var theAccount = "anybody";
        final var mintContractByteCode = "mintContractByteCode";
        final var amount = 10L;
        final var fungibleToken = "fungibleToken";
        final var multiKey = "purpose";
        final var theContract = "mintContract";
        final var firstMintTxn = "firstMintTxn";

        final AtomicLong fungibleNum = new AtomicLong();

        return defaultHapiSpec("callForMintWithContractKey")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        fileCreate(mintContractByteCode).payingWith(theAccount),
                        updateLargeFile(theAccount, mintContractByteCode, extractByteCode(ContractResources.ORDINARY_CALLS_CONTRACT)),
                        tokenCreate(fungibleToken)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(multiKey)
                                .supplyKey(multiKey)
                                .exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
                ).when(
                        sourcing(() -> contractCreate(theContract)
                                .bytecode(mintContractByteCode).payingWith(theAccount)
                                .gas(300_000L))
                ).then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,

                                                newKeyNamed("contractKey").shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
                                                        theContract))),
                                                tokenUpdate(fungibleToken)
                                                        .supplyKey("contractKey"),

                                                contractCall(theContract, MINT_TOKEN_ORDINARY_CALL,
                                                        asAddress(spec.registry().getTokenID(fungibleToken)), amount,
                                                        new byte[]{})
                                                        .via(firstMintTxn)
                                                        .payingWith(theAccount)
                                        )),

                        childRecordsCheck(firstMintTxn, SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        changingFungibleBalances()
                                                .including(fungibleToken, TOKEN_TREASURY, 10)
                                )
                                .newTotalSupply(10)
                        ),

                        getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
                        getTokenInfo(fungibleToken).hasTotalSupply(amount),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, amount)
                );
    }


    private HapiApiSpec callForMintWithDelegateContractKey() {
        final var theAccount = "anybody";
        final var mintContractByteCode = "mintContractByteCode";
        final var amount = 10L;
        final var fungibleToken = "fungibleToken";
        final var multiKey = "purpose";
        final var theContract = "mintContract";
        final var firstMintTxn = "firstMintTxn";

        final AtomicLong fungibleNum = new AtomicLong();

        return defaultHapiSpec("callForMintWithDelegateContractKey")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        fileCreate(mintContractByteCode).payingWith(theAccount),
                        updateLargeFile(theAccount, mintContractByteCode, extractByteCode(ContractResources.ORDINARY_CALLS_CONTRACT)),
                        tokenCreate(fungibleToken)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(multiKey)
                                .supplyKey(multiKey)
                                .exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
                ).when(
                        sourcing(() -> contractCreate(theContract)
                                .bytecode(mintContractByteCode).payingWith(theAccount)
                                .gas(300_000L))
                ).then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,

                                                newKeyNamed("delegateContractKey").shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
                                                        theContract))),
                                                tokenUpdate(fungibleToken)
                                                        .supplyKey("delegateContractKey"),

                                                contractCall(theContract, MINT_TOKEN_ORDINARY_CALL,
                                                        asAddress(spec.registry().getTokenID(fungibleToken)), amount,
                                                        new byte[]{})
                                                        .via(firstMintTxn)
                                                        .payingWith(theAccount)
                                        )),

                        childRecordsCheck(firstMintTxn, SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        changingFungibleBalances()
                                                .including(fungibleToken, TOKEN_TREASURY, 10)
                                )
                                .newTotalSupply(10)
                        ),

                        getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
                        getTokenInfo(fungibleToken).hasTotalSupply(amount),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, amount)
                );
    }

    private HapiApiSpec callForTransferWithContractKey() {
        return defaultHapiSpec("callForTransferWithContractKey")
                .given(
                        newKeyNamed(UNIVERSAL_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(UNIVERSAL_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(ACCOUNT, NFT),
                        mintToken(NFT, List.of(metadata("firstMemo"), metadata("secondMemo"))),
                        fileCreate("bytecode").payingWith(ACCOUNT),
                        updateLargeFile(ACCOUNT, "bytecode", extractByteCode(ContractResources.ORDINARY_CALLS_CONTRACT))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(CONTRACT)
                                                        .payingWith(ACCOUNT)
                                                        .bytecode("bytecode")
                                                        .via("creationTx")
                                                        .gas(28_000),
                                                getTxnRecord("creationTx").logged(),

                                                newKeyNamed("contractKey").shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
                                                        CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("contractKey"),

                                                tokenAssociate(CONTRACT, List.of(NFT)),
                                                tokenAssociate(RECEIVER, List.of(NFT)),
                                                cryptoTransfer(TokenMovement.movingUnique(NFT, 1).between(TOKEN_TREASURY, ACCOUNT)),
                                                contractCall(CONTRACT, TRANSFER_NFT_ORDINARY_CALL,
                                                        asAddress(spec.registry().getTokenID(NFT)),
                                                        asAddress(spec.registry().getAccountID(ACCOUNT)),
                                                        asAddress(spec.registry().getAccountID(RECEIVER)),
                                                        1L
                                                )
                                                        .fee(ONE_HBAR)
                                                        .hasKnownStatus(SUCCESS)
                                                        .payingWith(GENESIS)
                                                        .gas(48_000)
                                                        .via("distributeTx"),
                                                getTxnRecord("distributeTx").andAllChildRecords().logged()))
                ).then(
                        getTokenInfo(NFT).hasTotalSupply(2),
                        getAccountInfo(RECEIVER).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER).hasTokenBalance(NFT, 1),
                        getAccountInfo(ACCOUNT).hasOwnedNfts(0),
                        getAccountBalance(ACCOUNT).hasTokenBalance(NFT, 0),

                        childRecordsCheck("distributeTx", SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        NonFungibleTransfers.changingNFTBalances()
                                                .including(NFT, ACCOUNT, RECEIVER, 1L)
                                ))
                );
    }

    private HapiApiSpec callForTransferWithDelegateContractKey() {
        return defaultHapiSpec("callForTransferWithDelegateContractKey")
                .given(
                        newKeyNamed(UNIVERSAL_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(UNIVERSAL_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(ACCOUNT, NFT),
                        mintToken(NFT, List.of(metadata("firstMemo"), metadata("secondMemo"))),
                        fileCreate("bytecode").payingWith(ACCOUNT),
                        updateLargeFile(ACCOUNT, "bytecode", extractByteCode(ContractResources.ORDINARY_CALLS_CONTRACT))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(CONTRACT)
                                                        .payingWith(ACCOUNT)
                                                        .bytecode("bytecode")
                                                        .via("creationTx")
                                                        .gas(28_000),
                                                getTxnRecord("creationTx").logged(),

                                                newKeyNamed("delegateContractKey").shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
                                                        CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("delegateContractKey"),

                                                tokenAssociate(CONTRACT, List.of(NFT)),
                                                tokenAssociate(RECEIVER, List.of(NFT)),
                                                cryptoTransfer(TokenMovement.movingUnique(NFT, 1).between(TOKEN_TREASURY, ACCOUNT)),
                                                contractCall(CONTRACT, TRANSFER_NFT_ORDINARY_CALL,
                                                        asAddress(spec.registry().getTokenID(NFT)),
                                                        asAddress(spec.registry().getAccountID(ACCOUNT)),
                                                        asAddress(spec.registry().getAccountID(RECEIVER)),
                                                        1L
                                                )
                                                        .fee(ONE_HBAR)
                                                        .hasKnownStatus(SUCCESS)
                                                        .payingWith(GENESIS)
                                                        .gas(48_000)
                                                        .via("distributeTx"),
                                                getTxnRecord("distributeTx").andAllChildRecords().logged()))
                ).then(
                        getTokenInfo(NFT).hasTotalSupply(2),
                        getAccountInfo(RECEIVER).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER).hasTokenBalance(NFT, 1),
                        getAccountInfo(ACCOUNT).hasOwnedNfts(0),
                        getAccountBalance(ACCOUNT).hasTokenBalance(NFT, 0),

                        childRecordsCheck("distributeTx", SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        NonFungibleTransfers.changingNFTBalances()
                                                .including(NFT, ACCOUNT, RECEIVER, 1L)
                                ))
                );
    }


    private HapiApiSpec callForAssociateWithDelegateContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final String THE_CONTRACT = "Associate/Dissociate Contract";

        return defaultHapiSpec("callAssociateWithDelegateContractKey")
                .given(
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        fileCreate(THE_CONTRACT),
                        updateLargeFile(ACCOUNT, THE_CONTRACT,
                                extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),

                                                newKeyNamed("delegateContractKey").shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("delegateContractKey"),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("vanillaTokenAssociateTxn")
                                                        .hasKnownStatus(SUCCESS)
                                        )
                        )
                ).then(
                        childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS, recordWith()
                                .status(SUCCESS)),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
                );
    }

    private HapiApiSpec callForAssociateWithContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final String THE_CONTRACT = "Associate/Dissociate Contract";

        return defaultHapiSpec("callAssociateWithContractKey")
                .given(
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        fileCreate(THE_CONTRACT),
                        updateLargeFile(ACCOUNT, THE_CONTRACT,
                                extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),

                                                newKeyNamed("contractKey").shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("contractKey"),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("vanillaTokenAssociateTxn")
                                                        .hasKnownStatus(SUCCESS)
                                        )
                        )
                ).then(
                        childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS, recordWith()
                                .status(SUCCESS)),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
                );
    }


    public HapiApiSpec callForDissociateWithDelegateContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final String THE_CONTRACT = "Associate/Dissociate Contract";
        final long TOTAL_SUPPLY = 1_000;

        return defaultHapiSpec("callDissociateWithDelegateContractKey")
                .given(
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        fileCreate(THE_CONTRACT),
                        updateLargeFile(ACCOUNT, THE_CONTRACT,
                                extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
                        cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(TOTAL_SUPPLY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
                                                newKeyNamed("delegateContractKey").shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("delegateContractKey"),
                                                cryptoUpdate(TOKEN_TREASURY).key("delegateContractKey"),

                                                tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                                                cryptoTransfer(
                                                        moving(1, VANILLA_TOKEN)
                                                                .between(TOKEN_TREASURY, ACCOUNT)),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("nonZeroTokenBalanceDissociateWithDelegateContractKeyFailedTxn")
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),

                                                cryptoTransfer(
                                                        moving(1, VANILLA_TOKEN)
                                                                .between(ACCOUNT, TOKEN_TREASURY)),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("tokenDissociateWithDelegateContractKeyHappyTxn")
                                                        .hasKnownStatus(SUCCESS)
                                        )
                        )
                ).then(
                        childRecordsCheck("nonZeroTokenBalanceDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
                                .status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)),
                        childRecordsCheck("tokenDissociateWithDelegateContractKeyHappyTxn", SUCCESS, recordWith()
                                .status(SUCCESS)),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN).logged()
                );
    }

    public HapiApiSpec callForDissociateWithContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final String THE_CONTRACT = "Associate/Dissociate Contract";
        final long TOTAL_SUPPLY = 1_000;

        return defaultHapiSpec("callDissociateWithContractKey")
                .given(
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        fileCreate(THE_CONTRACT),
                        updateLargeFile(ACCOUNT, THE_CONTRACT,
                                extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
                        cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(TOTAL_SUPPLY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
                                                newKeyNamed("contractKey").shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("contractKey"),
                                                cryptoUpdate(TOKEN_TREASURY).key("contractKey"),

                                                tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                                                cryptoTransfer(
                                                        moving(1, VANILLA_TOKEN)
                                                                .between(TOKEN_TREASURY, ACCOUNT)),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("nonZeroTokenBalanceDissociateWithContractKeyFailedTxn")
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),

                                                cryptoTransfer(
                                                        moving(1, VANILLA_TOKEN)
                                                                .between(ACCOUNT, TOKEN_TREASURY)),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("tokenDissociateWithContractKeyHappyTxn")
                                                        .hasKnownStatus(SUCCESS)
                                        )
                        )
                ).then(
                        childRecordsCheck("nonZeroTokenBalanceDissociateWithContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
                                .status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)),
                        childRecordsCheck("tokenDissociateWithContractKeyHappyTxn", SUCCESS, recordWith()
                                .status(SUCCESS)),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN).logged()
                );
    }

    private HapiApiSpec callForBurnWithDelegateContractKey() {
        final var theContract = "burn token";
        final var multiKey = "purpose";
        final String ALICE = "Alice";
        final String TOKEN = "Token";

        return defaultHapiSpec("callBurnWithDelegateContractKey")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(multiKey)
                                .adminKey(multiKey)
                                .treasury(TOKEN_TREASURY),
                        fileCreate("bytecode").payingWith(ALICE),
                        updateLargeFile(ALICE, "bytecode", extractByteCode(ContractResources.BURN_TOKEN)),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(theContract, ContractResources.BURN_TOKEN_CONSTRUCTOR_ABI,
                                                        asAddress(spec.registry().getTokenID(TOKEN)))
                                                        .payingWith(ALICE)
                                                        .bytecode("bytecode")
                                                        .via("creationTx")
                                                        .gas(28_000))),
                        getTxnRecord("creationTx").logged()
                )
                .when(

                        newKeyNamed("delegateContractKey").shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, theContract))),
                        tokenUpdate(TOKEN)
                                .supplyKey("delegateContractKey"),

                        contractCall(theContract, BURN_TOKEN_ABI, 1, new ArrayList<Long>())
                                .via("burn with contract key")
                                .gas(48_000),

                        childRecordsCheck("burn with contract key", SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        changingFungibleBalances()
                                                .including(TOKEN, TOKEN_TREASURY, -1)
                                )
                                .newTotalSupply(49)
                        )

                )
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 49)

                );
    }

    private HapiApiSpec callForBurnWithContractKey() {
        final var theContract = "burn token";
        final var multiKey = "purpose";
        final String ALICE = "Alice";
        final String TOKEN = "Token";

        return defaultHapiSpec("callBurnWithContractKey")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(multiKey)
                                .adminKey(multiKey)
                                .treasury(TOKEN_TREASURY),
                        fileCreate("bytecode").payingWith(ALICE),
                        updateLargeFile(ALICE, "bytecode", extractByteCode(ContractResources.BURN_TOKEN)),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(theContract, ContractResources.BURN_TOKEN_CONSTRUCTOR_ABI,
                                                        asAddress(spec.registry().getTokenID(TOKEN)))
                                                        .payingWith(ALICE)
                                                        .bytecode("bytecode")
                                                        .via("creationTx")
                                                        .gas(28_000))),
                        getTxnRecord("creationTx").logged()
                )
                .when(

                        newKeyNamed("contractKey").shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, theContract))),
                        tokenUpdate(TOKEN)
                                .supplyKey("contractKey"),

                        contractCall(theContract, BURN_TOKEN_ABI, 1, new ArrayList<Long>())
                                .via("burn with contract key")
                                .gas(48_000),

                        childRecordsCheck("burn with contract key", SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        changingFungibleBalances()
                                                .including(TOKEN, TOKEN_TREASURY, -1)
                                )
                                .newTotalSupply(49)
                        )

                )
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 49)

                );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
