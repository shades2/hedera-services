/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TogglePayerRecordUse extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(TogglePayerRecordUse.class);

    public static void main(String... args) throws Exception {
        new TogglePayerRecordUse().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
                    changePayerRecordStateStorage(),
                });
    }

    private HapiApiSpec changePayerRecordStateStorage() {
        final String NEW_VALUE = "false";

        return defaultHapiSpec("ChangePayerRecordStateStorage")
                .given()
                .when()
                .then(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("ledger.createPayerRecords", NEW_VALUE)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
