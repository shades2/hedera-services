package com.hedera.services.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Transaction;

import javax.inject.Inject;

import static com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody;
import static com.hedera.services.utils.MiscUtils.functionExtractor;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;

public class AccessorFactory {
	final AliasManager aliasManager;

	@Inject
	public AccessorFactory(final AliasManager aliasManager) {
		this.aliasManager = aliasManager;
	}

	public TxnAccessor nonTriggeredTxn(byte[] signedTxnWrapperBytes) throws InvalidProtocolBufferException {
		final var subtype = constructSpecializedAccessor(signedTxnWrapperBytes);
		subtype.setTriggered(false);
		subtype.setScheduleRef(null);
		return subtype;
	}

	public TxnAccessor triggeredTxn(byte[] signedTxnWrapperBytes, final AccountID payer,
			ScheduleID parent) throws InvalidProtocolBufferException {
		final var subtype = constructSpecializedAccessor(signedTxnWrapperBytes);
		subtype.setTriggered(true);
		subtype.setScheduleRef(parent);
		subtype.setPayer(payer);
		return subtype;
	}

	/**
	 * parse the signedTxnWrapperBytes, figure out what specialized implementation to use
	 * construct the subtype instance
	 *
	 * @param signedTxnWrapperBytes
	 * @return
	 * @throws InvalidProtocolBufferException
	 */
	private TxnAccessor constructSpecializedAccessor(byte[] signedTxnWrapperBytes)
			throws InvalidProtocolBufferException {
		final var body = extractTransactionBody(Transaction.parseFrom(signedTxnWrapperBytes));
		final var function = functionExtractor.apply(body);
		if (function == TokenAccountWipe) {
			return new TokenWipeAccessor(signedTxnWrapperBytes, aliasManager);
		}
		return new BaseTxnAccessor(signedTxnWrapperBytes, aliasManager);
	}
}
