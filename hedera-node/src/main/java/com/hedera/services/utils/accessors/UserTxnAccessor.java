package com.hedera.services.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.order.LinkedRefs;
import com.hedera.services.sigs.sourcing.PojoSigMapPubKeyToSigBytes;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.usage.crypto.CryptoTransferMeta;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.Map;
import java.util.function.Function;

/**
 * A {@link TxnAccessor} that includes access to all the context of a gRPC
 * transaction submitted by a user, via HAPI and then reaching consensus
 * in the platform.
 */
public class UserTxnAccessor implements TxnAccessor {
	private TxnAccessor delegate;
	private final SwirldTransaction swirldTransaction;

	private ResponseCodeEnum expandedSigStatus;
	private PubKeyToSigBytes pubKeyToSigBytes;
	private SubmitMessageMeta submitMessageMeta;
	private CryptoTransferMeta xferUsageMeta;
	private BaseTransactionMeta txnUsageMeta;
	private RationalizedSigMeta sigMeta = null;
	private SignatureMap sigMap;

	public UserTxnAccessor(final TxnAccessor delegate, final SwirldTransaction swirldTransaction)
			throws InvalidProtocolBufferException {
		this.delegate = delegate;
		this.swirldTransaction = swirldTransaction;

		final var signedTxnWrapper = delegate.getSignedTxnWrapper();
		final var signedTxnBytes = signedTxnWrapper.getSignedTransactionBytes();
		if (signedTxnBytes.isEmpty()) {
			sigMap = signedTxnWrapper.getSigMap();
		} else {
			final var signedTxn = SignedTransaction.parseFrom(signedTxnBytes);
			sigMap = signedTxn.getSigMap();
		}
		delegate.setSigMapSize(sigMap.getSerializedSize());
		delegate.setNumSigPairs(sigMap.getSigPairCount());
		pubKeyToSigBytes = new PojoSigMapPubKeyToSigBytes(sigMap);
	}

	public static UserTxnAccessor from(final SwirldTransaction platformTxn, final AliasManager aliasManager)
			throws InvalidProtocolBufferException {
		return new UserTxnAccessor(new BaseTxnAccessor(platformTxn.getContentsDirect(), () -> aliasManager),
				platformTxn);
	}

	@Override
	public boolean isTriggeredTxn() {
		return delegate.isTriggeredTxn();
	}

	@Override
	public ScheduleID getScheduleRef() {
		return delegate.getScheduleRef();
	}

	@Override
	public boolean canTriggerTxn() {
		return false;
	}

	@Override
	public long getOfferedFee() {
		return delegate.getOfferedFee();
	}

	@Override
	public AccountID getPayer() {
		return delegate.getPayer();
	}

	@Override
	public TransactionID getTxnId() {
		return delegate.getTxnId();
	}

	@Override
	public HederaFunctionality getFunction() {
		return delegate.getFunction();
	}

	@Override
	public SubType getSubType() {
		return delegate.getSubType();
	}

	@Override
	public byte[] getMemoUtf8Bytes() {
		return delegate.getMemoUtf8Bytes();
	}

	@Override
	public String getMemo() {
		return delegate.getMemo();
	}

	@Override
	public boolean memoHasZeroByte() {
		return delegate.memoHasZeroByte();
	}

	@Override
	public byte[] getHash() {
		return delegate.getHash();
	}

	@Override
	public byte[] getTxnBytes() {
		return delegate.getTxnBytes();
	}

	@Override
	public TransactionBody getTxn() {
		return delegate.getTxn();
	}

	@Override
	public void setNumAutoCreations(final int numAutoCreations) {
		delegate.setNumAutoCreations(numAutoCreations);
	}

	@Override
	public int getNumAutoCreations() {
		return delegate.getNumAutoCreations();
	}

	@Override
	public boolean areAutoCreationsCounted() {
		return delegate.areAutoCreationsCounted();
	}

	@Override
	public void countAutoCreationsWith(final AliasManager aliasManager) {
		delegate.countAutoCreationsWith(aliasManager);
	}

	@Override
	public void setLinkedRefs(final LinkedRefs linkedRefs) {
		delegate.setLinkedRefs(linkedRefs);
	}

	@Override
	public LinkedRefs getLinkedRefs() {
		return delegate.getLinkedRefs();
	}

	@Override
	public long getGasLimitForContractTx() {
		return delegate.getGasLimitForContractTx();
	}

	public SwirldTransaction getPlatformTxn() {
		return swirldTransaction;
	}

	@Override
	public Map<String, Object> getSpanMap() {
		return delegate.getSpanMap();
	}

	@Override
	public ExpandHandleSpanMapAccessor getSpanMapAccessor() {
		return delegate.getSpanMapAccessor();
	}

	@Override
	public void setTriggered(final boolean isTriggered) {
		delegate.setTriggered(isTriggered);
	}

	@Override
	public void setScheduleRef(final ScheduleID parent) {
		delegate.setScheduleRef(parent);
	}

	public int sigMapSize() {
		return delegate.sigMapSize();
	}

	public int numSigPairs() {
		return delegate.numSigPairs();
	}

	@Override
	public void setSigMapSize(final int sigMapSize) {
		delegate.setSigMapSize(sigMapSize);
	}

	@Override
	public void setNumSigPairs(final int numPairs) {
		delegate.setNumSigPairs(numPairs);
	}

	public SignatureMap getSigMap() {
		return sigMap;
	}

	public void setExpandedSigStatus(ResponseCodeEnum expandedSigStatus) {
		this.expandedSigStatus = expandedSigStatus;
	}

	public ResponseCodeEnum getExpandedSigStatus() {
		return expandedSigStatus;
	}

	public PubKeyToSigBytes getPkToSigsFn() {
		return pubKeyToSigBytes;
	}

	public void setSigMeta(RationalizedSigMeta sigMeta) {
		this.sigMeta = sigMeta;
	}

	public RationalizedSigMeta getSigMeta() {
		return sigMeta;
	}

	public Function<byte[], TransactionSignature> getRationalizedPkToCryptoSigFn() {
		final var sigMeta = getSigMeta();
		if (!sigMeta.couldRationalizeOthers()) {
			throw new IllegalStateException("Public-key-to-crypto-sig mapping is unusable after rationalization " +
					"failed");
		}
		return sigMeta.pkToVerifiedSigFn();
	}

	public byte[] getSignedTxnWrapperBytes() {
		return delegate.getSignedTxnWrapperBytes();
	}

	public Transaction getSignedTxnWrapper() {
		return delegate.getSignedTxnWrapper();
	}

	@Override
	public void setPayer(final AccountID payer) {
		delegate.setPayer(payer);
	}

	@Override
	public BaseTransactionMeta baseUsageMeta() {
		return delegate.baseUsageMeta();
	}
}
