package com.hedera.services.txns.token;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doThrow;

class TokenDeleteTransitionLogicTest {
	private long tokenNum = 12345L;
	private TokenID tokenID = IdUtils.asToken("0.0." + tokenNum);
	private Id tokenId = new Id(0,0,tokenNum);

	private TypedTokenStore tokenStore;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private Token token;

	private TransactionBody tokenDeleteTxn;
	private TokenDeleteTransitionLogic subject;

	@BeforeEach
	private void setup() {
		tokenStore = mock(TypedTokenStore.class);
		accessor = mock(PlatformTxnAccessor.class);
		token = mock(Token.class);

		txnCtx = mock(TransactionContext.class);

		subject = new TokenDeleteTransitionLogic(tokenStore, txnCtx);
	}

	@Test
	public void capturesInvalidDelete() {
		givenValidTxnCtx();
		// and:
		doThrow(new InvalidTransactionException(INVALID_TOKEN_ID)).when(tokenStore).loadToken(tokenId);

		// verify:
		assertFailsWith(() -> subject.doStateTransition(), INVALID_TOKEN_ID);
	}

	@Test
	public void capturesInvalidDeletionDueToAlreadyDeleted() {
		givenValidTxnCtx();
		// and:
		doThrow(new InvalidTransactionException(TOKEN_WAS_DELETED)).when(tokenStore).loadToken(tokenId);

		// verify:
		assertFailsWith(() -> subject.doStateTransition(), TOKEN_WAS_DELETED);
	}

	@Test
	public void capturesInvalidDeletionOfImmutableToken() {
		givenValidTxnCtx();
		// and:
		doThrow(new InvalidTransactionException(TOKEN_IS_IMMUTABLE)).when(tokenStore).loadToken(tokenId);

		// verify:
		assertFailsWith(() -> subject.doStateTransition(), TOKEN_IS_IMMUTABLE);
	}

	@Test
	public void followsHappyPath() {
		givenValidTxnCtx();

		// when:
		subject.doStateTransition();

		// then:
		verify(tokenStore).loadToken(tokenId);
		verify(token).delete();
		verify(tokenStore).persistToken(token);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenDeleteTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(tokenDeleteTxn));
	}

	@Test
	public void rejectsMissingToken() {
		givenMissingToken();

		// expect:
		assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenDeleteTxn));
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}

	private void givenValidTxnCtx() {
		tokenDeleteTxn = TransactionBody.newBuilder()
				.setTokenDeletion(TokenDeleteTransactionBody.newBuilder()
						.setToken(tokenID))
				.build();
		given(accessor.getTxn()).willReturn(tokenDeleteTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(tokenStore.loadToken(tokenId)).willReturn(token);
	}

	private void givenMissingToken() {
		tokenDeleteTxn = TransactionBody.newBuilder()
				.setTokenDeletion(TokenDeleteTransactionBody.newBuilder())
				.build();
	}
}
