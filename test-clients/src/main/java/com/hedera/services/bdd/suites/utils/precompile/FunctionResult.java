package com.hedera.services.bdd.suites.utils.precompile;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import java.math.BigInteger;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class FunctionResult {
	private FunctionResult() {}

	private static final TupleType mintReturnType = TupleType.parse("(int32,uint64,int64[])");
	private static final TupleType burnReturnType = TupleType.parse("(int32,uint64)");

	public static FunctionResult functionResult() {
		return new FunctionResult();
	}

	public enum FunctionType {
		MINT, BURN
	}

	private FunctionType functionType;
	private TupleType tupleType;
	private ResponseCodeEnum status;
	private long totalSupply;
	private long[] serialNumbers;

	public FunctionResult forFunction(final FunctionType functionType) {
		if (functionType == FunctionType.MINT) {
			tupleType = mintReturnType;
		} else if (functionType == FunctionType.BURN) {
			tupleType = burnReturnType;
		}
		this.functionType = functionType;
		return this;
	}

	public FunctionResult withStatus(final ResponseCodeEnum status) {
		this.status = status;
		return this;
	}

	public FunctionResult withTotalSupply(final long totalSupply) {
		this.totalSupply = totalSupply;
		return this;
	}

	public FunctionResult withSerialNumbers(final long ... serialNumbers) {
		this.serialNumbers = serialNumbers;
		return this;
	}

	public Bytes getBytes() {
		Tuple result;
		if (functionType == FunctionType.MINT) {
			result = Tuple.of(status.getNumber(), BigInteger.valueOf(totalSupply), serialNumbers);
		} else if (functionType == FunctionType.BURN) {
			result = Tuple.of(status.getNumber(), BigInteger.valueOf(totalSupply));
		} else {
			return UInt256.valueOf(status.getNumber());
		}
		return Bytes.wrap(tupleType.encode(result).array());
	}
}
