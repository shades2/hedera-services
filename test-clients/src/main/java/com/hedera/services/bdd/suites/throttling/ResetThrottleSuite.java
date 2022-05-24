package com.hedera.services.bdd.suites.throttling;

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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.PropertySource.asAccount;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;

public final class ResetThrottleSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ResetThrottleSuite.class);

	public static void main(String... args) {
		new ResetThrottleSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				resetThrottle()
		);
	}

	private HapiApiSpec resetThrottle() {
		final var devThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
		return defaultHapiSpec("ResetThrottle")
				.given(
				).when(
						// only allow the first client to update throttle file with the first node
						withOpContext((spec, opLog) -> {
							HapiSpecOperation subOp;
							if (spec.setup().defaultNode().equals(asAccount("0.0.3"))) {
								subOp = fileUpdate(THROTTLE_DEFS)
										.payingWith(GENESIS)
										.contents(devThrottles.toByteArray());
							} else {
								subOp = sleepFor(20000);
							}
							allRunFor(spec, subOp);
						})
				).then(
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}