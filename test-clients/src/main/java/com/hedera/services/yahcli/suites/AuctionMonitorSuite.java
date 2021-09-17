package com.hedera.services.yahcli.suites;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromLiteral;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class AuctionMonitorSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AuctionMonitorSuite.class);

	private static long balanceThreshold = ONE_HBAR * 5;
	private static long rechargeAmount = ONE_HBAR  * 10;
	private static Map<String, String> accounts;

	private static String accountsFile = "accounts.json";

	public static void main(String[] args) {
		if(args.length > 0) {
			accountsFile = args[0];
			if(args.length > 1) {
				balanceThreshold = Long.parseLong(args[1]);
				if(args.length > 2) {
					rechargeAmount = Long.parseLong(args[2]);
				}
			}
		}

		log.info("Proceed with the following parameters: {} {} {}", accountsFile, balanceThreshold, rechargeAmount);

		accounts = loadAccountsAndKeys(accountsFile);

		new AuctionMonitorSuite().runSuiteSync();
	}

	public AuctionMonitorSuite() {
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		//return List.of(createTestAccounts());
		return checkAllAccounts();
	}

	private List<HapiApiSpec> checkAllAccounts() {
		List<HapiApiSpec> specToRun = new ArrayList<>();
		accounts.entrySet().stream().forEach(s -> specToRun.add(checkAndFund(s.getKey(), s.getValue())));
		return specToRun;
	}

	private HapiApiSpec createTestAccounts() {
		return HapiApiSpec.defaultHapiSpec("CheckAndFund")
				.given(
				).when(
						cryptoCreate("acct1").balance(100 * ONE_HBAR),
						cryptoCreate("acct2").balance(2 * ONE_HBAR),
						cryptoCreate("acct3").balance(50 * ONE_HBAR),
						cryptoCreate("acct4").balance(1 * ONE_HBAR),
						cryptoCreate("acct5").balance(1 * ONE_HBAR),
						cryptoCreate("acct6").balance(2 * ONE_HBAR),
						cryptoCreate("acct7").balance(200 * ONE_HBAR)
						cryptoCreate("acct8").balance(3 * ONE_HBAR)
				).then();
	}

	private HapiApiSpec checkAndFund(String account, String keyEncoded) {
		String curKey = "tesTKey";
		return HapiApiSpec.customHapiSpec(("CheckAndFund"))
				.withProperties(
						//"default.payer.pemKeyLoc", "mainnet-account950.pem"
				)
				.given(
						// Load keys
						//keyFromLiteral(curKey, keyEncoded)
				).when(
						withOpContext((spec, ctxLog) -> {
							// sign with appropriate key
							var checkBalanceOp = getAccountInfo(account).logged();
							allRunFor(spec, checkBalanceOp);
							if (checkBalanceOp.getResponse().getCryptoGetInfo().getAccountInfo().getBalance()
									< balanceThreshold) {
								// sign with appropriate key if receiver sig required
								var fundAccountOp =
										cryptoTransfer(tinyBarsFromTo(GENESIS, account, rechargeAmount))
												.payingWith(GENESIS)
												.signedBy(GENESIS)
												.logged();
								allRunFor(spec, fundAccountOp);
								if(fundAccountOp.getLastReceipt().getStatus() != ResponseCodeEnum.SUCCESS) {
									ctxLog.warn("Account transfer failed");
								}
							}
						})
				)
				.then(
						getAccountInfo(account).logged()
				);
	}

	private static Map<String, String> loadAccountsAndKeys(final String accountFile) {
		try (InputStream fin = AuctionMonitorSuite.class.getClassLoader().getResourceAsStream(accountFile)) {
			final ObjectMapper reader = new ObjectMapper();
			final Map<String, String> accounts = reader.readValue(fin, Map.class);
			return accounts;
		} catch (IOException e) {
			log.error("Can't read accounts file {}", accountFile,  e);
		}
		return Map.of();
	}
}
