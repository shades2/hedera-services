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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class AuctionMonitorSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AuctionMonitorSuite.class);

	private final Map<String, String> specConfig;
	private static long balanceThreshold = 5;
	private static long rechargeAmount = 10;
	private static String[] accounts = new String[]{
			"0.0.1001",
			"0.0.1002",
			"0.0.1003",
			"0.0.1004",
			"0.0.1005",
			"0.0.1006",
			"0.0.1007",
	};

	private static String accountsFile = "accounts.json";

	public static void main(String[] args) {
//		if(args.length > 0) {
//			accounts = args[0];
//		}

//		log.info("Proceed with the following parameters: {} {} {}", accounts, balanceThreshold, rechargeAmount);

//		accountsMap = loadAccountsAndKeys(accountsFile);

		new AuctionMonitorSuite(Map.of()).runSuiteSync();
	}

	public AuctionMonitorSuite(final Map<String, String> specConfig) {
		this.specConfig = specConfig;
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(createTestAccounts());
//		return checkAllAccounts();
	}

	private List<HapiApiSpec> checkAllAccounts() {
		List<HapiApiSpec> specToRun = new ArrayList<>();
		Arrays.stream(accounts).forEach(s -> specToRun.add(checkAndFund(s)));
		return specToRun;
	}

	private HapiApiSpec createTestAccounts() {
		return HapiApiSpec.defaultHapiSpec("CheckAndFund")
				.given(
				).when(
						cryptoCreate("acct1").balance(100 * ONE_HBAR),
						cryptoCreate("acct2").balance(2 * ONE_HBAR),
						cryptoCreate("acct3").balance(50 * ONE_HBAR),
						cryptoCreate("acct4").balance(ONE_HBAR),
						cryptoCreate("acct5").balance(ONE_HBAR),
						cryptoCreate("acct6").balance(2 * ONE_HBAR),
						cryptoCreate("acct7").balance(200 * ONE_HBAR)
				).then();
	}

	private HapiApiSpec checkAndFund(String account) {
//		String curKey = "tesTKey";
		return HapiApiSpec.customHapiSpec(("CheckAndFund"))
				.withProperties(
						specConfig
//						"nodes", "35.237.200.180:0.0.3"
//						"default.payer", "0.0.950",
//						"default.payer.pemKeyLoc", "mainnet-account950.pem"
//						"default.payer.pemKeyPassphrase", "<asdasdada>"
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
									< balanceThreshold * ONE_HBAR) {
								// sign with appropriate key if receiver sig required
									ctxLog.info("Account " + account + " depleted its funds below " + balanceThreshold + " Hbar");
								var fundAccountOp =
										cryptoTransfer(tinyBarsFromTo(GENESIS, account, rechargeAmount * ONE_HBAR))
												.payingWith(GENESIS)
												.signedBy(GENESIS)
												.logged();
								allRunFor(spec, fundAccountOp);
								if(fundAccountOp.getLastReceipt().getStatus() != ResponseCodeEnum.SUCCESS) {
									ctxLog.warn("Account transfer failed");
								} else {
									ctxLog.info("Account " + account + " has been funded with " + rechargeAmount + " Hbar");
								}
							}
						})
				)
				.then(
						getAccountInfo(account).logged()
				);
	}

//	private static Map<String, String> loadAccountsAndKeys(final String accountFile) {
//		try (InputStream fin = AuctionMonitorSuite.class.getClassLoader().getResourceAsStream(accountFile)) {
//			final ObjectMapper reader = new ObjectMapper();
//			final Map<String, String> accounts = reader.readValue(fin, Map.class);
//			return accounts;
//		} catch (IOException e) {
//			log.error("Can't read accounts file {}", accountFile,  e);
//		}
//		return Map.of();
//	}
}
