package com.hedera.services.ledger.accounts;

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

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityIdUtils.readableId;
import static java.util.stream.Collectors.toList;

/**
 * A store that provides efficient access to the list of Tokens
 * that an Account is a treasury of indexed by @code AccountID.
 * This class is <b>not</b> thread-safe, and should never be used
 * by any thread other than the {@code handleTransaction} thread.
 */
public class BackingTokenTreasuries {
	Map<AccountID, Set<TokenID>> knownTreasuries = new HashMap<>();

	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> delegate;

	public BackingTokenTreasuries(
			final Supplier<FCMap<MerkleEntityId, MerkleToken>> delegate) {
		this.delegate = delegate;
		buildFromSource();
	}

	public void rebuildFromSources() {
		knownTreasuries.clear();
		buildFromSource();
	}

	public boolean isKnownTreasury(AccountID aid) {
		return knownTreasuries.containsKey(aid);
	}

	public boolean isTreasuryForToken(AccountID aId, TokenID tId) {
		if (!knownTreasuries.containsKey(aId)) {
			return false;
		}
		return knownTreasuries.get(aId).contains(tId);
	}

	public List<TokenID> listOfTokensServed(AccountID treasury) {
		if (!isKnownTreasury(treasury)) {
			return Collections.emptyList();
		} else {
			return knownTreasuries.get(treasury).stream()
					.sorted(HederaLedger.TOKEN_ID_COMPARATOR)
					.collect(toList());
		}
	}

	private void buildFromSource() {
		delegate.get().forEach((key, value) -> {
			/* A deleted token's treasury is no longer bound by ACCOUNT_IS_TREASURY restrictions. */
			if (!value.isDeleted()) {
				addKnownTreasury(value.treasury().toGrpcAccountId(), key.toTokenId());
			}
		});
	}

	public void addKnownTreasury(AccountID aId, TokenID tId) {
		knownTreasuries.computeIfAbsent(aId, ignore -> new HashSet<>()).add(tId);
	}

	public void removeKnownTreasuryForToken(AccountID aId, TokenID tId) {
		throwIfKnownTreasuryIsMissing(aId);
		knownTreasuries.get(aId).remove(tId);
		if (knownTreasuries.get(aId).isEmpty()) {
			knownTreasuries.remove(aId);
		}
	}

	private void throwIfKnownTreasuryIsMissing(AccountID aId) {
		if (!knownTreasuries.containsKey(aId)) {
			throw new IllegalArgumentException(String.format(
					"Argument 'aId=%s' does not refer to a known treasury!",
					readableId(aId)));
		}
	}

	Map<AccountID, Set<TokenID>> getKnownTreasuries() {
		return knownTreasuries;
	}
}
