package com.hedera.services.usage.crypto;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;

public class SimpleXferPricing {
	private static final int SIG_COUNT = 1;
	private static final int SIG_MAP_SIZE = 71;
	private static final int NUM_PAYER_ED25519_KEYS = 1;
	private static final long TOKEN_ID_SIZE = 24;
	private static final long RECORD_LIFETIME = 180;
	private static final long BASIC_TXN_BODY_SIZE = 76;
	private static final long BASIC_TXN_RECORD_SIZE = 132;
	private static final long BALANCE_ADJUSTMENT_SIZE = 32;
	private static final long TOKEN_TRANSFER_MULTIPLIER = 380;

	private final FeeData hbarOnlyPrices;
	private final FeeData tokenOnlyPrices;
	private final ExchangeRate activeRate;

	public SimpleXferPricing(ExchangeRate activeRate) {
		this(R0160_HBAR_ONLY_PRICES, R0160_TOKEN_ONLY_PRICES, activeRate);
	}

	public SimpleXferPricing(FeeData hbarOnlyPrices, FeeData tokenOnlyPrices, ExchangeRate activeRate) {
		this.tokenOnlyPrices = tokenOnlyPrices;
		this.hbarOnlyPrices = hbarOnlyPrices;
		this.activeRate = activeRate;
	}

	public long feeInTbForHbarOnly(int memoLength)	{
		return tbFeeGiven(hbarOnlyPrices, bptForHbarOnly(memoLength), rbhForHbarOnly(memoLength));
	}

	public long feeInTbForTokenOnly(int memoLength)	{
		return tbFeeGiven(tokenOnlyPrices, bptForTokenOnly(memoLength), rbhForTokenOnly(memoLength));
	}

	private long tbFeeGiven(FeeData prices, long bpt, long rbh) {
		final long tinyCentFee = networkFeeInTinycents(prices.getNetworkdata(), bpt)
				+ nodeFeeInTinycents(prices.getNodedata(), bpt)
				+ serviceFeeInTinycents(prices.getServicedata(), rbh);
		return (tinyCentFee / 1000) * activeRate.getHbarEquiv() / activeRate.getCentEquiv();
	}

	private long networkFeeInTinycents(FeeComponents networkPrices, long bpt) {
		return networkPrices.getConstant()
				+ SIG_COUNT * networkPrices.getVpt()
				+ bpt * networkPrices.getBpt()
				+ networkPrices.getRbh();
	}

	private long nodeFeeInTinycents(FeeComponents nodePrices, long bpt) {
		return nodePrices.getConstant()
				+ NUM_PAYER_ED25519_KEYS * nodePrices.getVpt()
				+ bpt * nodePrices.getBpt()
				+ 4 * nodePrices.getBpr();
	}

	private long serviceFeeInTinycents(FeeComponents servicePrices, long rbh) {
		return servicePrices.getConstant() + rbh * servicePrices.getRbh();
	}

	private long bptForHbarOnly(int memoLength) {
		return BASIC_TXN_BODY_SIZE + SIG_MAP_SIZE + memoLength
				+ 2 * BALANCE_ADJUSTMENT_SIZE;
	}

	private long bptForTokenOnly(int memoLength) {
		return BASIC_TXN_BODY_SIZE + SIG_MAP_SIZE + memoLength
				+ (2 * BALANCE_ADJUSTMENT_SIZE + TOKEN_ID_SIZE) * TOKEN_TRANSFER_MULTIPLIER;
	}

	private long rbhForHbarOnly(int memoLength) {
		final long rb = BASIC_TXN_RECORD_SIZE + memoLength
				+ 4 * BALANCE_ADJUSTMENT_SIZE;
		return nonDegenerateDiv(RECORD_LIFETIME * rb, 3600);
	}

	private long rbhForTokenOnly(int memoLength) {
		final long rb = BASIC_TXN_RECORD_SIZE + memoLength
				+ (2 * BALANCE_ADJUSTMENT_SIZE + TOKEN_ID_SIZE) * TOKEN_TRANSFER_MULTIPLIER;
		return nonDegenerateDiv(RECORD_LIFETIME * rb, 3600);
	}

	private long nonDegenerateDiv(long dividend, int divisor) {
		return (dividend == 0) ? 0 : Math.max(1, dividend / divisor);
	}

	private static final FeeData R0160_HBAR_ONLY_PRICES = FeeData.newBuilder()
			.setNodedata(FeeComponents.newBuilder()
					.setConstant(7574478L)
					.setBpt(12109L)
					.setVpt(30273301L)
					.setBpr(12109L))
			.setNetworkdata(FeeComponents.newBuilder()
					.setConstant(151489557L)
					.setBpt(242186L)
					.setVpt(605466012L)
					.setRbh(161L))
			.setServicedata(FeeComponents.newBuilder()
					.setConstant(151489557L)
					.setRbh(161L))
			.build();

	private static final FeeData R0160_TOKEN_ONLY_PRICES = FeeData.newBuilder()
			.setNodedata(FeeComponents.newBuilder()
					.setConstant(7983519L)
					.setBpt(12763L)
					.setVpt(31908136L)
					.setBpr(12763L))
			.setNetworkdata(FeeComponents.newBuilder()
					.setConstant(159670382L)
					.setBpt(255265L)
					.setVpt(638162730L)
					.setRbh(170L))
			.setServicedata(FeeComponents.newBuilder()
					.setConstant(159670382L)
					.setRbh(170L))
			.build();
}
