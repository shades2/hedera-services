package com.hedera.services.bdd.suites.savedstate;

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
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.LoadTest.defaultLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

public class AccountCreation extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AccountCreation.class);

	public static final int CREATION_TPS = 10;
	public static final int CREATION_THREADS = 20;
	public static final int CREATION_MINS = 50;

	private static final AtomicInteger accountNumber = new AtomicInteger(1);

	public static void main(String... args) {
		new AccountCreation().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				createAccounts()
		);
	}

	private synchronized HapiSpecOperation generateCreateAccountOperation() {
		final var accNumber = accountNumber.getAndIncrement();
		return cryptoCreate("account" + accNumber)
				.balance(THOUSAND_HBAR)
				.payingWith(GENESIS)
				.key(GENESIS)
				.noLogging()
				.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
				.deferStatusResolution();
	}

	private HapiApiSpec createAccounts() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings(
				CREATION_TPS,
				CREATION_MINS,
				CREATION_THREADS);

		Supplier<HapiSpecOperation[]> createBurst = () -> new HapiSpecOperation[] {
				generateCreateAccountOperation()
		};

		return defaultHapiSpec("CreateAccounts")
				.given(
						logIt(ignore -> settings.toString())
				).when()
				.then(
						defaultLoadTest(createBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
