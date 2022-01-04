package com.hedera.services.bdd.spec.transactions.crypto;

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

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccountString;
import static com.hedera.services.bdd.spec.keys.DefaultKeyGen.DEFAULT_KEY_GEN;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.netOf;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiApiSuite.ONE_HBAR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiCryptoCreate extends HapiTxnOp<HapiCryptoCreate> {
	static final Logger log = LogManager.getLogger(HapiCryptoCreate.class);

	private static final SplittableRandom r = new SplittableRandom();

	/* Added to simplify wide-spread use of auto-created accounts in CI.
	(Can be overridden via the `creation.mode` in _spec-default.properties_.) */
	public enum Mode {
		NORMAL, ALIAS_TRANSFER, PSEUDORANDOM, UNSPECIFIED
	}

	private Key key;
	private Mode mode = Mode.UNSPECIFIED;
	private AccountID payerId;
	private ByteString alias;
	/**
	 * when create account were used as an account, whether perform auto recharge when
	 * receiving insufficient payer or insufficient transaction fee precheck
	 */
	private boolean recharging = false;
	private boolean advertiseCreation = false;
	private boolean forgettingEverything = false;
	/** The time window (unit of second) of not doing another recharge if just recharged recently */
	private String account;
	private Optional<Integer> rechargeWindow = Optional.empty();
	private Optional<Long> sendThresh = Optional.empty();
	private Optional<Long> receiveThresh = Optional.empty();
	private Optional<Long> initialBalance = Optional.empty();
	private Optional<Long> autoRenewDurationSecs = Optional.empty();
	private Optional<AccountID> proxy = Optional.empty();
	private Optional<Boolean> receiverSigRequired = Optional.empty();
	private Optional<String> keyName = Optional.empty();
	private Optional<String> entityMemo = Optional.empty();
	private Optional<KeyType> keyType = Optional.empty();
	private Optional<SigControl> keyShape = Optional.empty();
	private Optional<Function<HapiApiSpec, Long>> balanceFn = Optional.empty();
	private Optional<Integer> maxAutomaticTokenAssociations = Optional.empty();

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.CryptoCreate;
	}

	@Override
	protected Key lookupKey(HapiApiSpec spec, String name) {
		return name.equals(account) ? key : spec.registry().getKey(name);
	}

	public HapiCryptoCreate doViaAliasXfer() {
		mode = Mode.ALIAS_TRANSFER;
		return this;
	}

	public HapiCryptoCreate advertisingCreation() {
		advertiseCreation = true;
		return this;
	}

	public HapiCryptoCreate rememberingNothing() {
		forgettingEverything = true;
		return this;
	}

	public HapiCryptoCreate(String account) {
		this.account = account;
	}

	public HapiCryptoCreate(final String account, final Mode mode) {
		this.account = account;
		this.mode = mode;
	}

	public HapiCryptoCreate entityMemo(String memo) {
		entityMemo = Optional.of(memo);
		return this;
	}

	public HapiCryptoCreate sendThreshold(Long amount) {
		sendThresh = Optional.of(amount);
		return this;
	}

	public HapiCryptoCreate autoRenewSecs(long time) {
		autoRenewDurationSecs = Optional.of(time);
		return this;
	}

	public HapiCryptoCreate receiveThreshold(Long amount) {
		receiveThresh = Optional.of(amount);
		return this;
	}

	public HapiCryptoCreate receiverSigRequired(boolean isRequired) {
		receiverSigRequired = Optional.of(isRequired);
		return this;
	}

	public HapiCryptoCreate balance(Long amount) {
		initialBalance = Optional.of(amount);
		return this;
	}

	public HapiCryptoCreate maxAutomaticTokenAssociations(int max) {
		maxAutomaticTokenAssociations = Optional.of(max);
		return this;
	}

	public HapiCryptoCreate balance(Function<HapiApiSpec, Long> fn) {
		balanceFn = Optional.of(fn);
		return this;
	}

	public HapiCryptoCreate key(String name) {
		keyName = Optional.of(name);
		return this;
	}

	public HapiCryptoCreate key(Key key) {
		this.key = key;
		return this;
	}

	public HapiCryptoCreate keyType(KeyType type) {
		keyType = Optional.of(type);
		return this;
	}

	public HapiCryptoCreate withRecharging() {
		recharging = true;
		return this;
	}

	public HapiCryptoCreate rechargeWindow(int window) {
		rechargeWindow = Optional.of(window);
		return this;
	}

	public HapiCryptoCreate keyShape(SigControl controller) {
		keyShape = Optional.of(controller);
		return this;
	}

	public HapiCryptoCreate proxy(String idLit) {
		proxy = Optional.of(HapiPropertySource.asAccount(idLit));
		return this;
	}

	@Override
	protected HapiCryptoCreate self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		ensureModeFinalizedFor(spec);
		if (mode == Mode.NORMAL) {
			return spec.fees().forActivityBasedOp(
					HederaFunctionality.CryptoCreate, this::usageEstimate, txn, numPayerKeys);
		} else {
			/* Don't bother doing client-side fee calculation for the transfer-to-alias pattern. */
			return ONE_HBAR;
		}
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		ensureModeFinalizedFor(spec);
		key = key != null ? key : netOf(spec, keyName, keyShape, keyType, Optional.of(this::effectiveKeyGen));
		long amount = balanceFn.map(fn -> fn.apply(spec)).orElse(initialBalance.orElse(-1L));
		initialBalance = (amount >= 0) ? Optional.of(amount) : Optional.empty();
		return (mode == Mode.NORMAL) ? normalOpBody(spec) : xferToAliasBody(spec);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return Arrays.asList(
				spec -> spec.registry().getKey(effectivePayer(spec)),
				ignore -> key);
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls)::createAccount;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
		if (actualStatus != SUCCESS || forgettingEverything) {
			return;
		}
		if (recharging) {
			spec.registry().setRecharging(account, initialBalance.orElse(spec.setup().defaultBalance()));
			if (rechargeWindow.isPresent()) {
				spec.registry().setRechargingWindow(account, rechargeWindow.get());
			}
		}

		spec.registry().saveKey(account, key);
		if (mode == Mode.NORMAL) {
			spec.registry().saveAccountId(account, lastReceipt.getAccountID());
			receiverSigRequired.ifPresent(r -> spec.registry().saveSigRequirement(account, r));
		} else {
			completeAutoAccountCustomization(spec);
		}

		if (advertiseCreation) {
			String banner = "\n\n" + bannerWith(
					String.format(
							"Created account '%s' with id '0.0.%d'.",
							account,
							lastReceipt.getAccountID().getAccountNum()));
			log.info(banner);
		}
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("account", account);
		keyType.ifPresent(kt -> helper.add("keyType", kt));
		initialBalance.ifPresent(b -> helper.add("balance", b));
		Optional
				.ofNullable(lastReceipt)
				.ifPresent(receipt -> {
					if (receipt.getAccountID().getAccountNum() != 0) {
						helper.add("created", receipt.getAccountID().getAccountNum());
					}
				});
		return helper;
	}

	public long numOfCreatedAccount() {
		return Optional
				.ofNullable(lastReceipt)
				.map(receipt -> receipt.getAccountID().getAccountNum())
				.orElse(-1L);
	}

	private void completeAutoAccountCustomization(HapiApiSpec spec) {
		final var balanceLookup = getAliasedAccountBalance(account);
		allRunFor(spec, balanceLookup);
		final var info = balanceLookup.getResponse().getCryptogetAccountBalance();
		final var createdId = info.getAccountID();
		spec.registry().saveAccountId(account, createdId);

		final var actualBalance = info.getBalance();
		completeBalanceCustomization(spec, actualBalance);

		completePropertyCustomization();
	}

	private void completePropertyCustomization() {
		final var updateOp = cryptoUpdate(account);
		proxy.ifPresent(pId -> updateOp.newProxy(asAccountString(pId)));
		entityMemo.ifPresent(updateOp::entityMemo);
		receiverSigRequired.ifPresent(updateOp::receiverSigRequired);
		autoRenewDurationSecs.ifPresent(updateOp::autoRenewPeriod);
		maxAutomaticTokenAssociations.ifPresent(updateOp::maxAutomaticAssociations);
	}

	private void completeBalanceCustomization(final HapiApiSpec spec, final long actualBalance) {
		if (initialBalance.isEmpty()) {
			return;
		}
		final var delta = initialBalance.get() - actualBalance;
		if (delta > 0) {
			final var topUpBalance = cryptoTransfer(
					tinyBarsFromTo(GENESIS, account, delta)
			).fee(ONE_HBAR).signedBy(GENESIS);
			allRunFor(spec, topUpBalance);
		} else if (delta < 0) {
			final var topOffBalance = cryptoTransfer(
					tinyBarsFromTo(account, GENESIS, -delta)
			).fee(ONE_HBAR).signedBy(account);
			allRunFor(spec, topOffBalance);
		}
	}

	private Consumer<TransactionBody.Builder> xferToAliasBody(final HapiApiSpec spec) throws Throwable {
		/* Create a key for the new account (to be used as the alias) */
		var algo = spec.setup().defaultKeyAlgo();
		if (algo == SigControl.KeyAlgo.UNSPECIFIED) {
			algo = r.nextBoolean() ? SigControl.KeyAlgo.ED25519 : SigControl.KeyAlgo.SECP256K1;
		}
		final var shape = (algo == SigControl.KeyAlgo.ED25519) ? KeyShape.ED25519 : KeyShape.SECP256K1;
		final var key = spec.keys().generateSubjectTo(spec, shape, DEFAULT_KEY_GEN);
		spec.registry().saveKey(account, key);

		alias = key.toByteString();
		/* Send the initial ℏ from this operation's payer account */
		payerId = payer.isPresent()
				? TxnUtils.asId(payer.get(), spec)
				: spec.registry().getAccountID(spec.setup().defaultPayerName());
		final CryptoTransferTransactionBody opBody = spec
				.txns()
				.<CryptoTransferTransactionBody, CryptoTransferTransactionBody.Builder>body(
						CryptoTransferTransactionBody.class, b -> b.setTransfers(TransferList.newBuilder()
								.addAccountAmounts(aaWith(alias, +ONE_HBAR))
								.addAccountAmounts(aaWith(payerId, -ONE_HBAR))
								.build()));
		return b -> b.setCryptoTransfer(opBody);
	}

	private AccountAmount aaWith(final AccountID account, final long amount) {
		return AccountAmount.newBuilder().setAccountID(account).setAmount(amount).build();
	}

	private AccountAmount aaWith(final ByteString alias, final long amount) {
		return AccountAmount.newBuilder().setAccountID(AccountID.newBuilder().setAlias(alias)).setAmount(amount).build();
	}

	private Consumer<TransactionBody.Builder> normalOpBody(final HapiApiSpec spec) throws Throwable {
		final CryptoCreateTransactionBody opBody = spec
				.txns()
				.<CryptoCreateTransactionBody, CryptoCreateTransactionBody.Builder>body(
						CryptoCreateTransactionBody.class, b -> {
							b.setKey(key);
							proxy.ifPresent(b::setProxyAccountID);
							entityMemo.ifPresent(b::setMemo);
							sendThresh.ifPresent(b::setSendRecordThreshold);
							receiveThresh.ifPresent(b::setReceiveRecordThreshold);
							initialBalance.ifPresent(b::setInitialBalance);
							receiverSigRequired.ifPresent(b::setReceiverSigRequired);
							autoRenewDurationSecs.ifPresent(
									s -> b.setAutoRenewPeriod(Duration.newBuilder().setSeconds(s).build()));
							maxAutomaticTokenAssociations.ifPresent(b::setMaxAutomaticTokenAssociations);
						});
		return b -> b.setCryptoCreateAccount(opBody);
	}

	private void ensureModeFinalizedFor(final HapiApiSpec spec) {
		if (mode == Mode.UNSPECIFIED) {
			mode = spec.setup().creationMode();
		}
		if (mode == Mode.PSEUDORANDOM) {
			mode = r.nextBoolean() ? Mode.ALIAS_TRANSFER : Mode.NORMAL;
		}
	}

	private FeeData usageEstimate(TransactionBody txn, SigValueObj svo) {
		var baseMeta = new BaseTransactionMeta(txn.getMemoBytes().size(), 0);
		var opMeta = new CryptoCreateMeta(txn.getCryptoCreateAccount());
		var accumulator = new UsageAccumulator();
		cryptoOpsUsage.cryptoCreateUsage(suFrom(svo), baseMeta, opMeta, accumulator);
		return AdapterUtils.feeDataFrom(accumulator);
	}
}
