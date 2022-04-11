package com.hedera.services.bdd.suites.contract.hapi;

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
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PAYABLE_CONSTRUCTOR;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemContractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemContractUndelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

public class ContractDeleteSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractDeleteSuite.class);

	public static void main(String... args) {
		new ContractDeleteSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						rejectsWithoutProperSig(),
						systemCannotDeleteOrUndeleteContracts(),
						deleteWorksWithMutableContract(),
						deleteFailsWithImmutableContract(),
						deleteFailsWhenContractIsTreasury(),
						deleteFailsWhenContractHasNonZeroBalance(),
						deleteTransfersToAccount(),
						deleteTransfersToContract()
				}
		);
	}

	HapiApiSpec rejectsWithoutProperSig() {
		return defaultHapiSpec("ScDelete")
				.given(
						contractCreate("tbd")
				).when().then(
						contractDelete("tbd")
								.signedBy(GENESIS)
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec systemCannotDeleteOrUndeleteContracts() {
		return defaultHapiSpec("SystemCannotDeleteOrUndeleteContracts")
				.given(
						contractCreate("test-contract")
				).when( ).then(
						systemContractDelete("test-contract")
								.payingWith(SYSTEM_DELETE_ADMIN)
								.hasPrecheck(NOT_SUPPORTED),
						systemContractUndelete("test-contract")
								.payingWith(SYSTEM_UNDELETE_ADMIN)
								.hasPrecheck(NOT_SUPPORTED),
						getContractInfo("test-contract").hasAnswerOnlyPrecheck(OK)
				);
	}

	private HapiApiSpec deleteWorksWithMutableContract() {
		return defaultHapiSpec("DeleteWorksWithMutableContract")
				.given(
						contractCreate("toBeDeleted")
				).when().then(
						contractDelete("toBeDeleted"),
						getContractInfo("toBeDeleted")
								.has(contractWith().isDeleted())
				);
	}

	private HapiApiSpec deleteFailsWithImmutableContract() {
		return defaultHapiSpec("DeleteFailsWithImmutableContract")
				.given(
						contractCreate("immutable").omitAdminKey()
				).when().then(
						contractDelete("immutable").hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT)
				);
	}

	private HapiApiSpec deleteTransfersToAccount() {
		return defaultHapiSpec("DeleteTransfersToAccount")
				.given(
						fileCreate("contractBytecode").path(PAYABLE_CONSTRUCTOR),
						cryptoCreate("receiver").balance(0L),
						contractCreate("toBeDeleted").bytecode("contractBytecode").balance(1L)
				).when(
						contractDelete("toBeDeleted").transferAccount("receiver")
				).then(
						getAccountBalance("receiver").hasTinyBars(1L)
				);
	}

	private HapiApiSpec deleteFailsWhenContractIsTreasury() {
		return defaultHapiSpec("DeleteFailsWhenContractIsTreasury")
				.given(
						fileCreate("contractBytecode").path(PAYABLE_CONSTRUCTOR),
						cryptoCreate("receiver").balance(0L),
						contractCreate("toBeDeleted").bytecode("contractBytecode").balance(1L),
						tokenCreate("tokenA").treasury("toBeDeleted").initialSupply(10)
				).when(
						// TODO: Modify this, when the new ResponseCode is introduced
						contractDelete("toBeDeleted").transferAccount("receiver").hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
				).then(
						getAccountBalance("receiver").hasTinyBars(0L)
				);
	}

	private HapiApiSpec deleteFailsWhenContractHasNonZeroBalance() {
		return defaultHapiSpec("DeleteFailsWhenContractHasNonZeroBalance")
				.given(
						fileCreate("contractBytecode").path(PAYABLE_CONSTRUCTOR),
						cryptoCreate("receiver").balance(0L),
						contractCreate("toBeDeleted").bytecode("contractBytecode").balance(1L),
						tokenCreate("tokenA").treasury("receiver").initialSupply(10),
						tokenAssociate("toBeDeleted", "tokenA"),
						cryptoTransfer(TokenMovement.moving(5, "tokenA").between("receiver", "toBeDeleted"))
				).when(
						contractDelete("toBeDeleted").transferAccount("receiver").hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
				).then(
						getAccountBalance("receiver").hasTinyBars(0L)
				);
	}

	private HapiApiSpec deleteTransfersToContract() {
		return defaultHapiSpec("DeleteTransfersToContract")
				.given(
						fileCreate("contractBytecode").path(PAYABLE_CONSTRUCTOR),
						contractCreate("receiver").balance(0L),
						contractCreate("toBeDeleted").bytecode("contractBytecode").balance(1L)
				).when(
						contractDelete("toBeDeleted").transferContract("receiver")
				).then(
						getAccountBalance("receiver").hasTinyBars(1L)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
