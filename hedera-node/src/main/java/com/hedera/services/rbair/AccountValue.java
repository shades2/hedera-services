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
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class AccountValue implements VirtualValue {
	private static final long CLASS_ID = 7089145358000672700L;

	private long balance;
	private long sendThreshold;
	private long receiveThreshold;
	private boolean requireSignature;
	private long uid;

	public AccountValue() {
		this(0, 0, 0, false, 0);
	}

	public AccountValue(final long balance, final long sendThreshold, final long receiveThreshold,
						final boolean requireSignature, final long uid) {
		this.balance = balance;
		this.sendThreshold = sendThreshold;
		this.receiveThreshold = receiveThreshold;
		this.requireSignature = requireSignature;
		this.uid = uid;
	}

	public AccountValue(AccountValue accountValue) {
		this.balance = accountValue.balance;
		this.sendThreshold = accountValue.sendThreshold;
		this.receiveThreshold = accountValue.receiveThreshold;
		this.requireSignature = accountValue.requireSignature;
		this.uid = accountValue.uid;
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public void release() {
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(balance);
		out.writeLong(sendThreshold);
		out.writeLong(receiveThreshold);
		out.write(getRequireSignatureAsByte());
		out.writeLong(uid);
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		this.balance = in.readLong();
		this.sendThreshold = in.readLong();
		this.receiveThreshold = in.readLong();
		this.requireSignature = in.readByte() == 1;
		this.uid = in.readLong();
	}

	@Override
	public void serialize(final ByteBuffer buffer) throws IOException {
		buffer.putLong(balance);
		buffer.putLong(sendThreshold);
		buffer.putLong(receiveThreshold);
		buffer.put(getRequireSignatureAsByte());
		buffer.putLong(uid);
	}

	@Override
	public void deserialize(final ByteBuffer buffer, final int version) throws IOException {
		this.balance = buffer.getLong();
		this.sendThreshold = buffer.getLong();
		this.receiveThreshold = buffer.getLong();
		this.requireSignature = buffer.get() == 1;
		this.uid = buffer.getLong();
	}

	@Override
	public VirtualValue copy() {
		return new AccountValue(this);
	}

	@Override
	public VirtualValue asReadOnly() {
		return new AccountValue(this);
	}

	@Override
	public String toString() {
		return "AccountVirtualMapValue{" +
				"balance=" + balance +
				", sendThreshold=" + sendThreshold +
				", receiveThreshold=" + receiveThreshold +
				", requireSignature=" + requireSignature +
				", uid=" + uid +
				'}';
	}

	private byte getRequireSignatureAsByte() {
		return (byte) (requireSignature ? 1 : 0);
	}

	public long getBalance() {
		return balance;
	}

	public long getSendThreshold() {
		return sendThreshold;
	}

	public long getReceiveThreshold() {
		return receiveThreshold;
	}

	public boolean isRequireSignature() {
		return requireSignature;
	}

	public long getUid() {
		return uid;
	}

	public static int getSizeInBytes() {
		return 33;
	}

	public void setBalance(long balance) {
		this.balance = balance;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		AccountValue that = (AccountValue) o;
		return balance == that.balance && sendThreshold == that.sendThreshold && receiveThreshold == that.receiveThreshold && requireSignature == that.requireSignature && uid == that.uid;
	}

	@Override
	public int hashCode() {
		return Objects.hash(balance, sendThreshold, receiveThreshold, requireSignature, uid);
	}
}
