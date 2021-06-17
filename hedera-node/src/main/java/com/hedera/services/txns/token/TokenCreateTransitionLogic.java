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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.state.submerkle.EntityId.fromGrpcAccountId;
import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hedera.services.txns.validation.TokenListChecks.initialSupplyAndDecimalsCheck;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;

/**
 * Provides the state transition for token creation.
 *
 * @author Michael Tinker
 */
public class TokenCreateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenCreateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;
	private final Id NO_PENDING_ID = new Id();

	private final EntityIdSource ids;
	private final OptionValidator validator;
	private final HederaLedger ledger;
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties dynamicProperties;

	public TokenCreateTransitionLogic(
			OptionValidator validator,
			AccountStore accountStore,
			TypedTokenStore tokenStore,
			HederaLedger ledger,
			TransactionContext txnCtx,
			EntityIdSource ids,
			GlobalDynamicProperties dynamicProperties
	) {
		this.validator = validator;
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.ledger = ledger;
		this.txnCtx = txnCtx;
		this.ids = ids;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void doStateTransition()  {
		try {
			/* --- Translate from gRPC types --- */
			final var op = txnCtx.accessor().getTxn().getTokenCreation();
			if (op.hasExpiry() && !validator.isValidExpiry(op.getExpiry())) {
				txnCtx.setStatus(INVALID_EXPIRATION_TIME);
				return;
			}

			/* -- Do business logic --- */
			transitionFor(op);
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxnWrapper(), e);
			abortWith(NO_PENDING_ID, FAIL_INVALID);
		}
	}

	private void transitionFor(TokenCreateTransactionBody op) {
		try{
			validateAccountId(txnCtx.activePayer());
		} catch (InvalidTransactionException ex) {
			abortWith(NO_PENDING_ID, INVALID_PAYER_ACCOUNT_ID);
			return;
		}

		AccountID treasury = op.getTreasury();
		Account treasuryAccount;
		try{
			treasuryAccount = validateAccountId(treasury);
		} catch (InvalidTransactionException ex) {
			abortWith(NO_PENDING_ID, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
			return;
		}

		if (op.hasAutoRenewAccount()) {
			try{
				validateAccountId(op.getAutoRenewAccount());
			} catch (InvalidTransactionException ex) {
				abortWith(NO_PENDING_ID, INVALID_AUTORENEW_ACCOUNT);
				return;
			}
		}

		final Id tokenId = ids.newId(treasuryAccount.getId());
		final var token = new Token(tokenId);
		final var merkleToken = createMerkleToken(op, treasuryAccount);


		var status = OK;
		/* --- associate the created token with its treasury --- */
		try {
			tokenStore.addToken(token, merkleToken);
			treasuryAccount.associateWith(List.of(token),
					dynamicProperties.maxTokensPerAccount());
		} catch (InvalidTransactionException ex) {
			status = ex.getResponseCode();
			abortWith(tokenId, status);
			return;
		}


		TokenID created = TokenID.newBuilder()
				.setRealmNum(tokenId.getRealm())
				.setShardNum(tokenId.getShard())
				.setTokenNum(tokenId.getNum())
				.build();

		if (op.hasFreezeKey()) {
			status = ledger.unfreeze(treasury, created);
		}
		if (status == OK && op.hasKycKey()) {
			status = ledger.grantKyc(treasury, created);
		}
		if (status == OK) {
			status = ledger.adjustTokenBalance(treasury, created, op.getInitialSupply());
		}

		if (status != OK) {
			abortWith(tokenId, status);
			return;
		}

		accountStore.persistAccount(treasuryAccount);
		tokenStore.persistTokenRelationship(token.newRelationshipWith(treasuryAccount));

		txnCtx.setCreatedTokenId(tokenId);
		txnCtx.setStatus(SUCCESS);
	}

	private MerkleToken createMerkleToken(TokenCreateTransactionBody op, Account treasuryAccount) {
		var freezeKey = asUsableFcKey(op.getFreezeKey());
		var adminKey = asUsableFcKey(op.getAdminKey());
		var kycKey = asUsableFcKey(op.getKycKey());
		var wipeKey = asUsableFcKey(op.getWipeKey());
		var supplyKey = asUsableFcKey(op.getSupplyKey());
		var expiry = expiryOf(op, txnCtx.consensusTime().getEpochSecond());
		MerkleToken pendingCreation = new MerkleToken(
				expiry,
				op.getInitialSupply(),
				op.getDecimals(),
				op.getSymbol(),
				op.getName(),
				op.getFreezeDefault(),
				kycKey.isEmpty(),
				new EntityId(treasuryAccount.getId()));

		pendingCreation.setMemo(op.getMemo());
		adminKey.ifPresent(pendingCreation::setAdminKey);
		kycKey.ifPresent(pendingCreation::setKycKey);
		wipeKey.ifPresent(pendingCreation::setWipeKey);
		freezeKey.ifPresent(pendingCreation::setFreezeKey);
		supplyKey.ifPresent(pendingCreation::setSupplyKey);

		if(op.hasCustomFees()) {
			// TODO
		}

		if (op.hasAutoRenewAccount()) {
			pendingCreation.setAutoRenewAccount(fromGrpcAccountId(op.getAutoRenewAccount()));
			pendingCreation.setAutoRenewPeriod(op.getAutoRenewPeriod().getSeconds());
		}
		return pendingCreation;
	}

	private void abortWith(Id tokenID, ResponseCodeEnum cause) {
		if (tokenID != NO_PENDING_ID) {
			ids.reclaimLastId();
		}
		ledger.dropPendingTokenChanges();
		txnCtx.setStatus(cause);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenCreation;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenCreateTransactionBody op = txnBody.getTokenCreation();

		var validity = validator.memoCheck(op.getMemo());
		if (validity != OK) {
			return validity;
		}

		validity = validator.tokenSymbolCheck(op.getSymbol());
		if (validity != OK) {
			return validity;
		}

		validity = validator.tokenNameCheck(op.getName());
		if (validity != OK) {
			return validity;
		}

		validity = initialSupplyAndDecimalsCheck(op.getInitialSupply(), op.getDecimals());
		if (validity != OK) {
			return validity;
		}

		if (!op.hasTreasury()) {
			return INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
		}

		validity = checkKeys(
				op.hasAdminKey(), op.getAdminKey(),
				op.hasKycKey(), op.getKycKey(),
				op.hasWipeKey(), op.getWipeKey(),
				op.hasSupplyKey(), op.getSupplyKey(),
				op.hasFreezeKey(), op.getFreezeKey());
		if (validity != OK) {
			return validity;
		}

		if (op.getFreezeDefault() && !op.hasFreezeKey()) {
			return TOKEN_HAS_NO_FREEZE_KEY;
		}

		if (op.hasAutoRenewAccount()) {
			validity = validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod()) ? OK : INVALID_RENEWAL_PERIOD;
			return validity;
		} else {
			if (op.getExpiry().getSeconds() <= txnCtx.consensusTime().getEpochSecond()) {
				return INVALID_EXPIRATION_TIME;
			}
		}

		return OK;
	}

	private Account validateAccountId(AccountID id) {
		return accountStore.loadAccount(new Id(id.getShardNum(), id.getRealmNum(), id.getAccountNum()));
	}

	private long expiryOf(TokenCreateTransactionBody request, long now) {
		return request.hasAutoRenewAccount()
				? now + request.getAutoRenewPeriod().getSeconds()
				: request.getExpiry().getSeconds();
	}
}
