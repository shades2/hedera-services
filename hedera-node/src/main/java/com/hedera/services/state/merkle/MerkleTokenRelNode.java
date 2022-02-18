package com.hedera.services.state.merkle;

import com.hedera.services.state.merkle.internals.AbstractMerkleMapValueListNode;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;

public class MerkleTokenRelNode extends AbstractMerkleMapValueListNode<EntityNumPair, MerkleTokenRelNode> {
	private static final int VERSION = 1;
	private static final long CLASS_ID = 0xb4042553a306d311L;

	private MerkleTokenRelStatus value;

	public MerkleTokenRelNode(MerkleTokenRelStatus value) {
		this.value = value;
	}

	public MerkleTokenRelNode(MerkleTokenRelNode that) {
		this.value = that.value;
	}

	public MerkleTokenRelStatus getValue() {
		return value;
	}

	@Override
	public void serializeValueTo(final SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(value, true);
	}

	@Override
	public void deserializeValueFrom(final SerializableDataInputStream in, final int version) throws IOException {
		value = in.readSerializable();
	}

	@Override
	public MerkleTokenRelNode newValueCopyOf(final MerkleTokenRelNode that) {
		return new MerkleTokenRelNode(that);
	}

	@Override
	public MerkleTokenRelNode self() {
		return this;
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return VERSION;
	}
}
