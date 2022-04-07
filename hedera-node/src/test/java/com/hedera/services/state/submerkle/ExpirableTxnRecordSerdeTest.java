package com.hedera.services.state.submerkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.serde.SerializedForms;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;

import java.util.Collections;

import static com.hedera.services.state.submerkle.ExpirableTxnRecord.RELEASE_0230_VERSION;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.RELEASE_0250_VERSION;

public class ExpirableTxnRecordSerdeTest extends SelfSerializableDataTest<ExpirableTxnRecord> {
	public static final int NUM_TEST_CASES = 4 * MIN_TEST_CASES_PER_VERSION;

	@Override
	protected Class<ExpirableTxnRecord> getType() {
		return ExpirableTxnRecord.class;
	}

	@Override
	protected void registerConstructables() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(TxnId.class, TxnId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(TxnReceipt.class, TxnReceipt::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FcTokenAssociation.class, FcTokenAssociation::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FcAssessedCustomFee.class, FcAssessedCustomFee::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(NftAdjustments.class, NftAdjustments::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(CurrencyAdjustments.class, CurrencyAdjustments::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EvmFnResult.class, EvmFnResult::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FcTokenAllowance.class, FcTokenAllowance::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FcTokenAllowanceId.class, FcTokenAllowanceId::new));
	}

	@Override
	protected int getNumTestCasesFor(int version) {
		return version == RELEASE_0230_VERSION ? MIN_TEST_CASES_PER_VERSION : NUM_TEST_CASES;
	}

	@Override
	protected byte[] getSerializedForm(final int version, final int testCaseNo) {
		return SerializedForms.loadForm(ExpirableTxnRecord.class, version, testCaseNo);
	}

	@Override
	protected ExpirableTxnRecord getExpectedObject(final int version, final int testCaseNo) {
		final var seeded = SeededPropertySource.forSerdeTest(version, testCaseNo).nextRecord();
		if (version == RELEASE_0230_VERSION) {
			// Always empty before release 0.25
			seeded.setCryptoAllowances(Collections.emptyMap());
			seeded.setFungibleTokenAllowances(Collections.emptyMap());
			return seeded;
		} else if (version == RELEASE_0250_VERSION) {
			return seeded;
		} else {
			throw new IllegalArgumentException("Version " + version + " isn't actually supported");
		}
	}

	@Override
	protected ExpirableTxnRecord getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextRecord();
	}
}
