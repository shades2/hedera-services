package com.hedera.services.bdd.suites.perf.crypto;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoAdjustAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class CryptoAllowancePerfSuite extends LoadTest {
	private static final Logger log = LogManager.getLogger(CryptoAllowancePerfSuite.class);
	private Random r = new Random();
	private static final int SPENDER_ACCOUNTS = 100;

	public static void main(String... args) {
		parseArgs(args);

		CryptoAllowancePerfSuite suite = new CryptoAllowancePerfSuite();
		suite.runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				runCryptoAllowances()
		);
	}

	protected HapiApiSpec runCryptoAllowances() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();

		Supplier<HapiSpecOperation[]> allowanceBurst = () -> {
			String owner = "owner";
			String spender = "spender" + r.nextInt(SPENDER_ACCOUNTS);

			return new HapiSpecOperation[] {
					cryptoApproveAllowance()
							.payingWith(owner)
							.addCryptoAllowance(owner, spender, 1L)
							.addTokenAllowance(owner, "token", spender, 1L)
							.addNftAllowance(owner, "nft", spender, false, List.of(1L))
							.hasKnownStatusFrom(SUCCESS, OK, INSUFFICIENT_PAYER_BALANCE
									, UNKNOWN, TRANSACTION_EXPIRED,
									INSUFFICIENT_ACCOUNT_BALANCE)
							.hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED)
							.deferStatusResolution(),
					cryptoAdjustAllowance()
							.payingWith(owner)
							.addCryptoAllowance(owner, spender, 2L)
							.addTokenAllowance(owner, "token", spender, 2L)
							.addNftAllowance(owner, "nft", spender, true, List.of(1L))
							.hasKnownStatusFrom(SUCCESS, OK, INSUFFICIENT_PAYER_BALANCE
									, UNKNOWN, TRANSACTION_EXPIRED,
									INSUFFICIENT_ACCOUNT_BALANCE)
							.hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED)
							.deferStatusResolution()
			};
		};

		return defaultHapiSpec("RunCryptoTransfersWithAutoAccounts")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				).when(
						cryptoCreate("owner")
								.balance(ignore -> settings.getInitialBalance())
								.payingWith(GENESIS)
								.withRecharging()
								.key(GENESIS)
								.rechargeWindow(3).logging()
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),

						withOpContext((spec, opLog) -> {
							List<HapiSpecOperation> ops = new ArrayList<>();
							for (int i = 0; i < SPENDER_ACCOUNTS; i++) {
								ops.add(cryptoCreate("spender" + i)
										.payingWith(GENESIS)
										.hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED)
										.key(GENESIS)
										.logged());
							}
							allRunFor(spec, ops);
						}),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS).logging(),
						newKeyNamed("supplyKey"),
						tokenCreate("token")
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(100_000_000_000L)
								.initialSupply(100_000L)
								.treasury(TOKEN_TREASURY),
						tokenCreate("nft")
								.maxSupply(500L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate("owner", "token"),
						tokenAssociate("owner", "nft"),
						mintToken("token", 10000L).via("tokenMint"),
						withOpContext((spec, opLog) -> {
							List<HapiSpecOperation> ops = new ArrayList<>();
							for (int i = 0; i < SPENDER_ACCOUNTS; i++) {
								ops.add(mintToken("nft", List.of(ByteString.copyFromUtf8("a" + i))));
							}
							allRunFor(spec, ops);
						}),
						withOpContext((spec, opLog) -> {
							List<HapiSpecOperation> ops = new ArrayList<>();
							for (int i = 1; i < SPENDER_ACCOUNTS + 1; i++) {
								ops.add(cryptoTransfer(movingUnique("nft", i)
										.between(TOKEN_TREASURY, "owner"))
										.logged());
							}
							allRunFor(spec, ops);
						})
				).then(
						defaultLoadTest(allowanceBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}


