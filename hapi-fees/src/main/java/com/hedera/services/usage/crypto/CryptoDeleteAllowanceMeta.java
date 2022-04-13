package com.hedera.services.usage.crypto;

/*-
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;

import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.NFT_DELETE_ALLOWANCE_SIZE;

/**
 * Metadata for CryptoDeleteAllowance
 */
public class CryptoDeleteAllowanceMeta {
	private final long effectiveNow;
	private final long msgBytesUsed;

	public CryptoDeleteAllowanceMeta(Builder builder) {
		effectiveNow = builder.effectiveNow;
		msgBytesUsed = builder.msgBytesUsed;
	}

	public CryptoDeleteAllowanceMeta(CryptoDeleteAllowanceTransactionBody cryptoDeleteTxnBody,
			long transactionValidStartSecs) {
		effectiveNow = transactionValidStartSecs;
		msgBytesUsed = bytesUsedInTxn(cryptoDeleteTxnBody);
	}

	private int bytesUsedInTxn(CryptoDeleteAllowanceTransactionBody op) {
		return op.getNftAllowancesCount() * NFT_DELETE_ALLOWANCE_SIZE +
				countNftDeleteSerials(op.getNftAllowancesList()) * LONG_SIZE;
	}

	public static int countNftDeleteSerials(final List<NftRemoveAllowance> nftAllowancesList) {
		int totalSerials = 0;
		for (var allowance : nftAllowancesList) {
			totalSerials += allowance.getSerialNumbersCount();
		}
		return totalSerials;

	}

	public static Builder newBuilder() {
		return new CryptoDeleteAllowanceMeta.Builder();
	}

	public long getEffectiveNow() {
		return effectiveNow;
	}

	public long getMsgBytesUsed() {
		return msgBytesUsed;
	}

	public static class Builder {
		private long effectiveNow;
		private long msgBytesUsed;

		public CryptoDeleteAllowanceMeta.Builder effectiveNow(long now) {
			this.effectiveNow = now;
			return this;
		}

		public CryptoDeleteAllowanceMeta.Builder msgBytesUsed(long msgBytesUsed) {
			this.msgBytesUsed = msgBytesUsed;
			return this;
		}

		public Builder() {
			// empty here on purpose.
		}

		public CryptoDeleteAllowanceMeta build() {
			return new CryptoDeleteAllowanceMeta(this);
		}
	}

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("effectiveNow", effectiveNow)
				.add("msgBytesUsed", msgBytesUsed)
				.toString();
	}
}
