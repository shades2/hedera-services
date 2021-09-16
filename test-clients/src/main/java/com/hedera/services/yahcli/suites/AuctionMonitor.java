package com.hedera.services.yahcli.suites;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class AuctionMonitor extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AuctionMonitor.class);

	private final Map<String, String> specConfig;
	private final List<String> accounts;
	private final long balanceThreshold;
	private final long rechargeAmount = ONE_HUNDRED_HBARS / 10;

	public AuctionMonitor(final Map<String, String> specConfig, final String[] accounts, final long balanceThreshold) {
		this.specConfig = specConfig;
		this.accounts = rationalized(accounts);
		this.balanceThreshold = balanceThreshold;
	}

	private List<String> rationalized(final String[] accounts) {
		return Arrays.stream(accounts)
				.map(Utils::extractAccount)
				.collect(Collectors.toList());
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		List<HapiApiSpec> specToRun = new ArrayList<>();
		accounts.forEach(s -> specToRun.add(checkAndFund(s)));
		return specToRun;
	}

	private HapiApiSpec checkAndFund(String account) {
		return HapiApiSpec.customHapiSpec(("CheckAndFund"))
				.withProperties(specConfig)
				.given(
						// Load keys
				).when(

				)
				.then(
						withOpContext((spec, ctxLog) -> {
							// sign with appropriate key
							var checkBalanceOp = getAccountInfo(account);
							allRunFor(spec, checkBalanceOp);
							if (checkBalanceOp.getResponse().getCryptoGetInfo().getAccountInfo().getBalance()
									< balanceThreshold) {
								// sign with appropriate key if receiver sig required
								var fundAccountOp =
										cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, account, rechargeAmount));
								allRunFor(spec, fundAccountOp);
							}
						})
				);
	}
}
