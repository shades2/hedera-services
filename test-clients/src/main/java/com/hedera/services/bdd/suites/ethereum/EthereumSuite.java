package com.hedera.services.bdd.suites.ethereum;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.ethereum.EthTxData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdateAliased;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class EthereumSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(EthereumSuite.class);

	private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";

	public static void main(String... args) {
		new EthereumSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				invalidTxData(),
				ETX_014_contractCreateInheritsSignerProperties()
		);
	}

	HapiApiSpec invalidTxData() {
		return defaultHapiSpec("InvalidTxData")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))    .via("autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords(),


						uploadInitCode(PAY_RECEIVABLE_CONTRACT)
				).when(
						ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
								.adminKey(THRESHOLD)
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.nonce(0)
								.gasPrice(10L)
								.maxGasAllowance(5L)
								.maxPriorityGas(2L)
								.invalidateEthereumData()
								.gasLimit(1_000_000L).hasPrecheck(INVALID_ETHEREUM_TRANSACTION)
								.via("payTxn")
				).then();
	}


	HapiApiSpec ETX_014_contractCreateInheritsSignerProperties() {
		final AtomicReference<String> contractID = new AtomicReference<>();
		final String MEMO = "memo";
		final String PROXY = "proxy";
		final long INITIAL_BALANCE = 100L;
		final long AUTO_RENEW_PERIOD = THREE_MONTHS_IN_SECONDS + 60;
		return defaultHapiSpec("ContractCreateInheritsProperties")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
								.via("autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords(),
						cryptoCreate(PROXY)
				).when(
						cryptoUpdateAliased(SECP_256K1_SOURCE_KEY)
								.autoRenewPeriod(AUTO_RENEW_PERIOD)
								.entityMemo(MEMO)
								.newProxy(PROXY)
								.payingWith(GENESIS)
								.signedBy(SECP_256K1_SOURCE_KEY, GENESIS),
						ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.nonce(0)
								.balance(INITIAL_BALANCE)
								.gasPrice(10L)
								.maxGasAllowance(5L)
								.maxPriorityGas(2L)
								.exposingNumTo(num -> contractID.set(
										asHexedSolidityAddress(0, 0, num)))
								.gasLimit(1_000_000L)
								.hasKnownStatus(SUCCESS),
						ethereumCall(PAY_RECEIVABLE_CONTRACT, "getBalance")
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.nonce(1L)
								.gasPrice(10L)
								.gasLimit(1_000_000L)
								.hasKnownStatus(SUCCESS)
				).then(
						getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).logged(),
						sourcing(() -> getContractInfo(contractID.get()).logged()
								.has(ContractInfoAsserts.contractWith()
										.adminKey(SECP_256K1_SOURCE_KEY)
										.autoRenew(AUTO_RENEW_PERIOD)
										.balance(INITIAL_BALANCE)
										.memo(MEMO))
						)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}