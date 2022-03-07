package com.hedera.services.utils.types;

import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.function.Function;

/**
 * A {@link TxnAccessor} that includes access to all the context of a gRPC
 * transaction submitted by a user, via HAPI and then reaching consensus
 * in the platform...
 *
 */
public class UserTxnAccessor implements TxnAccessor {
	private final TxnAccessor delegate;
	private final SwirldTransaction swirldTransaction;

	public UserTxnAccessor(TxnAccessor delegate, SwirldTransaction swirldTransaction) {
		this.delegate = delegate;
		this.swirldTransaction = swirldTransaction;
	}

	public static UserTxnAccessor from(SwirldTransaction platformTxn, AliasManager aliasManager) {
		delegate = BaseTxnAccessor.from(platformTxn.getContentsDirect(), aliasManager);
		throw new AssertionError("Not implemented");
	}

	@Override
	public boolean isTriggeredTxn() {
		return delegate.isTriggeredTxn();
	}
	@Override
	public ScheduleID getScheduleRef() {
		return delegate.getScheduleRef();
	}
	int sigMapSize() {
		throw new AssertionError("Not implemented");
	}
	int numSigPairs() {
		throw new AssertionError("Not implemented");
	}
	SignatureMap getSigMap() {
		throw new AssertionError("Not implemented");
	}
	void setExpandedSigStatus(ResponseCodeEnum status) {
		throw new AssertionError("Not implemented");
	}
	ResponseCodeEnum getExpandedSigStatus() {
		throw new AssertionError("Not implemented");
	}
	PubKeyToSigBytes getPkToSigsFn() {
		throw new AssertionError("Not implemented");
	}
	void setSigMeta(RationalizedSigMeta sigMeta) {
		throw new AssertionError("Not implemented");
	}
	RationalizedSigMeta getSigMeta() {
		throw new AssertionError("Not implemented");
	}
	Function<byte[], TransactionSignature> getRationalizedPkToCryptoSigFn() {
		throw new AssertionError("Not implemented");
	}
	SwirldTransaction getPlatformTxn() {
		throw new AssertionError("Not implemented");
	}

}
