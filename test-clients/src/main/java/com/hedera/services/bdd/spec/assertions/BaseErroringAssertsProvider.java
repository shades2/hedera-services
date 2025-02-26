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
package com.hedera.services.bdd.spec.assertions;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.isIdLiteral;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;

public class BaseErroringAssertsProvider<T> implements ErroringAssertsProvider<T> {
    List<Function<HapiApiSpec, Function<T, Optional<Throwable>>>> testProviders = new ArrayList<>();

    protected void registerProvider(AssertUtils.ThrowingAssert throwing) {
        testProviders.add(
                spec ->
                        instance -> {
                            try {
                                throwing.assertThrowable(spec, instance);
                            } catch (Throwable t) {
                                return Optional.of(t);
                            }
                            return Optional.empty();
                        });
    }

    /* Helper for asserting something about a ContractID, FileID, AccountID, etc. */
    @SuppressWarnings("unchecked")
    protected <R> void registerIdLookupAssert(
            String key, Function<T, R> getActual, Class<R> cls, String err) {
        registerProvider(
                (spec, o) -> {
                    R expected =
                            isIdLiteral(key)
                                    ? parseIdByType(key, cls)
                                    : spec.registry().getId(key, cls);
                    R actual = getActual.apply((T) o);
                    Assertions.assertEquals(expected, actual, err);
                });
    }

    @SuppressWarnings("unchecked")
    private static <R> R parseIdByType(final String literal, Class<R> cls) {
        if (cls.equals(AccountID.class)) {
            return (R) HapiPropertySource.asAccount(literal);
        } else if (cls.equals(ContractID.class)) {
            return (R) HapiPropertySource.asContract(literal);
        } else if (cls.equals(TokenID.class)) {
            return (R) HapiPropertySource.asToken(literal);
        } else if (cls.equals(FileID.class)) {
            return (R) HapiPropertySource.asFile(literal);
        } else if (cls.equals(TopicID.class)) {
            return (R) HapiPropertySource.asTopic(literal);
        } else {
            throw new IllegalArgumentException("Cannot parse an id of type " + cls.getSimpleName());
        }
    }

    @Override
    public ErroringAsserts<T> assertsFor(HapiApiSpec spec) {
        return new BaseErroringAsserts<>(
                testProviders.stream().map(p -> p.apply(spec)).collect(Collectors.toList()));
    }
}
