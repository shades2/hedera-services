package com.hedera.services.context.properties;

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

import com.hedera.services.fees.calculation.CongestionMultipliers;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static java.util.Map.entry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({LogCaptureExtension.class})
class BootstrapPropertiesTest {
	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private BootstrapProperties subject = new BootstrapProperties();

	private static final String STD_PROPS_RESOURCE = "bootstrap/standard.properties";
	private static final String INVALID_PROPS_RESOURCE = "bootstrap/not.properties";
	private static final String UNREADABLE_PROPS_RESOURCE = "bootstrap/unreadable.properties";
	private static final String INCOMPLETE_STD_PROPS_RESOURCE = "bootstrap/incomplete.properties";

	private static final String OVERRIDE_PROPS_LOC = "src/test/resources/bootstrap/override.properties";
	private static final String EMPTY_OVERRIDE_PROPS_LOC = "src/test/resources/bootstrap/empty-override.properties";

	private static final Map<String, Object> expectedProps = Map.ofEntries(
			entry("bootstrap.feeSchedulesJson.resource", "feeSchedules.json"),
			entry("bootstrap.genesisB64Keystore.keyName", "START_ACCOUNT"),
			entry("bootstrap.genesisB64Keystore.path", "data/onboard/StartUpAccount.txt"),
			entry("bootstrap.genesisPemPassphrase.path", "TBD"),
			entry("bootstrap.genesisPem.path", "TBD"),
			entry("bootstrap.hapiPermissions.path", "data/config/api-permission.properties"),
			entry("bootstrap.networkProperties.path", "data/config/application.properties"),
			entry("bootstrap.rates.currentHbarEquiv", 1),
			entry("bootstrap.rates.currentCentEquiv", 12),
			entry("bootstrap.rates.currentExpiry", 4102444800L),
			entry("bootstrap.rates.nextHbarEquiv", 1),
			entry("bootstrap.rates.nextCentEquiv", 15),
			entry("bootstrap.rates.nextExpiry", 4102444800L),
			entry("bootstrap.system.entityExpiry", 1654819200L),
			entry("bootstrap.throttleDefsJson.resource", "throttles.json"),
			entry("accounts.addressBookAdmin", 55L),
			entry("balances.exportDir.path", "/opt/hgcapp/accountBalances/"),
			entry("balances.exportEnabled", true),
			entry("balances.exportPeriodSecs", 900),
			entry("balances.exportTokenBalances", true),
			entry("balances.nodeBalanceWarningThreshold", 0L),
			entry("accounts.exchangeRatesAdmin", 57L),
			entry("accounts.feeSchedulesAdmin", 56L),
			entry("accounts.freezeAdmin", 58L),
			entry("accounts.systemAdmin", 50L),
			entry("accounts.systemDeleteAdmin", 59L),
			entry("accounts.systemUndeleteAdmin", 60L),
			entry("accounts.treasury", 2L),
			entry("contracts.allowCreate2", true),
			entry("contracts.defaultLifetime", 7890000L),
			entry("contracts.localCall.estRetBytes", 32),
			entry("contracts.maxGas", 300000),
			entry("contracts.maxKvPairs.aggregate", 500_000_000L),
			entry("contracts.maxKvPairs.individual", 163_840),
			entry("contracts.chainId", 1),
			entry("contracts.enableTraceability", true),
			entry("contracts.throttle.throttleByGas", true),
			entry("contracts.maxRefundPercentOfGasLimit", 20),
			entry("contracts.frontendThrottleMaxGasLimit", 5000000L),
			entry("contracts.consensusThrottleMaxGasLimit", 15000000L),
			entry("contracts.redirectTokenCalls", false),
			entry("contracts.precompile.htsDefaultGasCost", 10000L),
			entry("contracts.precompile.exportRecordResults", false),
			entry("dev.onlyDefaultNodeListens", true),
			entry("dev.defaultListeningNodeAccount", "0.0.3"),
			entry("entities.maxLifetime", 3153600000L),
			entry("fees.percentCongestionMultipliers", CongestionMultipliers.from("90,10x,95,25x,99,100x")),
			entry("fees.minCongestionPeriod", 60),
			entry("files.addressBook", 101L),
			entry("files.networkProperties", 121L),
			entry("files.exchangeRates", 112L),
			entry("files.feeSchedules", 111L),
			entry("files.hapiPermissions", 122L),
			entry("files.throttleDefinitions", 123L),
			entry("files.nodeDetails", 102L),
			entry("files.softwareUpdateRange", Pair.of(150L, 159L)),
			entry("grpc.port", 50211),
			entry("grpc.tlsPort", 50212),
			entry("hedera.accountsExportPath", "data/onboard/exportedAccount.txt"),
			entry("hedera.exportAccountsOnStartup", false),
			entry("hedera.numReservedSystemEntities", 1_000L),
			entry("hedera.prefetch.queueCapacity", 10000),
			entry("hedera.prefetch.threadPoolSize", 2),
			entry("hedera.prefetch.codeCacheTtlSecs", 120),
			entry("hedera.profiles.active", Profile.PROD),
			entry("hedera.realm", 0L),
			entry("hedera.recordStream.logDir", "/opt/hgcapp/recordStreams"),
			entry("hedera.recordStream.logPeriod", 2L),
			entry("hedera.recordStream.isEnabled", true),
			entry("hedera.recordStream.queueCapacity", 5000),
			entry("hedera.shard", 0L),
			entry("hedera.transaction.maxMemoUtf8Bytes", 100),
			entry("hedera.transaction.minValidDuration", 15L),
			entry("hedera.transaction.maxValidDuration", 180L),
			entry("hedera.transaction.minValidityBufferSecs", 10),
			entry("ledger.id", "0x03"),
			entry("ledger.changeHistorian.memorySecs", 20),
			entry("ledger.fundingAccount", 98L),
			entry("ledger.maxAccountNum", 100_000_000L),
			entry("ledger.numSystemAccounts", 100),
			entry("ledger.records.maxQueryableByAccount", 180),
			entry("ledger.transfers.maxLen", 10),
			entry("ledger.tokenTransfers.maxLen", 10),
			entry("ledger.totalTinyBarFloat", 5000000000000000000L),
			entry("autoCreation.enabled", true),
			entry("autorenew.isEnabled", false),
			entry("autorenew.numberOfEntitiesToScan", 100),
			entry("autorenew.maxNumberOfEntitiesToRenewOrDelete", 2),
			entry("autorenew.gracePeriod", 604800L),
			entry("ledger.autoRenewPeriod.maxDuration", 8000001L),
			entry("ledger.autoRenewPeriod.minDuration", 6999999L),
			entry("ledger.schedule.txExpiryTimeSecs", 1800),
			entry("iss.dumpFcms", false),
			entry("iss.resetPeriod", 60),
			entry("iss.roundsToDump", 5000),
			entry("netty.mode", Profile.PROD),
			entry("netty.prod.flowControlWindow", 10240),
			entry("netty.prod.maxConcurrentCalls", 10),
			entry("netty.prod.maxConnectionAge", 15L),
			entry("netty.prod.maxConnectionAgeGrace", 5L),
			entry("netty.prod.maxConnectionIdle", 10L),
			entry("netty.prod.keepAliveTime", 10L),
			entry("netty.prod.keepAliveTimeout", 3L),
			entry("netty.startRetries", 90),
			entry("netty.startRetryIntervalMs", 1_000L),
			entry("netty.tlsCrt.path", "hedera.crt"),
			entry("netty.tlsKey.path", "hedera.key"),
			entry("queries.blob.lookupRetries", 3),
			entry("tokens.maxPerAccount", 1_000),
			entry("tokens.maxSymbolUtf8Bytes", 100),
			entry("tokens.maxTokenNameUtf8Bytes", 100),
			entry("tokens.maxCustomFeesAllowed", 10),
			entry("tokens.maxCustomFeeDepth", 2),
			entry("files.maxSizeKb", 1024),
			entry("fees.tokenTransferUsageMultiplier", 380),
			entry("cache.records.ttl", 180),
			entry("rates.intradayChangeLimitPercent", 25),
			entry("rates.midnightCheckInterval", 1L),
			entry("scheduling.whitelist", Set.of(
					CryptoTransfer,
					TokenMint,
					TokenBurn,
					ConsensusSubmitMessage)),
			entry("scheduling.triggerTxn.windBackNanos", 11L),
			entry("sigs.expandFromLastSignedState", true),
			entry("stats.runningAvgHalfLifeSecs", 10.0),
			entry("stats.hapiOps.speedometerUpdateIntervalMs", 3_000L),
			entry("stats.speedometerHalfLifeSecs", 10.0),
			entry("stats.executionTimesToTrack", 0),
			entry("consensus.message.maxBytesAllowed", 1024),
			entry("ledger.nftTransfers.maxLen", 10),
			entry("ledger.xferBalanceChanges.maxLen", 20),
			entry("tokens.nfts.areEnabled", true),
			entry("tokens.nfts.areQueriesEnabled", true),
			entry("tokens.nfts.useTreasuryWildcards", true),
			entry("tokens.nfts.maxQueryRange", 100L),
			entry("tokens.nfts.maxBatchSizeWipe", 10),
			entry("tokens.nfts.maxBatchSizeMint", 10),
			entry("tokens.nfts.maxBatchSizeBurn", 10),
			entry("tokens.nfts.maxMetadataBytes", 100),
			entry("tokens.nfts.maxAllowedMints", 5000000L),
			entry("tokens.nfts.mintThrottleScaleFactor", ThrottleReqOpsScaleFactor.from("5:2")),
			entry("upgrade.artifacts.path", "/opt/hgcapp/services-hedera/HapiApp2.0/data/upgrade/current"),
			entry("hedera.allowances.maxTransactionLimit", 20),
			entry("hedera.allowances.maxAccountLimit", 100)
	);

	@Test
	void containsProperty() {
		assertTrue(subject.containsProperty("tokens.nfts.maxQueryRange"));
	}

	@BeforeEach
	void setUp() {
		subject.bootstrapOverridePropsLoc = EMPTY_OVERRIDE_PROPS_LOC;
	}

	@Test
	void throwsIseIfUnreadable() {
		subject.bootstrapPropsResource = UNREADABLE_PROPS_RESOURCE;

		final var ise = assertThrows(IllegalStateException.class, subject::ensureProps);
		final var msg = String.format("'%s' contains unrecognized properties:", UNREADABLE_PROPS_RESOURCE);
		assertTrue(ise.getMessage().startsWith(msg));
	}

	@Test
	void throwsIseIfIoExceptionOccurs() {
		final var bkup = BootstrapProperties.resourceStreamProvider;
		subject.bootstrapPropsResource = STD_PROPS_RESOURCE;
		BootstrapProperties.resourceStreamProvider = ignore -> {
			throw new IOException("Oops!");
		};

		final var ise = assertThrows(IllegalStateException.class, subject::ensureProps);
		final var msg = String.format("'%s' could not be loaded!", STD_PROPS_RESOURCE);
		assertEquals(msg, ise.getMessage());

		BootstrapProperties.resourceStreamProvider = bkup;
	}

	@Test
	void throwsIseIfInvalid() {
		subject.bootstrapPropsResource = INVALID_PROPS_RESOURCE;

		final var ise = assertThrows(IllegalStateException.class, subject::ensureProps);
		final var msg = String.format("'%s' contains unrecognized properties:", INVALID_PROPS_RESOURCE);
		assertTrue(ise.getMessage().startsWith(msg));
	}

	@Test
	void ensuresFilePropsFromExtant() {
		subject.bootstrapPropsResource = STD_PROPS_RESOURCE;

		subject.ensureProps();

		for (String name : BootstrapProperties.BOOTSTRAP_PROP_NAMES) {
			assertEquals(expectedProps.get(name), subject.getProperty(name), name + " has the wrong value!");
		}
		assertEquals(expectedProps, subject.bootstrapProps);
	}

	@Test
	void includesOverrides() {
		subject.bootstrapPropsResource = STD_PROPS_RESOURCE;
		subject.bootstrapOverridePropsLoc = OVERRIDE_PROPS_LOC;

		subject.ensureProps();

		assertEquals(30, subject.getProperty("tokens.maxPerAccount"));
	}

	@Test
	void doesntThrowOnMissingOverridesFile() {
		subject.bootstrapPropsResource = STD_PROPS_RESOURCE;
		subject.bootstrapOverridePropsLoc = "im-not-here";

		assertDoesNotThrow(subject::ensureProps);
	}

	@Test
	void throwsIaeOnMissingPropRequest() {
		subject.bootstrapPropsResource = STD_PROPS_RESOURCE;

		subject.ensureProps();

		final var ise = assertThrows(IllegalArgumentException.class, () -> subject.getProperty("not-a-real-prop"));
		assertEquals("Argument 'name=not-a-real-prop' is invalid!", ise.getMessage());
	}

	@Test
	void throwsIseIfMissingProps() {
		subject.bootstrapPropsResource = INCOMPLETE_STD_PROPS_RESOURCE;

		final var ise = assertThrows(IllegalStateException.class, subject::ensureProps);
		final var msg = String.format("'%s' is missing properties:", INCOMPLETE_STD_PROPS_RESOURCE);
		assertTrue(ise.getMessage().startsWith(msg));
	}

	@Test
	void logsLoadedPropsOnInit() {
		subject.bootstrapPropsResource = STD_PROPS_RESOURCE;
		subject.getProperty("bootstrap.feeSchedulesJson.resource");

		assertThat(logCaptor.infoLogs(), contains(Matchers.startsWith(("Resolved bootstrap properties:"))));
	}
}
