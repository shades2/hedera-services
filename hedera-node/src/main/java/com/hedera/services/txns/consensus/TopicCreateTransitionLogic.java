package com.hedera.services.txns.consensus;

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
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TopicStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Topic;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.TopicCreateAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * The syntax check pre-consensus validates the adminKey's structure as signature validation occurs before
 * doStateTransition().
 */
@Singleton
public final class TopicCreateTransitionLogic implements TransitionLogic {
	private final AccountStore accountStore;
	private final TopicStore topicStore;
	private final EntityIdSource entityIdSource;
	private final OptionValidator validator;
	private final SigImpactHistorian sigImpactHistorian;
	private final TransactionContext txnCtx;

	@Inject
	public TopicCreateTransitionLogic(
			final TopicStore topicStore,
			final EntityIdSource entityIdSource,
			final OptionValidator validator,
			final SigImpactHistorian sigImpactHistorian,
			final TransactionContext transactionContext,
			final AccountStore accountStore
	) {
		this.accountStore = accountStore;
		this.topicStore = topicStore;
		this.entityIdSource = entityIdSource;
		this.validator = validator;
		this.sigImpactHistorian = sigImpactHistorian;
		this.txnCtx = transactionContext;
	}

	@Override
	public void doStateTransition() {
		/* --- Extract gRPC --- */
		final var accessor = (TopicCreateAccessor) txnCtx.accessor();
		final var payerAccountId = accessor.getPayer();
		final var submitKey = accessor.hasSubmitKey() ? validator.attemptDecodeOrThrow(accessor.submitKey()) : null;
		final var adminKey = accessor.hasAdminKey() ? validator.attemptDecodeOrThrow(accessor.adminKey()) : null;
		final var memo = accessor.memo();
		final var autoRenewPeriod = accessor.autoRenewPeriod();
		final var autoRenewAccountId = accessor.hasAutoRenewAccount() ? accessor.autoRenewAccount() : null;

		/* --- Validate --- */
		final var memoValidationResult = validator.memoCheck(memo);
		validateTrue(OK == memoValidationResult, memoValidationResult);
		validateTrue(accessor.hasAutoRenewPeriod(), INVALID_RENEWAL_PERIOD);
		validateTrue(validator.isValidAutoRenewPeriod(autoRenewPeriod), AUTORENEW_DURATION_NOT_IN_RANGE);
		Account autoRenewAccount = null;
		if (accessor.hasAutoRenewAccount()) {
			autoRenewAccount = accountStore.loadAccountOrFailWith(autoRenewAccountId, INVALID_AUTORENEW_ACCOUNT);
			validateFalse(autoRenewAccount.isSmartContract(), INVALID_AUTORENEW_ACCOUNT);
			validateTrue(accessor.hasAdminKey(), AUTORENEW_ACCOUNT_NOT_ALLOWED);
		}

		/* --- Do business logic --- */
		final var expirationTime = txnCtx.consensusTime().plusSeconds(autoRenewPeriod.getSeconds());
		final var topicId = entityIdSource.newTopicId(payerAccountId);
		final var topic = Topic.fromGrpcTopicCreate(
				Id.fromGrpcTopic(topicId),
				submitKey,
				adminKey,
				autoRenewAccount,
				memo,
				autoRenewPeriod.getSeconds(),
				expirationTime);

		/* --- Persist the topic --- */
		topicStore.persistNew(topic);
		sigImpactHistorian.markEntityChanged(topicId.getTopicNum());
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasConsensusCreateTopic;
	}

	@Override
	public ResponseCodeEnum validateSemantics(final TxnAccessor accessor) {
		return validate((TopicCreateAccessor) accessor);
	}

	/**
	 * Pre-consensus (and post-consensus-pre-doStateTransition) validation validates the encoding of the optional
	 * adminKey; this check occurs before signature validation which occurs before doStateTransition.
	 *
	 * @param accessor
	 * 		- the TxnAccessor
	 * @return the validity
	 */
	private ResponseCodeEnum validate(final TopicCreateAccessor accessor) {
		if (accessor.hasAdminKey() && !validator.hasGoodEncoding(accessor.adminKey())) {
			return BAD_ENCODING;
		}
		return OK;
	}
}
