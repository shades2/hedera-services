package com.hedera.services.utils.types;

import com.hederahashgraph.api.proto.java.ScheduleID;

/**
 * The base implementation of a TxnAccessor; every transaction type
 * will have a sub-class of this
 */
public class BaseTxnAccessor implements TxnAccessor {
	private boolean isTriggered;
	private ScheduleID scheduleRef;

	protected BaseTxnAccessor() {
		/* Not meant to instantiate directly, this class only provides implementation support for subclasses */
	}

	private void setTriggered(boolean triggered) {
		isTriggered = triggered;
	}

	private void setScheduleRef(ScheduleID scheduleRef) {
		this.scheduleRef = scheduleRef;
	}

// final AliasManager aliasManager

	// protected AccountID resolve(final ByteString alias) { ...

	public static TxnAccessor nonTriggeredTxn(byte[] signedTxnWrapperBytes) {
		BaseTxnAccessor subtype = constructSpecializedAccessor(signedTxnWrapperBytes);
		subtype.setTriggered(false);
		subtype.setScheduleRef(null);
		return subtype;
	}

	public static TxnAccessor triggeredTxn(byte[] signedTxnWrapperBytes, ScheduleID parent) {
		BaseTxnAccessor subtype = constructSpecializedAccessor(signedTxnWrapperBytes);
		subtype.setTriggered(true);
		subtype.setScheduleRef(parent);
		return subtype;
	}

	private static BaseTxnAccessor constructSpecializedAccessor(byte[] signedTxnWrapperBytes) {
		/*
		parse the signedTxnWrapperBytes, figure out what specialized implementation to use
		construct the subtype instance
		 */
	}


	@Override
	public boolean isTriggeredTxn() {
		return false;
	}

	@Override
	public ScheduleID getScheduleRef() {
		return null;
	}
}
