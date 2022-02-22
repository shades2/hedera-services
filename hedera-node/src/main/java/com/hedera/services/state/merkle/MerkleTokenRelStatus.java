package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.hedera.services.utils.EntityIdUtils.asRelationshipLiteral;

public class MerkleTokenRelStatus extends AbstractMerkleLeaf implements Keyed<EntityNumPair> {
	private static final int PREV_INDEX = 0;
	private static final int NEXT_INDEX = 1;

	static final int RELEASE_090_VERSION = 1;
	static final int RELEASE_0180_PRE_SDK_VERSION = 2;
	static final int RELEASE_0180_VERSION = 3;
	static final int CURRENT_VERSION = RELEASE_0180_VERSION;

	static final long RUNTIME_CONSTRUCTABLE_ID = 0xe487c7b8b4e7233fL;

	private EntityNumPair[] keys;

	private long numbers;
	private long balance;
	private boolean frozen;
	private boolean kycGranted;
	private boolean automaticAssociation;

	public MerkleTokenRelStatus() {
		/* RuntimeConstructable */
	}

	public MerkleTokenRelStatus(
			long balance,
			boolean frozen,
			boolean kycGranted,
			boolean automaticAssociation
	) {
		this.balance = balance;
		this.frozen = frozen;
		this.kycGranted = kycGranted;
		this.automaticAssociation = automaticAssociation;
	}

	public MerkleTokenRelStatus(
			long balance,
			boolean frozen,
			boolean kycGranted,
			boolean automaticAssociation,
			long numbers
	) {
		this.balance = balance;
		this.frozen = frozen;
		this.kycGranted = kycGranted;
		this.numbers = numbers;
		this.automaticAssociation = automaticAssociation;
	}

	/* --- MerkleLeaf --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		balance = in.readLong();
		frozen = in.readBoolean();
		kycGranted = in.readBoolean();
		if (version >= RELEASE_0180_PRE_SDK_VERSION) {
			automaticAssociation = in.readBoolean();
		}
		if (version >= RELEASE_0180_VERSION) {
			numbers = in.readLong();
		}
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(balance);
		out.writeBoolean(frozen);
		out.writeBoolean(kycGranted);
		out.writeBoolean(automaticAssociation);
		out.writeLong(numbers);
	}

	/* --- Object --- */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleTokenRelStatus.class != o.getClass()) {
			return false;
		}

		var that = (MerkleTokenRelStatus) o;
		return this.balance == that.balance
				&& this.frozen == that.frozen
				&& this.kycGranted == that.kycGranted
				&& this.numbers == that.numbers
				&& this.automaticAssociation == that.automaticAssociation;
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(balance)
				.append(frozen)
				.append(kycGranted)
				.append(automaticAssociation)
				.toHashCode();
	}

	/* --- Bean --- */
	public long getBalance() {
		return balance;
	}

	public void setBalance(long balance) {
		throwIfImmutable("Cannot change this token relation's balance if it's immutable.");
		if (balance < 0) {
			throw new IllegalArgumentException(String.format("Argument 'balance=%d' would negate %s!", balance, this));
		}
		this.balance = balance;
	}

	public boolean isFrozen() {
		return frozen;
	}

	public void setFrozen(boolean frozen) {
		throwIfImmutable("Cannot change this token relation's frozen status if it's immutable.");
		this.frozen = frozen;
	}

	public boolean isKycGranted() {
		return kycGranted;
	}

	public void setKycGranted(boolean kycGranted) {
		throwIfImmutable("Cannot change this token relation's grant kyc if it's immutable.");
		this.kycGranted = kycGranted;
	}

	public void setKeys(EntityNumPair[] keys) {
		throwIfImmutable("Cannot change this token relation's ListNode keys if it's immutable.");
		this.keys = keys;
	}

	public EntityNumPair[] getKeys() {
		return keys;
	}

	public boolean isAutomaticAssociation() {
		return automaticAssociation;
	}

	public void setAutomaticAssociation(boolean automaticAssociation) {
		throwIfImmutable("Cannot change this token relation's automaticAssociation if it's immutable.");
		this.automaticAssociation = automaticAssociation;
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleTokenRelStatus copy() {
		setImmutable(true);
		return new MerkleTokenRelStatus(balance, frozen, kycGranted, automaticAssociation, numbers);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("balance", balance)
				.add("isFrozen", frozen)
				.add("hasKycGranted", kycGranted)
				.add("key", numbers + " <-> " + asRelationshipLiteral(numbers))
				.add("isAutomaticAssociation", automaticAssociation)
				.toString();
	}

	/* --- Keyed --- */
	@Override
	public EntityNumPair getKey() {
		return new EntityNumPair(numbers);
	}

	@Override
	public void setKey(EntityNumPair numbers) {
		this.numbers = numbers.value();
	}

	public EntityNumPair prevKey() {
		return keys[PREV_INDEX];
	}

	public EntityNumPair nextKey() {
		return keys[NEXT_INDEX];
	}

	public void setPrevKey(@Nullable final EntityNumPair prevKey) {
		keys[PREV_INDEX] = prevKey;
	}

	public void setNextKey(@Nullable final EntityNumPair nextKey) {
		keys[NEXT_INDEX] = nextKey;
	}
//
//	/* --- MapValueListNode --- */
//	@Override
//	public EntityNumPair prevKey() {
//		return keys[PREV_INDEX];
//	}
//
//	@Override
//	public EntityNumPair nextKey() {
//		return keys[NEXT_INDEX];
//	}
//
//	@Override
//	public void setPrevKey(@Nullable final EntityNumPair prevKey) {
//		keys[PREV_INDEX] = prevKey;
//	}
//
//	@Override
//	public void setNextKey(@Nullable final EntityNumPair nextKey) {
//		keys[NEXT_INDEX] = nextKey;
//	}
//
//	@Override
//	public void serializeValueTo(final SerializableDataOutputStream out) throws IOException {
//		out.writeSerializableList(Arrays.asList(keys), true, true);
//		serialize(out);
//	}
//
//	@Override
//	public void deserializeValueFrom(final SerializableDataInputStream in, final int version) throws IOException {
//		final List<EntityNumPair> wrappedKeys = in.readSerializableList(3);
//		keys = orderedKeys(wrappedKeys.get(0), wrappedKeys.get(1), wrappedKeys.get(2));
//		deserialize(in, version);
//	}
//
//	@Override
//	public MerkleTokenRelStatus newValueCopyOf(final MerkleTokenRelStatus that) {
//		final var copy = new MerkleTokenRelStatus(
//				that.getBalance(),
//				that.isFrozen(),
//				that.isKycGranted(),
//				that.isAutomaticAssociation(),
//				that.numbers);
//		copy.setKeys(that.getKeys());
//		return copy;
//	}
//
//	@Override
//	public MerkleTokenRelStatus self() {
//		return this;
//	}
//
//	/* --- Internal helpers -- */
//	private EntityNumPair[] orderedKeys(EntityNumPair... k123) {
//		return k123;
//	}
}
