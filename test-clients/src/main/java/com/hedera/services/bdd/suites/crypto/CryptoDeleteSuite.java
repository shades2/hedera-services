package com.hedera.services.bdd.suites.crypto;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.approxChangeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.QueryVerbsWithAlias.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbsWithAlias.cryptoDeleteAliased;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;

public class CryptoDeleteSuite extends HapiApiSuite {
	static final Logger log = LogManager.getLogger(CryptoDeleteSuite.class);
	private static final long TOKEN_INITIAL_SUPPLY = 500;

	public static void main(String... args) {
		new CryptoDeleteSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				fundsTransferOnDelete(),
				cannotDeleteAccountsWithNonzeroTokenBalances(),
				cannotDeleteAlreadyDeletedAccount(),
				cannotDeleteAccountWithSameBeneficiary(),
				cannotDeleteTreasuryAccount(),
				deletedAccountCannotBePayer(),
				canDeleteAccountSpecifiedWithAlias(),
				transferAccountCanBeSpecifiedAsAlias()
		});
	}

	private HapiApiSpec canDeleteAccountSpecifiedWithAlias() {
		final var aliasToBeDeleted = "alias";

		return defaultHapiSpec("canDeleteAccountSpecifiedWithAlias")
				.given(
						newKeyNamed(aliasToBeDeleted),
						cryptoCreate("transferAccount").balance(0L)
				).when(
						cryptoTransfer(tinyBarsFromToWithAlias(DEFAULT_PAYER, aliasToBeDeleted, ONE_HUNDRED_HBARS)).via(
								"autoCreation"),
						getTxnRecord("autoCreation").andAllChildRecords().hasChildRecordCount(1),

						cryptoDeleteAliased(aliasToBeDeleted)
								.transfer("transferAccount")
								.hasKnownStatus(SUCCESS)
								.signedBy(aliasToBeDeleted, "transferAccount", DEFAULT_PAYER)
								.via("deleteTxn")
				).then(
						getAccountInfo("transferAccount")
								.has(accountWith().expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.05)),
						getTxnRecord("deleteTxn").logged(),
						getAliasedAccountInfo(aliasToBeDeleted).hasCostAnswerPrecheck(ACCOUNT_DELETED));
	}

	private HapiApiSpec transferAccountCanBeSpecifiedAsAlias() {
		final var aliasToBeDeleted = "alias";
		final var transferAccount = "transferAccount";

		return defaultHapiSpec("transferAccountCanBeSpecifiedAsAlias")
				.given(
						newKeyNamed(aliasToBeDeleted),
						newKeyNamed(transferAccount)
				).when(
						cryptoTransfer(tinyBarsFromToWithAlias(DEFAULT_PAYER, aliasToBeDeleted, ONE_HUNDRED_HBARS),
								tinyBarsFromToWithAlias(DEFAULT_PAYER, transferAccount, ONE_HUNDRED_HBARS)).via(
								"autoCreation"),
						getTxnRecord("autoCreation").andAllChildRecords().hasChildRecordCount(2),

						cryptoDeleteAliased(aliasToBeDeleted)
								.transferAliased(transferAccount)
								.hasKnownStatus(SUCCESS)
								.signedBy(aliasToBeDeleted, transferAccount, DEFAULT_PAYER)
								.via("deleteTxn")
				).then(
						getAliasedAccountInfo(aliasToBeDeleted).hasCostAnswerPrecheck(ACCOUNT_DELETED),
						getAliasedAccountInfo(transferAccount).has(
								accountWith().expectedBalanceWithChargedUsd(2 * ONE_HUNDRED_HBARS, 0.1, 0.05)),
						getTxnRecord("deleteTxn").logged());
	}

	private HapiApiSpec deletedAccountCannotBePayer() {
		// Account Names
		String SUBMITTING_NODE_ACCOUNT = "0.0.3";
		String ACCOUNT_TO_BE_DELETED = "toBeDeleted";
		String BENEFICIARY_ACCOUNT = "beneficiaryAccountForDeletedAccount";

		// Snapshot Names
		String SUBMITTING_NODE_PRE_TRANSFER = "submittingNodePreTransfer";
		String SUBMITTING_NODE_AFTER_BALANCE_LOAD = "submittingNodeAfterBalanceLoad";

		return defaultHapiSpec("DeletedAccountCannotBePayer")
				.given(
						cryptoCreate(ACCOUNT_TO_BE_DELETED),
						cryptoCreate(BENEFICIARY_ACCOUNT).balance(0L)
				).when(
				).then(
						balanceSnapshot(SUBMITTING_NODE_PRE_TRANSFER, SUBMITTING_NODE_ACCOUNT),
						cryptoTransfer(tinyBarsFromTo(GENESIS, SUBMITTING_NODE_ACCOUNT, 1000000000)),
						balanceSnapshot(SUBMITTING_NODE_AFTER_BALANCE_LOAD, SUBMITTING_NODE_ACCOUNT),
						cryptoDelete(ACCOUNT_TO_BE_DELETED)
								.transfer(BENEFICIARY_ACCOUNT)
								.deferStatusResolution(),
						cryptoTransfer(tinyBarsFromTo(BENEFICIARY_ACCOUNT, GENESIS, 1))
								.payingWith(ACCOUNT_TO_BE_DELETED)
								.hasKnownStatus(PAYER_ACCOUNT_DELETED),
						getAccountBalance(SUBMITTING_NODE_ACCOUNT)
								.hasTinyBars(
										approxChangeFromSnapshot(SUBMITTING_NODE_AFTER_BALANCE_LOAD, -100000, 50000))
								.logged()
				);
	}

	private HapiApiSpec fundsTransferOnDelete() {
		long B = HapiSpecSetup.getDefaultInstance().defaultBalance();

		return defaultHapiSpec("FundsTransferOnDelete")
				.given(
						cryptoCreate("toBeDeleted"),
						cryptoCreate("transferAccount").balance(0L)
				).when(
						cryptoDelete("toBeDeleted")
								.transfer("transferAccount")
								.via("deleteTxn")
				).then(
						getAccountInfo("transferAccount")
								.has(accountWith().balance(B)),
						getTxnRecord("deleteTxn")
								.hasPriority(recordWith().transfers(including(
										tinyBarsFromTo("toBeDeleted", "transferAccount", B)))));
	}

	private HapiApiSpec cannotDeleteAccountsWithNonzeroTokenBalances() {
		return defaultHapiSpec("CannotDeleteAccountsWithNonzeroTokenBalances")
				.given(
						cryptoCreate("toBeDeleted"),
						cryptoCreate("transferAccount"),
						cryptoCreate(TOKEN_TREASURY)
				).when(
						tokenCreate("misc")
								.initialSupply(TOKEN_INITIAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						tokenAssociate("toBeDeleted", "misc"),
						cryptoTransfer(moving(TOKEN_INITIAL_SUPPLY, "misc")
								.between(TOKEN_TREASURY, "toBeDeleted"))
				).then(
						cryptoDelete("toBeDeleted")
								.transfer("transferAccount")
								.hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
				);
	}

	private HapiApiSpec cannotDeleteAlreadyDeletedAccount() {
		return defaultHapiSpec("CannotDeleteAlreadyDeletedAccount")
				.given(
						cryptoCreate("toBeDeleted"),
						cryptoCreate("transferAccount")
				)
				.when(
						cryptoDelete("toBeDeleted")
								.transfer("transferAccount")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						cryptoDelete("toBeDeleted")
								.transfer("transferAccount")
								.hasKnownStatus(ACCOUNT_DELETED)
				);
	}

	private HapiApiSpec cannotDeleteAccountWithSameBeneficiary() {
		return defaultHapiSpec("CannotDeleteAccountWithSameBeneficiary")
				.given(
						cryptoCreate("toBeDeleted")
				)
				.when()
				.then(
						cryptoDelete("toBeDeleted")
								.transfer("toBeDeleted")
								.hasPrecheck(TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT)
				);
	}

	private HapiApiSpec cannotDeleteTreasuryAccount() {
		return defaultHapiSpec("CannotDeleteTreasuryAccount")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("transferAccount")
				)
				.when(
						tokenCreate("toBeTransferred")
								.initialSupply(TOKEN_INITIAL_SUPPLY)
								.treasury("treasury")
				)
				.then(
						cryptoDelete("treasury")
								.transfer("transferAccount")
								.hasKnownStatus(ACCOUNT_IS_TREASURY)
				);
	}
}
