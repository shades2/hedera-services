package com.hedera.services.state.merkle.internals;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.util.Objects;

public class ChunkPath implements SelfSerializable {
	private static final long CLASS_ID = 0x999ea888f530a7ddL;
	private static final int CURRENT_VERSION = 1;

	private String loc;

	public ChunkPath() {
	}

	public ChunkPath(String loc) {
		this.loc = loc;
	}

	public String getLoc() {
		return loc;
	}

	@Override
	public int hashCode() {
		return loc == null ? 0 : loc.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || ChunkPath.class != o.getClass()) {
			return false;
		}
		final var that = (ChunkPath) o;
		return Objects.equals(this.loc, that.loc);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		loc = in.readNormalisedString(Integer.MAX_VALUE);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeNormalisedString(loc);
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}
}
