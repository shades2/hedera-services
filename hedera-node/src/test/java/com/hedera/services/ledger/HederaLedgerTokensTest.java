package com.hedera.services.ledger;

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

import com.hedera.services.store.tokens.views.UniqueTokenViewsManager;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.ledger.properties.AccountProperty.ALREADY_USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.LAST_ASSOCIATED_TOKEN;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class HederaLedgerTokensTest extends BaseHederaLedgerTestHelper {
	@BeforeEach
	private void setup() {
		commonSetup();
		setupWithMockLedger();
	}

	@Test
	void getsTokenBalance() {
		final var balance = subject.getTokenBalance(misc, frozenId);

		assertEquals(miscFrozenTokenBalance, balance);
	}

	@Test
	void adjustsIfValid() {
		givenOkTokenXfers(any(), any(), anyLong());

		final var status = subject.adjustTokenBalance(misc, tokenId, 555);

		verify(tokenStore).adjustBalance(misc, tokenId, 555);
		assertEquals(OK, status);
	}

	@Test
	void injectsLedgerToTokenStore() {
		verify(tokenStore).setAccountsLedger(accountsLedger);
		verify(tokenStore).setHederaLedger(subject);
		verify(creator).setLedger(subject);
	}

	@Test
	void delegatesFreezeOps() {
		subject.freeze(misc, frozenId);
		verify(tokenStore).freeze(misc, frozenId);

		subject.unfreeze(misc, frozenId);
		verify(tokenStore).unfreeze(misc, frozenId);
	}

	@Test
	void delegatesKnowingOps() {
		subject.grantKyc(misc, frozenId);
		verify(tokenStore).grantKyc(misc, frozenId);
	}

	@Test
	void delegatesTokenChangeDrop() {
		final var manager = mock(UniqueTokenViewsManager.class);
		subject.setTokenViewsManager(manager);

		given(nftsLedger.isInTransaction()).willReturn(true);
		given(manager.isInTransaction()).willReturn(true);

		subject.dropPendingTokenChanges();

		verify(tokenRelsLedger).rollback();
		verify(nftsLedger).rollback();
		verify(manager).rollback();
		verify(accountsLedger).undoChangesOfType(List.of(LAST_ASSOCIATED_TOKEN, NUM_NFTS_OWNED, ALREADY_USED_AUTOMATIC_ASSOCIATIONS));
		verify(sideEffectsTracker).resetTrackedTokenChanges();
	}

	@Test
	void onlyRollsbackIfTokenRelsLedgerInTxn() {
		given(tokenRelsLedger.isInTransaction()).willReturn(false);
		subject.setTokenViewsManager(mock(UniqueTokenViewsManager.class));

		subject.dropPendingTokenChanges();

		verify(tokenRelsLedger, never()).rollback();
	}

	@Test
	void forwardsTransactionalSemanticsToTokenLedgersIfPresent() {
		final var manager = mock(UniqueTokenViewsManager.class);
		final var inOrder = inOrder(tokenRelsLedger, nftsLedger, manager);
		given(tokenRelsLedger.isInTransaction()).willReturn(true);
		given(tokensLedger.isInTransaction()).willReturn(true);
		given(nftsLedger.isInTransaction()).willReturn(true);
		given(manager.isInTransaction()).willReturn(true);
		subject.setTokenViewsManager(manager);
		given(sideEffectsTracker.getNetTrackedHbarChanges()).willReturn(TransferList.getDefaultInstance());

		subject.begin();
		subject.commit();
		subject.begin();
		subject.rollback();

		inOrder.verify(tokenRelsLedger).begin();
		inOrder.verify(nftsLedger).begin();
		inOrder.verify(manager).begin();
		inOrder.verify(tokenRelsLedger).isInTransaction();
		inOrder.verify(tokenRelsLedger).commit();
		inOrder.verify(nftsLedger).isInTransaction();
		inOrder.verify(nftsLedger).commit();
		inOrder.verify(manager).isInTransaction();
		inOrder.verify(manager).commit();

		inOrder.verify(tokenRelsLedger).begin();
		inOrder.verify(nftsLedger).begin();
		inOrder.verify(manager).begin();
		inOrder.verify(tokenRelsLedger).isInTransaction();
		inOrder.verify(tokenRelsLedger).rollback();
		inOrder.verify(nftsLedger).isInTransaction();
		inOrder.verify(nftsLedger).rollback();
		inOrder.verify(manager).isInTransaction();
		inOrder.verify(manager).rollback();
	}
}
