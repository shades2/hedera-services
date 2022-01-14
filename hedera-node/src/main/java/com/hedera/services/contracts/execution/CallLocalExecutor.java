package com.hedera.services.contracts.execution;

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

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.ResponseCodeUtil;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.CommonUtils;
import org.apache.tuweni.bytes.Bytes;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;

/**
 * Endpoint for executing static EVM calls. The singleton wraps {@link CallLocalEvmTxProcessor} providing interface
 * to {@link com.hedera.services.queries.contract.ContractCallLocalAnswer} and
 * {@link com.hedera.services.fees.calculation.contract.queries.ContractCallLocalResourceUsage}
 */
@Singleton
public class CallLocalExecutor {
	private final AccountStore accountStore;
	private final CallLocalEvmTxProcessor evmTxProcessor;

	@Inject
	public CallLocalExecutor(
			AccountStore accountStore,
			CallLocalEvmTxProcessor evmTxProcessor
	) {
		this.accountStore = accountStore;
		this.evmTxProcessor = evmTxProcessor;
	}

	/**
	 * Executes the specified {@link ContractCallLocalQuery} through a static call. Parses the result from the
	 * {@link CallLocalEvmTxProcessor} and sets the appropriate {@link com.hederahashgraph.api.proto.java.ResponseCode}
	 *
	 * @param op
	 * 		the query to asnwer
	 * @return {@link ContractCallLocalResponse} result of the execution
	 */
	public ContractCallLocalResponse execute(ContractCallLocalQuery op) {

		try {
			TransactionBody body =
					SignedTxnAccessor.uncheckedFrom(op.getHeader().getPayment()).getTxn();
			final var grpcSender = body.getTransactionID().getAccountID();
			final long senderAccountNum = accountStore.getAccountNumFromAlias(grpcSender.getAlias(),
					grpcSender.getAccountNum());
			final var senderId = new Id(grpcSender.getShardNum(), grpcSender.getRealmNum(), senderAccountNum);

			final var contractId = Id.fromGrpcContract(op.getContractID());

			/* --- Load the model objects --- */
			final var sender = accountStore.loadAccount(senderId);
			final var receiver = accountStore.loadContract(contractId);
			final var callData = !op.getFunctionParameters().isEmpty()
					? Bytes.fromHexString(CommonUtils.hex(op.getFunctionParameters().toByteArray())) : Bytes.EMPTY;

			/* --- Do the business logic --- */
			final var result = evmTxProcessor.execute(
					sender,
					receiver.getId().asEvmAddress(),
					op.getGas(),
					0,
					callData,
					Instant.now());

			var status = ResponseCodeUtil.getStatus(result, OK);

			final var responseHeader = RequestBuilder.getResponseHeader(status, 0L,
					ANSWER_ONLY, ByteString.EMPTY);

			return ContractCallLocalResponse
					.newBuilder()
					.setHeader(responseHeader)
					.setFunctionResult(result.toGrpc())
					.build();
		} catch (InvalidTransactionException ite) {
			final var responseHeader = RequestBuilder.getResponseHeader(ite.getResponseCode(), 0L,
					ANSWER_ONLY, ByteString.EMPTY);

			return ContractCallLocalResponse.newBuilder().setHeader(responseHeader).build();
		}
	}
}
