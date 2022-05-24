package com.hedera.services.store.contracts;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.accounts.ContractCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static com.hedera.services.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;
import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
class HederaWorldStateTest {
	@Mock
	private WorldLedgers worldLedgers;
	@Mock
	private EntityIdSource ids;
	@Mock
	private EntityAccess entityAccess;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private ContractAliases aliases;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private ContractCustomizer customizer;

	private CodeCache codeCache;

	final long balance = 1_234L;
	final Id sponsor = new Id(0, 0, 1);
	final Id contract = new Id(0, 0, 2);
	final EntityNum tokenNum = EntityNum.fromLong(1234);
	final AccountID accountId = IdUtils.asAccount("0.0.12345");
	final Bytes code = Bytes.of("0x60606060".getBytes());
	private static final Bytes TOKEN_CALL_REDIRECT_CONTRACT_BINARY_WITH_ZERO_ADDRESS = Bytes.fromHexString(
			"6080604052348015600f57600080fd5b506000610167905077618dc65e0000000000000000000000000000000000000000600052366000602037600080366018016008845af43d806000803e8160008114605857816000f35b816000fdfea2646970667358221220d8378feed472ba49a0005514ef7087017f707b45fb9bf56bb81bb93ff19a238b64736f6c634300080b0033");
	private HederaWorldState subject;

	@BeforeEach
	void setUp() {
		codeCache = new CodeCache(0, entityAccess);
		subject = new HederaWorldState(ids, entityAccess, codeCache, sigImpactHistorian, dynamicProperties);
	}

	@Test
	void canManageCustomizer() {
		subject.setHapiSenderCustomizer(customizer);
		assertSame(customizer, subject.hapiSenderCustomizer());
		subject.resetHapiSenderCustomizer();
		assertNull(subject.hapiSenderCustomizer());
	}

	@Test
	void baseUpdaterThrowsWithoutSenderCustomizer() {
		givenNonNullWorldLedgers();
		final var updater = subject.updater();
		assertThrows(IllegalStateException.class, updater::customizerForPendingCreation);
	}

	@Test
	void baseUpdaterReturnsSenderCustomizerWhenAvailable() {
		givenNonNullWorldLedgers();
		subject.setHapiSenderCustomizer(customizer);
		final var updater = subject.updater();
		assertSame(customizer, updater.customizerForPendingCreation());
	}

	@Test
	void getsProvisionalContractCreations() {
		var provisionalContractCreations = subject.getCreatedContractIds();
		assertEquals(0, provisionalContractCreations.size());
	}

	@Test
	void skipsTokenAccountsInCommit() {
		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);
		doAnswer(invocationOnMock -> invocationOnMock.getArguments()[0]).when(aliases).resolveForEvm(any());

		final var updater = subject.updater();
		updater.createAccount(tokenNum.toEvmAddress(), TOKEN_PROXY_ACCOUNT_NONCE, Wei.ZERO);

		updater.commit();

		verify(entityAccess, never()).isExtant(tokenNum.toGrpcAccountId());
	}

	@Test
	void newContractAddress() {
		final var sponsor = Address.fromHexString("0x0102030405060708090a0b0c0d0e0f1011121314");
		given(ids.newContractId(any())).willReturn(ContractID.newBuilder().setContractNum(1).build());
		var addr = subject.newContractAddress(sponsor);
		assertNotEquals(addr, sponsor);
		assertEquals(1,
				EntityIdUtils.accountIdFromEvmAddress(addr.toArrayUnsafe()).getAccountNum());
	}

	@Test
	void reclaimContractId() {
		subject.reclaimContractId();
		verify(ids).reclaimLastId();
	}

	@Test
	void updater() {
		givenNonNullWorldLedgers();
		var updater = subject.updater();
		assertNotNull(updater);
		assertTrue(updater instanceof HederaWorldState.Updater);
	}

	@Test
	void rootHash() {
		assertEquals(Hash.EMPTY, subject.rootHash());
		assertEquals(Hash.EMPTY, subject.frontierRootHash());
	}

	@Test
	void streamAccounts() {
		assertThrows(
				UnsupportedOperationException.class,
				() -> subject.streamAccounts(null, 10)
		);
	}

	@Test
	void returnsNullForDetached() {
		given(entityAccess.isExtant(accountId)).willReturn(true);
		given(entityAccess.isDeleted(accountId)).willReturn(false);
		given(entityAccess.isDetached(accountId)).willReturn(true);

		assertNull(subject.get(EntityIdUtils.asTypedEvmAddress(accountId)));
	}

	@Test
	void returnsNullForNull() {
		assertNull(subject.get(null));
	}

	@Test
	void returnsEmptyCodeIfNotPresent() {
		final var address = Address.RIPEMD160;
		final var ripeAccountId = EntityIdUtils.accountIdFromEvmAddress(address.toArrayUnsafe());
		givenWellKnownAccountWithCode(ripeAccountId, null);

		final var account = subject.get(address);

		assertTrue(account.getCode().isEmpty());
		assertFalse(account.hasCode());
	}

	@Test
	void returnsExpectedCodeIfPresent() {
		final var address = Address.RIPEMD160;
		final var ripeAccountId = EntityIdUtils.accountIdFromEvmAddress(address.toArrayUnsafe());
		givenWellKnownAccountWithCode(ripeAccountId, code);

		final var account = subject.get(address);

		assertEquals(code, account.getCode());
		assertTrue(account.hasCode());
	}

	@Test
	void getsAsExpected() {
		final var account = EntityIdUtils.accountIdFromEvmAddress(Address.RIPEMD160.toArray());
		givenWellKnownAccountWithCode(account, Bytes.EMPTY);
		given(entityAccess.getStorage(any(), any())).willReturn(UInt256.ZERO);

		final var acc = subject.get(Address.RIPEMD160);
		assertNotNull(acc);
		assertEquals(Wei.of(balance), acc.getBalance());

		objectContractWorksForWorldState(acc);

		/* non-existent accounts should resolve to null */
		given(entityAccess.isExtant(any())).willReturn(false);
		var nonExistent = subject.get(Address.RIPEMD160);
		assertNull(nonExistent);

		given(entityAccess.isExtant(any())).willReturn(true);
		given(entityAccess.isDeleted(any())).willReturn(true);
		nonExistent = subject.get(Address.RIPEMD160);
		assertNull(nonExistent);
	}

	private void givenWellKnownAccountWithCode(final AccountID account, final Bytes bytecode) {
		given(entityAccess.getBalance(account)).willReturn(balance);
		given(entityAccess.isExtant(any())).willReturn(true);
		given(entityAccess.isDeleted(any())).willReturn(false);
		if (bytecode != null) {
			given(entityAccess.fetchCodeIfPresent(any())).willReturn(bytecode);
		}
	}

	private void objectContractWorksForWorldState(Account acc) {
		assertNotNull(acc.getAddress());
		assertNotNull(acc.getAddressHash());
		assertFalse(acc.hasCode());
		assertEquals(Bytes.EMPTY, acc.getCode());
		assertEquals(Hash.EMPTY, acc.getCodeHash());
		assertEquals(0, acc.getNonce());

		final var stringified = "AccountState" + "{" +
				"address=" + Address.RIPEMD160 + ", " +
				"nonce=" + 0 + ", " +
				"balance=" + Wei.of(balance) + ", " +
				"codeHash=" + Hash.EMPTY + ", " +
				"}";
		assertEquals(stringified, acc.toString());

		assertEquals(
				Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
				acc.getOriginalStorageValue(UInt256.ONE)
		);
		assertThrows(UnsupportedOperationException.class,
				() -> acc.storageEntriesFrom(null, 10));
	}

	@Test
	void failsFastIfDeletionsHappenOnStaticWorld() {
		subject = new HederaWorldState(ids, entityAccess, new CodeCache(0, entityAccess), dynamicProperties);
		final var tbd = IdUtils.asAccount("0.0.321");
		final var tbdAddress = EntityIdUtils.asTypedEvmAddress(tbd);
		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(tbdAddress)).willReturn(tbdAddress);

		var actualSubject = subject.updater();
		var mockTbdAccount = Address.fromHexString("0x0102030405060708090a0b0c0d0e0f1011121314");
		actualSubject.deleteAccount(tbdAddress);

		assertFailsWith(actualSubject::commit, ResponseCodeEnum.FAIL_INVALID);
	}

	@Test
	void staticInnerUpdaterWorksAsExpected() {
		final var tbd = IdUtils.asAccount("0.0.321");
		final var tbdAddress = EntityIdUtils.asTypedEvmAddress(tbd);
		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);

		/* Please note that the subject of this test is the actual inner updater class */
		var actualSubject = subject.updater();
		assertNotNull(actualSubject.updater());
		assertEquals(0, actualSubject.getTouchedAccounts().size());

		/* delete branch */
		given(aliases.resolveForEvm(tbdAddress)).willReturn(tbdAddress);
		actualSubject.deleteAccount(tbdAddress);
		actualSubject.commit();
		verify(worldLedgers).commit(sigImpactHistorian);
		verify(sigImpactHistorian).markEntityChanged(tbd.getAccountNum());
		actualSubject.countIdsAllocatedByStacked(3);

		actualSubject.revert();

		actualSubject.addSbhRefund(234L);
		assertEquals(234L, actualSubject.getSbhRefund());
		actualSubject.revert();
		assertEquals(0L, actualSubject.getSbhRefund());
		verify(ids, times(3)).reclaimLastId();
		assertTrue(actualSubject.getStateChanges().isEmpty());
	}

	@Test
	void updaterGetsHederaAccount() {
		givenNonNullWorldLedgers();

		final var zeroAddress = EntityIdUtils.accountIdFromEvmAddress(Address.ZERO.toArray());
		final var updater = subject.updater();
		// and:
		given(entityAccess.isExtant(zeroAddress)).willReturn(true);
		given(entityAccess.getBalance(zeroAddress)).willReturn(balance);
		// and:
		final var expected = new WorldStateAccount(Address.ZERO, Wei.of(balance), codeCache, entityAccess);

		// when:
		final var result = updater.getAccount(Address.ZERO);

		// then:
		assertEquals(expected.getAddress(), result.getAddress());
		assertEquals(expected.getBalance(), result.getBalance());
		// and:
		verify(entityAccess).isExtant(zeroAddress);
		verify(entityAccess).getBalance(zeroAddress);
	}

	@Test
	void updaterGetsHederaTokenAccount() {
		givenNonNullWorldLedgers();

		final var zeroAddress = EntityIdUtils.accountIdFromEvmAddress(Address.ZERO.toArray());
		final var updater = subject.updater();
		// and:
		given(entityAccess.isTokenAccount(EntityIdUtils.asTypedEvmAddress(zeroAddress))).willReturn(true);
		given(dynamicProperties.isRedirectTokenCallsEnabled()).willReturn(true);
		// and:
		final var expected = new WorldStateAccount(Address.ZERO, Wei.of(0), codeCache, entityAccess);

		// when:
		final var result = updater.getAccount(Address.ZERO);

		// then:
		assertEquals(expected.getAddress(), result.getAddress());
		assertEquals(expected.getBalance(), result.getBalance());
		assertEquals(-1, result.getNonce());
		assertEquals(TOKEN_CALL_REDIRECT_CONTRACT_BINARY_WITH_ZERO_ADDRESS, result.getCode());
	}

	@Test
	void stackedUpdaterGetsTokenProxyAccountWhenRedirectEnabled() {
		final var htsProxyAddress = Address.RIPEMD160;

		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);
		given(worldLedgers.isTokenAddress(htsProxyAddress)).willReturn(true);
		willAnswer(invocationOnMock -> invocationOnMock.getArguments()[0]).given(aliases).resolveForEvm(any());
		given(dynamicProperties.isRedirectTokenCallsEnabled()).willReturn(true);

		final var expected = new WorldStateTokenAccount(htsProxyAddress);

		final var realSubject = subject.updater().updater();
		final var result = realSubject.get(htsProxyAddress);
		final var evmResult = realSubject.getAccount(htsProxyAddress);

		assertEquals(expected.getAddress(), result.getAddress());
		assertEquals(expected.getBalance(), result.getBalance());
		assertEquals(-1, result.getNonce());
		assertEquals(WorldStateTokenAccount.proxyBytecodeFor(htsProxyAddress), result.getCode());
		// and:
		assertEquals(expected.getAddress(), evmResult.getAddress());
		assertEquals(expected.getBalance(), evmResult.getBalance());
		assertEquals(-1, evmResult.getNonce());
		assertEquals(WorldStateTokenAccount.proxyBytecodeFor(htsProxyAddress), evmResult.getCode());
	}

	@Test
	void stackedUpdaterGetsNonProxyAccountWhenRedirectEnabled() {
		final var htsProxyAddress = Address.RIPEMD160;

		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);
		willAnswer(invocationOnMock -> invocationOnMock.getArguments()[0]).given(aliases).resolveForEvm(any());
		given(dynamicProperties.isRedirectTokenCallsEnabled()).willReturn(true);

		final var realSubject = subject.updater().updater();
		assertNull(realSubject.get(htsProxyAddress));
		assertNull(realSubject.getAccount(htsProxyAddress));
	}

	@Test
	void stackedUpdaterDoesntGetTokenProxyAccountWhenRedirectEnabled() {
		final var htsProxyAddress = Address.RIPEMD160;

		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);
		willAnswer(invocationOnMock -> invocationOnMock.getArguments()[0]).given(aliases).resolveForEvm(any());

		final var realSubject = subject.updater().updater();

		assertNull(realSubject.get(htsProxyAddress));
		assertNull(realSubject.getAccount(htsProxyAddress));
	}

	@Test
	void updaterAllocatesNewAddress() {
		givenNonNullWorldLedgers();
		given(ids.newContractId(sponsor.asGrpcAccount())).willReturn(contract.asGrpcContract());

		// when:
		final var result = subject.updater().newContractAddress(sponsor.asEvmAddress());

		// then:
		assertEquals(contract.asEvmAddress(), result);
		// and:
		verify(ids).newContractId(sponsor.asGrpcAccount());
	}

	@Test
	void updaterCreatesDeletedAccountUponCommit() {
		givenNonNullWorldLedgers();
		final var tbdAddress = contract.asEvmAddress();
		given(worldLedgers.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(tbdAddress)).willReturn(tbdAddress);

		final var updater = subject.updater();
		updater.deleteAccount(tbdAddress);

		// when:
		updater.commit();

		// then:
		verify(entityAccess).flushStorage();
		verify(worldLedgers).commit(sigImpactHistorian);
		verify(entityAccess).recordNewKvUsageTo(any());
	}

	@Test
	void stackedUpdaterPropagatesAllocatedIds() {
		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);

		final var firstSubject = subject.updater();
		final var secondSubject = (HederaWorldUpdater) firstSubject.updater();
		secondSubject.countIdsAllocatedByStacked(3);
		secondSubject.commit();
		firstSubject.revert();
		verify(ids, times(3)).reclaimLastId();
	}

	@Test
	void updaterCommitsSuccessfully() {
		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);
		final var newAddress = contract.asEvmAddress();
		given(aliases.resolveForEvm(newAddress)).willReturn(newAddress);

		final var actualSubject = subject.updater();
		final var evmAccount = actualSubject.createAccount(newAddress, 0, Wei.of(balance));
		final var storageKey = UInt256.ONE;
		final var storageValue = UInt256.valueOf(9_876);
		final var secondStorageKey = UInt256.valueOf(2);
		final var secondStorageValue = UInt256.ZERO;
		evmAccount.getMutable().setStorageValue(storageKey, storageValue);
		evmAccount.getMutable().setStorageValue(secondStorageKey, secondStorageValue);
		evmAccount.getMutable().setCode(code);
		// and:
		final var accountID = EntityIdUtils.accountIdFromEvmAddress(contract.asEvmAddress().toArray());
		given(entityAccess.isExtant(accountID)).willReturn(true);

		// when:
		actualSubject.commit();

		// then:
		verify(entityAccess).isExtant(accountID);
		verify(entityAccess).putStorage(accountID, storageKey, storageValue);
		verify(entityAccess).putStorage(accountID, secondStorageKey, secondStorageValue);
		// and:
		verify(entityAccess).storeCode(accountID, code);
	}

	@Test
	void updaterCorrectlyPopulatesStateChanges() {
		givenNonNullWorldLedgers();
		final var contractAddress = "0xffff";
		final var slot = 1L;
		final var oldSlotValue = 4L;
		final var newSlotValue = 255L;
		final var updatedAccount = mock(UpdateTrackingLedgerAccount.class);
		given(updatedAccount.getAddress()).willReturn(Address.fromHexString(contractAddress));
		given(updatedAccount.getOriginalStorageValue(UInt256.valueOf(slot))).willReturn(UInt256.valueOf(oldSlotValue));
		given(updatedAccount.getUpdatedStorage()).willReturn(
				Map.of(UInt256.valueOf(slot), UInt256.valueOf(newSlotValue)));
		given(updatedAccount.getStorageValue(UInt256.valueOf(slot))).willReturn(UInt256.valueOf(newSlotValue));

		final var actualSubject = subject.updater();
		assertEquals(0, actualSubject.getTouchedAccounts().size());
		actualSubject.track(updatedAccount);

		final var finalStateChanges = actualSubject.getFinalStateChanges();
		assertEquals(1, finalStateChanges.size());
		final var contractStateChange = finalStateChanges.get(Address.fromHexString(contractAddress));
		assertEquals(1, contractStateChange.size());
		assertNotNull(contractStateChange.get(UInt256.valueOf(slot)));
		assertEquals(contractStateChange.get(UInt256.valueOf(slot)).getLeft(), UInt256.valueOf(oldSlotValue));
		assertEquals(contractStateChange.get(UInt256.valueOf(slot)).getRight(), UInt256.valueOf(newSlotValue));
	}

	@Test
	void onlyStoresCodeIfUpdated() {
		givenNonNullWorldLedgers();
		final var someAddress = contract.asEvmAddress();

		final var actualSubject = subject.updater();

		final var accountId = accountIdFromEvmAddress(someAddress);
		given(entityAccess.isExtant(accountId)).willReturn(true);
		given(entityAccess.getBalance(accountId)).willReturn(balance);

		actualSubject.getAccount(someAddress);
		actualSubject.commit();

		verify(entityAccess, never()).storeCode(any(), any());
	}

	@Test
	void persistNewlyCreatedContracts() {
		givenNonNullWorldLedgers();
		final var newAddress = contract.asEvmAddress();
		given(worldLedgers.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(newAddress)).willReturn(newAddress);

		final var actualSubject = subject.updater();
		actualSubject.createAccount(newAddress, 0, Wei.of(balance));
		// and:
		given(entityAccess.isExtant(contract.asGrpcAccount())).willReturn(false);
		// and:

		// when:
		actualSubject.commit();
		// and:
		final var result = subject.getCreatedContractIds();

		// then:
		verify(entityAccess).isExtant(contract.asGrpcAccount());
		// and:
		assertEquals(1, result.size());
		assertEquals(contract.asGrpcContract(), result.get(0));
	}

	private void givenNonNullWorldLedgers() {
		given(worldLedgers.wrapped()).willReturn(worldLedgers);
		given(entityAccess.worldLedgers()).willReturn(worldLedgers);
	}
}