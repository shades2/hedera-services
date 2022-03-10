/*
 * -
 *  * ‌
 * * Hedera Services Node
 *  *
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  *
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
 *
 */

package com.hedera.services.bdd.suites.perf.crypto;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoAdjustAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class CryptoAllowancePerfSuite extends LoadTest {
	private static final Logger log = LogManager.getLogger(CryptoAllowancePerfSuite.class);

	public static void main(String... args) {
		CryptoAllowancePerfSuite suite = new CryptoAllowancePerfSuite();
		suite.runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				createNeededEntities(),
				runCryptoAllowances()
		);
	}

	private HapiApiSpec createNeededEntities() {
		final int NUM_CREATES = 500;
		return defaultHapiSpec("createNeededEntities")
				.given(
				).when(
						inParallel(
								asOpArray(NUM_CREATES, i ->
										cryptoCreate("spender" + i)
												.balance(100_000_000_000L)
												.key(GENESIS)
												.withRecharging()
												.rechargeWindow(30)
												.payingWith(GENESIS)
												.hasKnownStatus(ResponseCodeEnum.SUCCESS)
								)
						),

						cryptoCreate("owner")
								.balance(100_000_000_000L)
								.key(GENESIS)
								.withRecharging()
								.rechargeWindow(30)
								.payingWith(GENESIS),
						sleepFor(60_000L)
				).then(
						newKeyNamed("supplyKey"),
						tokenCreate("token")
								.payingWith(GENESIS)
								.initialSupply(100_000_000_000L)
								.signedBy(GENESIS),
						tokenCreate("nft")
								.maxSupply(500L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						mintToken("token", 10000L).via("tokenMint"),
						mintToken("nft", List.of(ByteString.copyFromUtf8("a"))),
						inParallel(
								asOpArray(NUM_CREATES, i ->
										cryptoTransfer(movingUnique("nft", 1L)
												.between(TOKEN_TREASURY, "owner")))),
						sleepFor(60_000L)
				);
	}

	private HapiApiSpec runCryptoAllowances() {
		final int NUM_ALLOWANCES = 1000;
		return defaultHapiSpec("runCryptoAllowances")
				.given(
				).when(
						inParallel(
								asOpArray(NUM_ALLOWANCES, i ->
										cryptoApproveAllowance()
												.payingWith("owner")
												.addCryptoAllowance("owner", "spender" + (i % 500), 1L)
												.addTokenAllowance("owner", "token", "spender" + (i % 500), 1L)
												.addNftAllowance("owner", "nft", "spender" + (i % 500), false,
														List.of(1L))
												.deferStatusResolution()
								)
						),
						sleepFor(60_000L),
						inParallel(
								asOpArray(NUM_ALLOWANCES, i ->
										cryptoAdjustAllowance()
												.payingWith("owner")
												.addCryptoAllowance("owner", "spender" + (i % 500), 2L)
												.addTokenAllowance("owner", "token" + (i % 500), "spender" + (i % 500),
														2L)
												.addNftAllowance("owner", "nft", "spender" + (i % 500), true,
														List.of(1L))
												.deferStatusResolution()
								)
						),
						sleepFor(60_000L)
				).then(
						freezeOnly().payingWith(GENESIS).startingIn(60).seconds()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
