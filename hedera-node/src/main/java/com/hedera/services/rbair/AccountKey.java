/*
 * (c) 2016-2021 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.hedera.services.rbair;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AccountKey implements VirtualKey {
	private static final long CLASS_ID = 1440746202302991749L;

	private long realmID;
	private long shardId;
	private long accountID;

	public AccountKey() {
		this(0, 0, 0);
	}

	public AccountKey(final long realmID, final long shardId, final long accountID) {
		this.realmID = realmID;
		this.shardId = shardId;
		this.accountID = accountID;
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(realmID);
		out.writeLong(shardId);
		out.writeLong(accountID);
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		this.realmID = in.readLong();
		this.shardId = in.readLong();
		this.accountID = in.readLong();
	}

	@Override
	public void serialize(final ByteBuffer buffer) throws IOException {
		buffer.putLong(realmID);
		buffer.putLong(shardId);
		buffer.putLong(accountID);
	}

	@Override
	public void deserialize(final ByteBuffer buffer, final int version) throws IOException {
		this.realmID = buffer.getLong();
		this.shardId = buffer.getLong();
		this.accountID = buffer.getLong();
	}

	@Override
	public boolean equals(final ByteBuffer buffer, final int version) throws IOException {
		if (version != getVersion()) {
			return false;
		}

		return realmID == buffer.getLong() &&
				shardId == buffer.getLong() &&
				accountID == buffer.getLong();
	}

	@Override
	public String toString() {
		return "AccountVirtualMapKey{" +
				"realmID=" + realmID +
				", shardId=" + shardId +
				", accountID=" + accountID +
				'}';
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) return false;

		final AccountKey that = (AccountKey) o;

		return new EqualsBuilder()
				.append(realmID, that.realmID)
				.append(shardId, that.shardId)
				.append(accountID, that.accountID)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(realmID)
				.append(shardId)
				.append(accountID)
				.toHashCode();
	}

	public static int getSizeInBytes() {
		return 24;
	}
}
