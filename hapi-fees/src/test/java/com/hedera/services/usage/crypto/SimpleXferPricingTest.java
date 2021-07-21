package com.hedera.services.usage.crypto;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import org.junit.jupiter.api.Test;

class SimpleXferPricingTest {
	private final ExchangeRate mockRate = ExchangeRate.newBuilder().setHbarEquiv(1).setCentEquiv(12).build();

	final SimpleXferPricing subject = new SimpleXferPricing(mockRate);

	@Test
	void testSomeHbarOnlyPrices() {
		System.out.println("With no memo, ℏ only: " + subject.feeInTbForHbarOnly(0));
		System.out.println("With 100-byte memo, ℏ only: " + subject.feeInTbForHbarOnly(100));
	}

	@Test
	void testSomeTokenOnlyPrices() {
		System.out.println("With no memo, token only: " + subject.feeInTbForTokenOnly(0));
		System.out.println("With 100-byte memo, token only: " + subject.feeInTbForTokenOnly(100));
	}
}