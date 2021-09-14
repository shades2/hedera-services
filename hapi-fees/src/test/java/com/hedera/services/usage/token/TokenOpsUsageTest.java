package com.hedera.services.usage.token;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hedera.services.test.IdUtils;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.test.AdapterUtils.feeDataFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenOpsUsageTest {
	private static final TokenOpsUsage subject = new TokenOpsUsage();

	@Test
	void knowsBytesNeededToReprCustomFeeSchedule() {
		final var expectedHbarFixed = FeeBuilder.LONG_SIZE + FeeBuilder.BASIC_ENTITY_ID_SIZE;
		final var expectedHtsFixed = FeeBuilder.LONG_SIZE + 2 * FeeBuilder.BASIC_ENTITY_ID_SIZE;
		final var expectedFractional = 4 * FeeBuilder.LONG_SIZE + FeeBuilder.BASIC_ENTITY_ID_SIZE;
		final var expectedRoyaltyNoFallback = 2 * FeeBuilder.LONG_SIZE + FeeBuilder.BASIC_ENTITY_ID_SIZE;
		final var expectedRoyaltyHtsFallback = 3 * FeeBuilder.LONG_SIZE + 2 * FeeBuilder.BASIC_ENTITY_ID_SIZE;
		final var expectedRoyaltyHbarFallback = 3 * FeeBuilder.LONG_SIZE + FeeBuilder.BASIC_ENTITY_ID_SIZE;

		final var perHbarFixedFee = subject.bytesNeededToRepr(1, 0, 0, 0, 0, 0);
		final var perHtsFixedFee = subject.bytesNeededToRepr(0, 1, 0, 0, 0, 0);
		final var perFracFee = subject.bytesNeededToRepr(0, 0, 1, 0, 0, 0);
		final var perRoyaltyNoFallbackFee = subject.bytesNeededToRepr(0, 0, 0, 1, 0, 0);
		final var perRoyaltyHtsFallbackFee = subject.bytesNeededToRepr(0, 0, 0, 0, 1, 0);
		final var perRoyaltyHbarFallbackFee = subject.bytesNeededToRepr(0, 0, 0, 0, 0, 1);
		final var oneOfEach = subject.bytesNeededToRepr(1, 1, 1, 1, 1, 1);

		assertEquals(expectedHbarFixed, perHbarFixedFee);
		assertEquals(expectedHtsFixed, perHtsFixedFee);
		assertEquals(expectedFractional, perFracFee);
		assertEquals(expectedRoyaltyNoFallback, perRoyaltyNoFallbackFee);
		assertEquals(expectedRoyaltyHtsFallback, perRoyaltyHtsFallbackFee);
		assertEquals(expectedRoyaltyHbarFallback, perRoyaltyHbarFallbackFee);
		assertEquals(
				expectedHbarFixed
						+ expectedHtsFixed
						+ expectedFractional
						+ expectedRoyaltyNoFallback
						+ expectedRoyaltyHtsFallback
						+ expectedRoyaltyHbarFallback,
				oneOfEach);
	}

	@Test
	void canCountFeeTypes() {
		final List<CustomFee> aSchedule = new ArrayList<>();
		aSchedule.add(CustomFee.newBuilder().setFixedFee(FixedFee.getDefaultInstance()).build());
		aSchedule.add(CustomFee.newBuilder().setFixedFee(FixedFee.newBuilder()
						.setDenominatingTokenId(IdUtils.asToken("1.2.3")))
				.build());
		aSchedule.add(CustomFee.newBuilder().setFixedFee(FixedFee.newBuilder()
						.setDenominatingTokenId(IdUtils.asToken("1.2.3")))
				.build());
		aSchedule.add(CustomFee.newBuilder().setFractionalFee(FractionalFee.getDefaultInstance()).build());
		aSchedule.add(CustomFee.newBuilder().setFractionalFee(FractionalFee.getDefaultInstance()).build());
		aSchedule.add(CustomFee.newBuilder().setFractionalFee(FractionalFee.getDefaultInstance()).build());
		aSchedule.add(CustomFee.newBuilder().setRoyaltyFee(RoyaltyFee.getDefaultInstance()).build());
		aSchedule.add(CustomFee.newBuilder().setRoyaltyFee(RoyaltyFee.newBuilder()
				.setFallbackFee(FixedFee.newBuilder().build())).build());
		aSchedule.add(CustomFee.newBuilder().setRoyaltyFee(RoyaltyFee.newBuilder()
				.setFallbackFee(FixedFee.newBuilder().setDenominatingTokenId(IdUtils.asToken("1.2.3"))
						.build())).build());

		final var expected = subject.bytesNeededToRepr(1, 2, 3, 1, 1, 1);

		final var actual = subject.bytesNeededToRepr(aSchedule);

		assertEquals(expected, actual);
	}

	@Test
	void accumulatesBptAndRbhAsExpected() {
		final var now = 1_234_567L;
		final var lifetime = 7776000L;
		final var expiry = now + lifetime;
		final var curSize = subject.bytesNeededToRepr(1, 0, 1, 1, 0, 1);
		final var newSize = subject.bytesNeededToRepr(2, 1, 0, 2, 1, 0);
		final var ctx = new ExtantFeeScheduleContext(expiry, curSize);
		final var opMeta = new FeeScheduleUpdateMeta(now, newSize);
		final var sigUsage = new SigUsage(1, 2, 3);
		final var baseMeta = new BaseTransactionMeta(50, 0);
		final var exp = new UsageAccumulator();
		exp.resetForTransaction(baseMeta, sigUsage);
		exp.addBpt(newSize + FeeBuilder.BASIC_ENTITY_ID_SIZE);
		exp.addRbs((newSize - curSize) * lifetime);

		final var ans = new UsageAccumulator();

		subject.feeScheduleUpdateUsage(sigUsage, baseMeta, opMeta, ctx, ans);

		assertEquals(feeDataFrom(exp), feeDataFrom(ans));
	}
}
