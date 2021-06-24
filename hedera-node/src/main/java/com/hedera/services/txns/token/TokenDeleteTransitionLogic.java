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
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides the state transition for token deletion.
 *
 * @author Michael Tinker
 */
public class TokenDeleteTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenDeleteTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;

	public TokenDeleteTransitionLogic(
			TypedTokenStore tokenStore,
			TransactionContext txnCtx
	) {
		this.tokenStore = tokenStore;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenDeletion();
		final var grpcId = op.getToken();
		final var tokenId = new Id(grpcId.getShardNum(), grpcId.getRealmNum(), grpcId.getTokenNum());

		/* --- Load the model objects --- */
		var token = tokenStore.loadToken(tokenId);

		/* --- Do the business logic --- */
		token.delete();

		/* --- Persist the updated models --- */
		tokenStore.persistToken(token);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenDeletion;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenDeleteTransactionBody op = txnBody.getTokenDeletion();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		return OK;
	}
}
