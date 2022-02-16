package com.hedera.services.bdd.spec.transactions;

/*-
 * ‌
 * Hedera Services Test Clients
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
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.crypto.ReferenceType;
import com.hedera.services.bdd.spec.transactions.consensus.HapiMessageSubmit;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicCreate;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicDelete;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicUpdate;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCreate;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractDelete;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractUpdate;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoAdjustAllowance;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoApproveAllowance;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoCreate;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoDelete;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoUpdate;
import com.hedera.services.bdd.spec.transactions.file.HapiFileAppend;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hedera.services.bdd.spec.transactions.file.HapiFileDelete;
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;
import com.hedera.services.bdd.spec.transactions.network.HapiUncheckedSubmit;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleDelete;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleSign;
import com.hedera.services.bdd.spec.transactions.system.HapiFreeze;
import com.hedera.services.bdd.spec.transactions.system.HapiSysDelete;
import com.hedera.services.bdd.spec.transactions.system.HapiSysUndelete;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenAssociate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenBurn;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenDelete;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenDissociate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenFeeScheduleUpdate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenFreeze;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenKycGrant;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenKycRevoke;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenMint;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenPause;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUnfreeze;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUnpause;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUpdate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenWipe;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.FunctionType.CONSTRUCTOR;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.FunctionType.FUNCTION;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static org.apache.commons.lang3.StringUtils.EMPTY;

public class TxnVerbs {
	// TODO: After refactor: remove the temporary new structure folder and refactor the bellow path
	public static final String RESOURCE_PATH = "src/main/resource/contract/newstructure/%1$s/%1$s";

	/* CRYPTO */
	public static HapiCryptoCreate cryptoCreate(String account) {
		return new HapiCryptoCreate(account);
	}

	public static HapiCryptoDelete cryptoDelete(String account) {
		return new HapiCryptoDelete(account);
	}

	public static HapiCryptoDelete cryptoDeleteAliased(final String alias) {
		return new HapiCryptoDelete(alias, ReferenceType.ALIAS_KEY_NAME);
	}

	@SafeVarargs
	public static HapiCryptoTransfer sortedCryptoTransfer(Function<HapiApiSpec, TransferList>... providers) {
		return new HapiCryptoTransfer(true, providers);
	}

	@SafeVarargs
	public static HapiCryptoTransfer cryptoTransfer(Function<HapiApiSpec, TransferList>... providers) {
		return new HapiCryptoTransfer(providers);
	}

	public static HapiCryptoTransfer cryptoTransfer(BiConsumer<HapiApiSpec, CryptoTransferTransactionBody.Builder> def) {
		return new HapiCryptoTransfer(def);
	}

	public static HapiCryptoTransfer cryptoTransfer(TokenMovement... sources) {
		return new HapiCryptoTransfer(sources);
	}

	public static HapiCryptoUpdate cryptoUpdate(String account) {
		return new HapiCryptoUpdate(account);
	}

	public static HapiCryptoUpdate cryptoUpdateAliased(final String alias) {
		return new HapiCryptoUpdate(alias, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiCryptoApproveAllowance cryptoApproveAllowance() {
		return new HapiCryptoApproveAllowance();
	}

	public static HapiCryptoAdjustAllowance cryptoAdjustAllowance() {
		return new HapiCryptoAdjustAllowance();
	}

	/* CONSENSUS */
	public static HapiTopicCreate createTopic(String topic) {
		return new HapiTopicCreate(topic);
	}

	public static HapiTopicDelete deleteTopic(String topic) {
		return new HapiTopicDelete(topic);
	}

	public static HapiTopicDelete deleteTopic(Function<HapiApiSpec, TopicID> topicFn) {
		return new HapiTopicDelete(topicFn);
	}

	public static HapiTopicUpdate updateTopic(String topic) {
		return new HapiTopicUpdate(topic);
	}

	public static HapiMessageSubmit submitMessageTo(String topic) {
		return new HapiMessageSubmit(topic);
	}

	public static HapiMessageSubmit submitMessageTo(Function<HapiApiSpec, TopicID> topicFn) {
		return new HapiMessageSubmit(topicFn);
	}

	/* FILE */
	public static HapiFileCreate fileCreate(String fileName) {
		return new HapiFileCreate(fileName);
	}

	public static HapiFileAppend fileAppend(String fileName) {
		return new HapiFileAppend(fileName);
	}

	public static HapiFileUpdate fileUpdate(String fileName) {
		return new HapiFileUpdate(fileName);
	}

	public static HapiFileDelete fileDelete(String fileName) {
		return new HapiFileDelete(fileName);
	}

	public static HapiFileDelete fileDelete(Supplier<String> fileNameSupplier) {
		return new HapiFileDelete(fileNameSupplier);
	}

	/* TOKEN */
	public static HapiTokenDissociate tokenDissociate(String account, String... tokens) {
		return new HapiTokenDissociate(account, tokens);
	}

	public static HapiTokenAssociate tokenAssociate(String account, String... tokens) {
		return new HapiTokenAssociate(account, tokens);
	}

	public static HapiTokenAssociate tokenAssociate(String account, List<String> tokens) {
		return new HapiTokenAssociate(account, tokens);
	}

	public static HapiTokenCreate tokenCreate(String token) {
		return new HapiTokenCreate(token).name(token);
	}

	public static HapiTokenUpdate tokenUpdate(String token) {
		return new HapiTokenUpdate(token);
	}

	public static HapiTokenFeeScheduleUpdate tokenFeeScheduleUpdate(String token) {
		return new HapiTokenFeeScheduleUpdate(token);
	}

	public static HapiTokenPause tokenPause(String token) {
		return new HapiTokenPause(token);
	}

	public static HapiTokenUnpause tokenUnpause(String token) {
		return new HapiTokenUnpause(token);
	}

	public static HapiTokenDelete tokenDelete(String token) {
		return new HapiTokenDelete(token);
	}

	public static HapiTokenFreeze tokenFreeze(String token, String account) {
		return new HapiTokenFreeze(token, account);
	}

	public static HapiTokenUnfreeze tokenUnfreeze(String token, String account) {
		return new HapiTokenUnfreeze(token, account);
	}

	public static HapiTokenKycGrant grantTokenKyc(String token, String account) {
		return new HapiTokenKycGrant(token, account);
	}

	public static HapiTokenKycRevoke revokeTokenKyc(String token, String account) {
		return new HapiTokenKycRevoke(token, account);
	}

	public static HapiTokenWipe wipeTokenAccount(String token, String account, long amount) {
		return new HapiTokenWipe(token, account, amount);
	}

	public static HapiTokenWipe wipeTokenAccount(String token, String account, List<Long> serialNumbers) {
		return new HapiTokenWipe(token, account, serialNumbers);
	}

	public static HapiTokenMint mintToken(String token, long amount) {
		return new HapiTokenMint(token, amount);
	}

	public static HapiTokenMint mintToken(String token, List<ByteString> meta, String txName) {
		return new HapiTokenMint(token, meta, txName);
	}

	public static HapiTokenMint mintToken(String token, List<ByteString> metadata) {
		return new HapiTokenMint(token, metadata);
	}

	public static HapiTokenMint invalidMintToken(String token, List<ByteString> metadata, long amount) {
		return new HapiTokenMint(token, metadata, amount);
	}

	public static HapiTokenBurn burnToken(String token, long amount) {
		return new HapiTokenBurn(token, amount);
	}

	public static HapiTokenBurn burnToken(String token, List<Long> serialNumbers) {
		return new HapiTokenBurn(token, serialNumbers);
	}

	public static HapiTokenBurn invalidBurnToken(String token, List<Long> serialNumbers, long amount) {
		return new HapiTokenBurn(token, serialNumbers, amount);
	}

	/* SCHEDULE */
	public static <T extends HapiTxnOp<T>> HapiScheduleCreate<T> scheduleCreate(String scheduled, HapiTxnOp<T> txn) {
		return new HapiScheduleCreate<>(scheduled, txn);
	}

	public static HapiScheduleSign scheduleSign(String schedule) {
		return new HapiScheduleSign(schedule);
	}

	public static HapiScheduleCreate<HapiCryptoCreate> scheduleCreateFunctionless(String scheduled) {
		return new HapiScheduleCreate<>(scheduled, cryptoCreate("doomed")).functionless();
	}

	public static HapiScheduleDelete scheduleDelete(String schedule) {
		return new HapiScheduleDelete(schedule);
	}

	/* SYSTEM */
	public static HapiSysDelete systemFileDelete(String target) {
		return new HapiSysDelete().file(target);
	}

	public static HapiSysUndelete systemFileUndelete(String target) {
		return new HapiSysUndelete().file(target);
	}


	public static HapiSysDelete systemContractDelete(String target) {
		return new HapiSysDelete().contract(target);
	}

	public static HapiSysUndelete systemContractUndelete(String target) {
		return new HapiSysUndelete().contract(target);
	}

	/* NETWORK */
	public static <T extends HapiTxnOp<T>> HapiUncheckedSubmit<T> uncheckedSubmit(HapiTxnOp<T> subOp) {
		return new HapiUncheckedSubmit<>(subOp);
	}

	/* SMART CONTRACT */
	public static HapiContractCall contractCallFrom(String details) {
		return HapiContractCall.fromDetails(details);
	}

	public static HapiContractCall contractCall(String contract) {
		return new HapiContractCall(contract);
	}

	// The ternary operator enables the developer to pass either an ABI, or just the name of the function
	public static HapiContractCall contractCall(String contract, String abi, Object... params) {
		return abi.charAt(0) == '{'
				? new HapiContractCall(abi, contract, params)
				: new HapiContractCall(getABIFor(FUNCTION, abi, contract), contract, params);
	}

	public static HapiContractCall contractCall(String contract, String abi, Function<HapiApiSpec, Object[]> fn) {
		return new HapiContractCall(abi, contract, fn);
	}

	public static HapiContractCreate contractCreate(String contract) {
		return new HapiContractCreate(contract);
	}

	public static HapiContractCreate contractCreate(String contract, String abi, Object... params) {
		return new HapiContractCreate(contract, abi, params);
	}

	public static HapiContractDelete contractDelete(String contract) {
		return new HapiContractDelete(contract);
	}

	public static HapiContractUpdate contractUpdate(String contract) {
		return new HapiContractUpdate(contract);
	}

	public static HapiSpecOperation contractDeploy(final String contractName, final Object... params) {
		return withOpContext((spec, ctxLog) -> {
			final var path = String.format(RESOURCE_PATH + ".bin", contractName);
			validateFileExists(path);
			final var payer = cryptoCreate("PAYER");
			final var file = fileCreate(contractName);
			final var updatedFile = updateLargeFile("PAYER", contractName, extractByteCode(path));

			HapiContractCreate contract;

			if (params.length > 0) {
				final var constructorABI = getABIFor(CONSTRUCTOR, EMPTY, contractName);
				contract = contractCreate(contractName, constructorABI, params).bytecode(contractName);
			} else {
				contract = contractCreate(contractName).bytecode(contractName);
			}
			allRunFor(spec, payer, file, updatedFile, contract);
		});
	}

	/* SYSTEM */
	public static HapiFreeze hapiFreeze(final Instant freezeStartTime) {
		return new HapiFreeze().startingAt(freezeStartTime);
	}

	private static String getABIFor(final FunctionType type, final String functionName, String contractName) {
		final var path = String.format(RESOURCE_PATH + ".json", contractName);
		validateFileExists(path);
		var ABI = EMPTY;
		try (final var input = new FileInputStream(path)) {
			final var array = new JSONArray(new JSONTokener(input));
			ABI = IntStream
					.range(0, array.length())
					.mapToObj(array::getJSONObject)
					.filter(object -> type == CONSTRUCTOR
							? object.getString("type").equals(type.toString().toLowerCase())
							: object.getString("type").equals(type.toString().toLowerCase()) && object.getString("name").equals(functionName))
					.map(JSONObject::toString)
					.findFirst()
					.orElseThrow(() -> new IllegalArgumentException("No such function found: " + functionName));
		} catch (IOException e) {
			e.getStackTrace();
		}
		return ABI;
	}

	private static void validateFileExists(String path) {
		final var file = new File(path);
		if (!file.exists()) {
			throw new IllegalArgumentException("Invalid argument: " + path.substring(path.lastIndexOf('/') + 1));
		}
	}

	public enum FunctionType {CONSTRUCTOR, FUNCTION}
}
