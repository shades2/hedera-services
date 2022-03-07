package com.hedera.services.utils.types;

import com.hederahashgraph.api.proto.java.ScheduleID;

/**
 * Provides convenient access to the universal parts of a gRPC transaction---
 * the memo, the payer...the "source" of the transaction, whether HAPI or
 * from a MerkleSchedule?
 *
 * IMPORTANT POINT: only these methods are needed to handle a transaction
 * after the point all its signatures have been verified.
 *
 * A triggered transaction only needs an implementation of this type.
 */
public interface TxnAccessor {
	boolean isTriggeredTxn();
	ScheduleID getScheduleRef();

	/*
	... lots of other methods, but nothing related to signatures!
	 */
}
