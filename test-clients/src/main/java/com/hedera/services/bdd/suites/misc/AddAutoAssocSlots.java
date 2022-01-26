package com.hedera.services.bdd.suites.misc;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;

public class AddAutoAssocSlots extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AddAutoAssocSlots.class);

	public static void main(String... args) throws Exception {
		new AddAutoAssocSlots().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						addAutoAssocSlots(),
				}
		);
	}

	public HapiApiSpec addAutoAssocSlots() {
		/**
		 * We need to set the following variables:
		 *   - The payer account
		 *   - The location of the PEM file relative to the test-clients/ directory
		 *   - The passphrase for the PEM file
		 *   - The nodes to target (include node account 0.0.3)
		 *   - The number of auto-associations to create
		 *   - The fee to offer
		 *
		 *  For each variable, there are three lines below:
		 *    - The first line is for local testing
		 *    - The second line is for testnet
		 *    - The third line is for mainnet (all values TBD)
		 */

//		final var payerAccount = "0.0.2";
//		final var payerAccount = "0.0.50";
		final var payerAccount = "<TBD>";

//		final var pemLoc = "devGenesisKeypair.pem";
//		final var pemLoc = "stabletestnet-account50.pem";
		final var pemLoc = "<TBD>";

//		final var pemPassphrase = "passphrase";
//		final var pemPassphrase = "<SECRET>";
		final var pemPassphrase = "<TBD>";

		/* LOCAL */
//		final var nodes = "localhost";
		/* TESTNET */
//		final var nodes = "34.94.106.61:0.0.3";
		/* MAINNET */
		final var nodes = "35.237.200.180:0.0.3";

		final var newAutoAssociations = 5;
		final var feeInTinyBars = 10 * ONE_HBAR;

		return customHapiSpec("AddAutoAssocSlots")
				.withProperties(Map.of(
						"nodes", nodes,
						"default.node", "0.0.3",
						"fees.useFixedOffer=true",
						"fees.fixedOffer=" + feeInTinyBars,
						"default.payer", payerAccount,
						"default.payer.pemKeyLoc", pemLoc,
						"default.payer.pemKeyPassphrase", pemPassphrase
				)).given(
						getAccountInfo(DEFAULT_PAYER).logged()
				).when(
						cryptoUpdate(DEFAULT_PAYER)
								.blankMemo()
								.maxAutomaticAssociations(newAutoAssociations)
				).then(
						getAccountInfo(DEFAULT_PAYER).logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
