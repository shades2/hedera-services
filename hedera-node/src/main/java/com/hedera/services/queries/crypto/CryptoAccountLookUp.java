/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.queries.crypto;

import com.hedera.services.ledger.accounts.AliasManager;
import com.hederahashgraph.api.proto.java.AccountID;

import javax.inject.Inject;

import static com.hedera.services.utils.EntityIdUtils.isAlias;

public class CryptoAccountLookUp {
	private final AliasManager aliasManager;

	@Inject
	CryptoAccountLookUp(AliasManager aliasManager) {
		this.aliasManager = aliasManager;
	}

	protected AccountID lookUpAccountID(final AccountID idOrAlias) {
		if (isAlias(idOrAlias)) {
			final var id = aliasManager.lookupIdBy(idOrAlias.getAlias());
			return id.toGrpcAccountId();
		} else {
			return idOrAlias;
		}
	}

	protected AliasManager getAliasManager() {
		return aliasManager;
	}
}
