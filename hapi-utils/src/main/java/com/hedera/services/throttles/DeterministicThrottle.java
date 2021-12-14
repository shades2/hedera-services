package com.hedera.services.throttles;

/*-
 * ‌
 * Hedera Services API Utilities
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

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static com.hedera.services.throttles.BucketThrottle.productWouldOverflow;

/**
 * A throttle with milli-TPS resolution that exists in a deterministic timeline.
 */
public class DeterministicThrottle {
	private static final Instant NEVER = null;
	private static final String NO_NAME = null;

	private final String name;
	private final BucketThrottle delegate;
	private Instant lastDecisionTime;

	/**
	 * Throttle with provided transaction rate in TPS
	 *
	 * @param tps
	 * 		given TPS transaction rate
	 * @return throttle with the TPS
	 */
	public static DeterministicThrottle withTps(final int tps) {
		return new DeterministicThrottle(BucketThrottle.withTps(tps), NO_NAME);
	}

	/**
	 * Named throttle with provided transaction rate in TPS
	 *
	 * @param tps
	 * 		given TPS transaction rate
	 * @param name
	 * 		given name for throttle
	 * @return named throttle with the TPS
	 */
	public static DeterministicThrottle withTpsNamed(final int tps, final String name) {
		return new DeterministicThrottle(BucketThrottle.withTps(tps), name);
	}

	/**
	 * Throttle with provided transaction rate in milli tps
	 *
	 * @param mtps
	 * 		given milli tps transaction rate
	 * @return throttle with the milli tps
	 */
	public static DeterministicThrottle withMtps(final long mtps) {
		return new DeterministicThrottle(BucketThrottle.withMtps(mtps), NO_NAME);
	}

	/**
	 * Named throttle with provided transaction rate in tps
	 *
	 * @param mtps
	 * 		given milli TPS transaction rate
	 * @param name
	 * 		given name for throttle
	 * @return named throttle with the milli tps
	 */
	public static DeterministicThrottle withMtpsNamed(final long mtps, final String name) {
		return new DeterministicThrottle(BucketThrottle.withMtps(mtps), name);
	}

	/**
	 * Throttle with provided transaction rate in tps and burst period
	 *
	 * @param tps
	 * 		given transaction rate
	 * @param burstPeriod
	 * 		given burst period
	 * @return throttle constructed
	 */
	public static DeterministicThrottle withTpsAndBurstPeriod(final int tps, final int burstPeriod) {
		return new DeterministicThrottle(BucketThrottle.withTpsAndBurstPeriod(tps, burstPeriod), NO_NAME);
	}

	/**
	 * Named throttle with provided transaction rate in tps and burst period
	 *
	 * @param tps
	 * 		given transaction rate in TPS
	 * @param burstPeriod
	 * 		given burst period
	 * @param name
	 * 		given name for throttle
	 * @return throttle constructed
	 */
	public static DeterministicThrottle withTpsAndBurstPeriodNamed(
			final int tps,
			final int burstPeriod,
			final String name
	) {
		return new DeterministicThrottle(BucketThrottle.withTpsAndBurstPeriod(tps, burstPeriod), name);
	}

	/**
	 * Throttle with provided transaction rate in milli tps and burst period
	 *
	 * @param mtps
	 * 		given transaction rate in milli TPS
	 * @param burstPeriod
	 * 		given burst period
	 * @return throttle constructed
	 */
	public static DeterministicThrottle withMtpsAndBurstPeriod(final long mtps, final int burstPeriod) {
		return new DeterministicThrottle(BucketThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod), NO_NAME);
	}

	/**
	 * Named throttle with provided transaction rate in milli tps and burst period
	 *
	 * @param mtps
	 * 		given transaction rate in milli TPS
	 * @param burstPeriod
	 * 		given burst period
	 * @param name
	 * 		name of the throttle
	 * @return throttle constructed
	 */
	public static DeterministicThrottle withMtpsAndBurstPeriodNamed(
			final long mtps,
			final int burstPeriod,
			final String name) {
		return new DeterministicThrottle(BucketThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod), name);
	}

	/**
	 * Throttle with provided transaction rate in tps and burst period in milli secs
	 *
	 * @param tps
	 * 		given transaction rate in TPS
	 * @param burstPeriodMs
	 * 		given burst period in milli secs
	 * @return throttle constructed
	 */
	public static DeterministicThrottle withTpsAndBurstPeriodMs(final int tps, final long burstPeriodMs) {
		return new DeterministicThrottle(BucketThrottle.withTpsAndBurstPeriodMs(tps, burstPeriodMs), NO_NAME);
	}

	/**
	 * Named throttle with provided transaction rate in tps and burst period in milli secs
	 *
	 * @param tps
	 * 		given transaction rate in TPS
	 * @param burstPeriodMs
	 * 		given burst period in milli secs
	 * @param name
	 * 		given name for throttle
	 * @return throttle constructed
	 */
	public static DeterministicThrottle withTpsAndBurstPeriodMsNamed(
			final int tps,
			final long burstPeriodMs,
			final String name) {
		return new DeterministicThrottle(BucketThrottle.withTpsAndBurstPeriodMs(tps, burstPeriodMs), name);
	}

	/**
	 * Throttle with provided transaction rate in milli tps and burst period in milli secs
	 *
	 * @param mtps
	 * 		given transaction rate in milli TPS
	 * @param burstPeriodMs
	 * 		given burst period in milli secs
	 * @return throttle constructed
	 */
	public static DeterministicThrottle withMtpsAndBurstPeriodMs(final long mtps, final long burstPeriodMs) {
		return new DeterministicThrottle(BucketThrottle.withMtpsAndBurstPeriodMs(mtps, burstPeriodMs), NO_NAME);
	}

	/**
	 * Named throttle with provided transaction rate in milli tps and burst period in milli secs
	 *
	 * @param mtps
	 * 		given transaction rate in milli TPS
	 * @param burstPeriodMs
	 * 		given burst period in milli secs
	 * @param name
	 * 		given name for throttle
	 * @return throttle constructed
	 */
	public static DeterministicThrottle withMtpsAndBurstPeriodMsNamed(
			final long mtps,
			final long burstPeriodMs,
			final String name) {
		return new DeterministicThrottle(BucketThrottle.withMtpsAndBurstPeriodMs(mtps, burstPeriodMs), name);
	}

	private DeterministicThrottle(final BucketThrottle delegate, final String name) {
		this.name = name;
		this.delegate = delegate;
		lastDecisionTime = NEVER;
	}

	/**
	 * Capacity required for given number of transactions
	 *
	 * @param nTransactions
	 * 		number of transactions
	 * @return capacity needed
	 */
	public static long capacityRequiredFor(final int nTransactions) {
		if (productWouldOverflow(nTransactions, BucketThrottle.capacityUnitsPerTxn())) {
			return -1;
		}
		return nTransactions * BucketThrottle.capacityUnitsPerTxn();
	}

	/**
	 * Checks whether the throttle timeline is in allowed range compared to {@code Instant.now()}
	 *
	 * @param n
	 * 		number of operations requested
	 * @return true if the operation request is allowed, false otherwise
	 */
	public boolean allow(final int n) {
		return allow(n, Instant.now());
	}

	/**
	 * Checks whether the throttle timeline is in allowed range compared to given timestamp
	 *
	 * @param n
	 * 		number of operations requested
	 * @param now
	 * 		timestamp
	 * @return true if the operation request is allowed, false otherwise
	 */
	public boolean allow(final int n, final Instant now) {
		long elapsedNanos = 0L;
		if (lastDecisionTime != NEVER) {
			elapsedNanos = Duration.between(lastDecisionTime, now).toNanos();
			if (elapsedNanos < 0L) {
				throw new IllegalArgumentException(
						"Throttle timeline must advance, but " + now + " is not after " + lastDecisionTime + "!");
			}
		}

		lastDecisionTime = now;
		return delegate.allow(n, elapsedNanos);
	}

	/**
	 * Resets the last usage units
	 */
	public void reclaimLastAllowedUse() {
		delegate.reclaimLastAllowedUse();
	}

	/**
	 * Name of the throttle
	 *
	 * @return
	 */
	public String name() {
		return name;
	}

	/**
	 * Milli TPS of the throttle
	 *
	 * @return
	 */
	public long mtps() {
		return delegate.mtps();
	}

	/**
	 * capacity used from the bucket
	 *
	 * @return
	 */
	public long used() {
		return delegate.bucket().capacityUsed();
	}

	/**
	 * Total capacity of the bucket
	 *
	 * @return
	 */
	public long capacity() {
		return delegate.bucket().totalCapacity();
	}

	public UsageSnapshot usageSnapshot() {
		final var bucket = delegate.bucket();
		return new UsageSnapshot(bucket.capacityUsed(), lastDecisionTime);
	}

	public void resetUsageTo(final UsageSnapshot usageSnapshot) {
		final var bucket = delegate.bucket();
		lastDecisionTime = usageSnapshot.lastDecisionTime();
		bucket.resetUsed(usageSnapshot.used());
	}

	/* NOTE: The Object methods below are only overridden to improve
	readability of unit tests; Instances of this class are not used
        in hash-based collections */
	@Override
	public boolean equals(final Object obj) {
		if (obj == null || this.getClass() != obj.getClass()) {
			return false;
		}

		final var that = (DeterministicThrottle) obj;

		return this.delegate.bucket().totalCapacity() == that.delegate.bucket().totalCapacity()
				&& this.delegate.mtps() == that.delegate.mtps();
	}

	@Override
	public int hashCode() {
		return Objects.hash(delegate.bucket().totalCapacity(), delegate.mtps(), name, lastDecisionTime);
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder("DeterministicThrottle{");
		if (name != null) {
			sb.append("name='").append(name).append("', ");
		}
		return sb
				.append("mtps=").append(delegate.mtps()).append(", ")
				.append("capacity=").append(capacity()).append(" (used=").append(used()).append(")")
				.append(lastDecisionTime == NEVER ? "" : (", last decision @ " + lastDecisionTime))
				.append("}")
				.toString();
	}

	public static class UsageSnapshot {
		private final long used;
		private final Instant lastDecisionTime;

		/**
		 * Default constructor
		 *
		 * @param used
		 * 		bucket capacity used
		 * @param lastDecisionTime
		 */
		public UsageSnapshot(final long used, final Instant lastDecisionTime) {
			this.used = used;
			this.lastDecisionTime = lastDecisionTime;
		}

		/**
		 * capacity of the bucket used
		 *
		 * @return used capacity
		 */
		public long used() {
			return used;
		}

		/**
		 * last decision time of the snapshot
		 *
		 * @return last decision timestamp
		 */
		public Instant lastDecisionTime() {
			return lastDecisionTime;
		}

		@Override
		public String toString() {
			final var sb = new StringBuilder("DeterministicThrottle.UsageSnapshot{");
			return sb
					.append("used=").append(used)
					.append(", last decision @ ")
					.append(lastDecisionTime == NEVER ? "<N/A>" : lastDecisionTime)
					.append("}")
					.toString();
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o == null || !o.getClass().equals(UsageSnapshot.class)) {
				return false;
			}
			final var that = (UsageSnapshot) o;
			return this.used == that.used && Objects.equals(this.lastDecisionTime, that.lastDecisionTime);
		}

		@Override
		public int hashCode() {
			return Objects.hash(used, lastDecisionTime);
		}
	}

	/**
	 * Throttle to be delegated to
	 *
	 * @return to be delegated throttle
	 */
	BucketThrottle delegate() {
		return delegate;
	}

	/**
	 * last decision time
	 *
	 * @return last decision time
	 */
	Instant lastDecisionTime() {
		return lastDecisionTime;
	}
}
