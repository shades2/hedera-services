package com.hedera.services.sysfiles.domain.throttling;

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

import com.hederahashgraph.api.proto.java.HederaFunctionality;

import java.util.ArrayList;
import java.util.List;

public class ThrottleGroup {
	int opsPerSec;
	long milliOpsPerSec;
	List<HederaFunctionality> operations = new ArrayList<>();

	/**
	 * Setter for the milliOps/sec
	 *
	 * @param milliOpsPerSec
	 * 		given milliOps/sec
	 */
	public void setMilliOpsPerSec(long milliOpsPerSec) {
		this.milliOpsPerSec = milliOpsPerSec;
	}

	/**
	 * Getter for Ops/sec
	 *
	 * @return ops/sec
	 */
	public int getOpsPerSec() {
		return opsPerSec;
	}

	/**
	 * Setter for Ops/sec
	 *
	 * @param opsPerSec
	 * 		given ops/sec
	 */
	public void setOpsPerSec(int opsPerSec) {
		this.opsPerSec = opsPerSec;
	}

	/**
	 * @return
	 */
	public List<HederaFunctionality> getOperations() {
		return operations;
	}

	/**
	 * set of Hedera operations in the Throttle group
	 * @param operations Hedera operations
	 */
	public void setOperations(List<HederaFunctionality> operations) {
		this.operations = operations;
	}

	/**
	 * Construct a POJO {@code ThrottleGroup} from the protobuf {@code com.hederahashgraph.api.proto.java.ThrottleGroup}
	 *
	 * @param group
	 * 		given protobuf throttle group
	 * @return POJO throttle group
	 */
	public static ThrottleGroup fromProto(com.hederahashgraph.api.proto.java.ThrottleGroup group) {
		var pojo = new ThrottleGroup();
		pojo.setMilliOpsPerSec(group.getMilliOpsPerSec());
		pojo.setOpsPerSec((int) (group.getMilliOpsPerSec() / 1_000));
		pojo.operations.addAll(group.getOperationsList());
		return pojo;
	}

	/**
	 * Construct protobuf {@code com.hederahashgraph.api.proto.java.ThrottleGroup} from POJO {@code ThrottleGroup}
	 *
	 * @return protobuf throttle group
	 */
	public com.hederahashgraph.api.proto.java.ThrottleGroup toProto() {
		return com.hederahashgraph.api.proto.java.ThrottleGroup.newBuilder()
				.setMilliOpsPerSec(impliedMilliOpsPerSec())
				.addAllOperations(operations)
				.build();
	}

	/**
	 * Getter for milliOps/sec
	 *
	 * @return milliOps/sec
	 */
	public long getMilliOpsPerSec() {
		return milliOpsPerSec;
	}

	/**
	 * Get milliOps/sec from ops if milliOps/sec is not provided
	 *
	 * @return milliOps/sec
	 */
	long impliedMilliOpsPerSec() {
		return milliOpsPerSec > 0 ? milliOpsPerSec : 1_000 * opsPerSec;
	}
}
