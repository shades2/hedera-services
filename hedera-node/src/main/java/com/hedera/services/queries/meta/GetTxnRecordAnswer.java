package com.hedera.services.queries.meta;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.records.RecordCache;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordResponse;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetRecord;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

@Singleton
public class GetTxnRecordAnswer implements AnswerService {
	private final RecordCache recordCache;
	private final AnswerFunctions answerFunctions;
	private final OptionValidator optionValidator;
	private final AliasManager aliasManager;

	public static final String PRIORITY_RECORD_CTX_KEY =
			GetTxnRecordAnswer.class.getSimpleName() + "_priorityRecord";
	public static final String DUPLICATE_RECORDS_CTX_KEY =
			GetTxnRecordAnswer.class.getSimpleName() + "_duplicateRecords";
	public static final String CHILD_RECORDS_CTX_KEY =
			GetTxnRecordAnswer.class.getSimpleName() + "_childRecords";

	@Inject
	public GetTxnRecordAnswer(
			final RecordCache recordCache,
			final OptionValidator optionValidator,
			final AnswerFunctions answerFunctions,
			final AliasManager aliasManager
	) {
		this.recordCache = recordCache;
		this.answerFunctions = answerFunctions;
		this.optionValidator = optionValidator;
		this.aliasManager = aliasManager;
	}

	@Override
	public boolean needsAnswerOnlyCost(final Query query) {
		return COST_ANSWER == query.getTransactionGetRecord().getHeader().getResponseType();
	}

	@Override
	public boolean requiresNodePayment(final Query query) {
		return typicallyRequiresNodePayment(query.getTransactionGetRecord().getHeader().getResponseType());
	}

	@Override
	public ResponseCodeEnum extractValidityFrom(final Response response) {
		return response.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode();
	}

	@Override
	public Response responseGiven(
			final Query query,
			final StateView view,
			final ResponseCodeEnum validity,
			final long cost
	) {
		return responseFor(query, view, validity, cost, NO_QUERY_CTX);
	}

	@Override
	public Response responseGiven(
			final Query query,
			final StateView view,
			final ResponseCodeEnum validity,
			final long cost,
			final Map<String, Object> queryCtx
	) {
		return responseFor(query, view, validity, cost, Optional.of(queryCtx));
	}

	private Response responseFor(
			final Query query,
			final StateView view,
			final ResponseCodeEnum validity,
			final long cost,
			final Optional<Map<String, Object>> queryCtx
	) {
		final var op = query.getTransactionGetRecord();
		final var response = TransactionGetRecordResponse.newBuilder();

		final var type = op.getHeader().getResponseType();
		if (validity != OK) {
			response.setHeader(header(validity, type, cost));
		} else {
			if (type == COST_ANSWER) {
				response.setHeader(costAnswerHeader(OK, cost));
			} else {
				setAnswerOnly(response, view, op, queryCtx);
			}
		}

		return Response.newBuilder()
				.setTransactionGetRecord(response)
				.build();
	}

	@SuppressWarnings("unchecked")
	private void setAnswerOnly(
			final TransactionGetRecordResponse.Builder response,
			final StateView view,
			final TransactionGetRecordQuery op,
			final Optional<Map<String, Object>> queryCtx
	) {
		if (queryCtx.isPresent()) {
			final var ctx = queryCtx.get();
			if (!ctx.containsKey(PRIORITY_RECORD_CTX_KEY)) {
				response.setHeader(answerOnlyHeader(RECORD_NOT_FOUND));
			} else {
				response.setHeader(answerOnlyHeader(OK));
				response.setTransactionRecord((TransactionRecord) ctx.get(PRIORITY_RECORD_CTX_KEY));
				if (op.getIncludeDuplicates()) {
					response.addAllDuplicateTransactionRecords(
							(List<TransactionRecord>) ctx.get(DUPLICATE_RECORDS_CTX_KEY));
				}
				if (op.getIncludeChildRecords()) {
					response.addAllChildTransactionRecords(
							(List<TransactionRecord>) ctx.get(CHILD_RECORDS_CTX_KEY));
				}
			}
		} else {
			final var txnRecord = answerFunctions.txnRecord(recordCache, view, op);
			if (txnRecord.isEmpty()) {
				response.setHeader(answerOnlyHeader(RECORD_NOT_FOUND));
			} else {
				response.setHeader(answerOnlyHeader(OK));
				response.setTransactionRecord(txnRecord.get());
				if (op.getIncludeDuplicates()) {
					response.addAllDuplicateTransactionRecords(
							recordCache.getDuplicateRecords(op.getTransactionID()));
				}
				if (op.getIncludeChildRecords()) {
					response.addAllChildTransactionRecords(
							recordCache.getChildRecords(op.getTransactionID()));
				}
			}
		}
	}

	@Override
	public ResponseCodeEnum checkValidity(final Query query, final StateView view) {
		final var txnId = query.getTransactionGetRecord().getTransactionID();
		final var fallbackId = txnId.getAccountID();
		final var validation = aliasManager.lookUpAccountID(fallbackId);

		if (validation.response() != OK) {
			return validation.response();
		}

		return optionValidator.queryableAccountStatus(validation.resolvedId(), view.accounts());
	}

	@Override
	public HederaFunctionality canonicalFunction() {
		return TransactionGetRecord;
	}

	@Override
	public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
		final var paymentTxn = query.getTransactionGetRecord().getHeader().getPayment();
		return Optional.ofNullable(SignedTxnAccessor.uncheckedFrom(paymentTxn));
	}
}