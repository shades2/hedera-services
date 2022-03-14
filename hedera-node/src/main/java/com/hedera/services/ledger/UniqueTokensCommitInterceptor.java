package com.hedera.services.ledger;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.NftId;

public class UniqueTokensCommitInterceptor implements CommitInterceptor<NftId, UniqueTokenValue, NftProperty> {
	// The tracker this interceptor should use for previewing changes. The interceptor is NOT
	// responsible for calling reset() on the tracker, as that will be done by the client code.
//	private SideEffectsTracker sideEffectsTracker;

	public UniqueTokensCommitInterceptor(final SideEffectsTracker sideEffectsTracker) {
//		this.sideEffectsTracker = sideEffectsTracker;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void preview(final EntityChangeSet<NftId, UniqueTokenValue, NftProperty> pendingChanges) {
		// No-op for now
	}
}
