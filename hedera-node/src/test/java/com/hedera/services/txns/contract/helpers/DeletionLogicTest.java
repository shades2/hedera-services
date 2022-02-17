package com.hedera.services.txns.contract.helpers;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.ContractDeleteAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.swirlds.common.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeletionLogicTest {
	private static final byte[] mockAddr = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
	private static final AccountID obtainer = IdUtils.asAccount("0.0.4321");
	private static final ContractID aliasId = ContractID.newBuilder()
			.setEvmAddress(ByteString.copyFrom(mockAddr))
			.build();
	private static final EntityNum id = EntityNum.fromLong(1234);
	private static final ContractID mirrorId = id.toGrpcContractID();
	private static final AccountID target = id.toGrpcAccountId();

	@Mock
	private HederaLedger ledger;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private OptionValidator validator;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> contracts;

	private ContractDeleteAccessor accessor;
	private Transaction contractDeleteTxn;
	private ContractDeleteTransactionBody contractDeleteBody;

	private DeletionLogic subject;

	@BeforeEach
	void setUp() {
		subject = new DeletionLogic(ledger, aliasManager, validator, sigImpactHistorian, () -> contracts);
	}

	@Test
	void precheckValidityUsesValidatorForMirrorTarget() {
		givenOpWithAccountObtainer(mirrorId, obtainer);
		given(validator.queryableContractStatus(id, contracts)).willReturn(CONTRACT_DELETED);
		assertEquals(CONTRACT_DELETED, subject.precheckValidity(accessor));
	}

	@Test
	void precheckValidityUsesValidatorForAliasTarget() {
		givenOpWithAccountObtainer(aliasId, obtainer);
		given(aliasManager.lookupIdBy(aliasId.getEvmAddress())).willReturn(id);
		given(validator.queryableContractStatus(id, contracts)).willReturn(CONTRACT_DELETED);
		assertEquals(CONTRACT_DELETED, subject.precheckValidity(accessor));
	}

	@Test
	void happyPathWorksWithAccountObtainerAndMirrorTarget() {
		givenOpWithAccountObtainer(mirrorId, obtainer);
		given(ledger.exists(obtainer)).willReturn(true);
		given(ledger.alias(target)).willReturn(ByteString.EMPTY);

		final var deleted = subject.performFor(accessor);
		verify(ledger).delete(id.toGrpcAccountId(), obtainer);
		assertEquals(deleted, id.toGrpcContractID());
		verify(sigImpactHistorian).markEntityChanged(id.longValue());
	}

	@Test
	void happyPathWorksWithAccountObtainerAndMirrorTargetAndAliasToUnlink() {
		givenOpWithAccountObtainer(mirrorId, obtainer);

		given(ledger.exists(obtainer)).willReturn(true);
		given(ledger.alias(target)).willReturn(aliasId.getEvmAddress());

		final var deleted = subject.performFor(accessor);
		verify(ledger).delete(id.toGrpcAccountId(), obtainer);
		verify(aliasManager).unlink(aliasId.getEvmAddress());
		verify(ledger).clearAlias(target);
		assertEquals(deleted, id.toGrpcContractID());
		verify(sigImpactHistorian).markEntityChanged(id.longValue());
		verify(sigImpactHistorian).markAliasChanged(aliasId.getEvmAddress());
	}

	@Test
	void happyPathWorksWithAccountObtainerAndMirrorTargetAndNoAliasToUnlink() {
		givenOpWithAccountObtainer(mirrorId, obtainer);
		given(ledger.exists(obtainer)).willReturn(true);
		given(ledger.alias(target)).willReturn(ByteString.EMPTY);
		final var deleted = subject.performFor(accessor);
		verify(ledger).delete(id.toGrpcAccountId(), obtainer);
		verify(aliasManager, never()).unlink(any(ByteString.class));
		assertEquals(deleted, id.toGrpcContractID());
	}

	@Test
	void rejectsNoObtainerWithMirrorTarget() {
		givenOpWithNoObtainer(mirrorId);
		assertFailsWith(() -> subject.performFor(accessor), OBTAINER_REQUIRED);
	}

	@Test
	void rejectsExpiredObtainerAccount() {
		givenOpWithAccountObtainer(mirrorId, obtainer);
		given(ledger.exists(obtainer)).willReturn(true);
		given(ledger.isDetached(obtainer)).willReturn(true);
		assertFailsWith(() -> subject.performFor(accessor), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void rejectsMissingObtainerAccount() {
		givenOpWithAccountObtainer(mirrorId, obtainer);
		given(aliasManager.unaliased(obtainer)).willReturn(EntityNum.fromAccountId(obtainer));
		given(aliasManager.unaliased(mirrorId)).willReturn(EntityNum.fromContractId(mirrorId));
		assertFailsWith(() -> subject.performFor(accessor), OBTAINER_DOES_NOT_EXIST);
	}

	@Test
	void rejectsDeletedObtainerAccount() {
		givenOpWithAccountObtainer(mirrorId, obtainer);
		given(ledger.exists(obtainer)).willReturn(true);
		given(ledger.isDeleted(obtainer)).willReturn(true);
		assertFailsWith(() -> subject.performFor(accessor), OBTAINER_DOES_NOT_EXIST);
	}

	@Test
	void rejectsSameObtainerAccount() {
		givenOpWithAccountObtainer(mirrorId, id.toGrpcAccountId());
		assertFailsWith(() -> subject.performFor(accessor), OBTAINER_SAME_CONTRACT_ID);
	}

	@Test
	void rejectsSameObtainerContractWithAliasTarget() {
		givenOpWithContractObtainer(aliasId, mirrorId);
		assertFailsWith(() -> subject.performFor(accessor), OBTAINER_SAME_CONTRACT_ID);
	}

	@Test
	void rejectsSameObtainerContractWithAliasObtainer() {
		givenOpWithContractObtainer(mirrorId, aliasId);
		assertFailsWith(() -> subject.performFor(accessor), OBTAINER_SAME_CONTRACT_ID);
	}

	private void givenOpWithNoObtainer(final ContractID target) {
		contractDeleteBody = baseOp(target).build();
		setUpAccessor();
	}

	private void givenOpWithContractObtainer(final ContractID target, final ContractID obtainer) {
		contractDeleteBody = baseOp(target).setTransferContractID(obtainer).build();
		setUpAccessor();
	}

	private void setUpAccessor() {
		contractDeleteTxn = Transaction.newBuilder().setBodyBytes(contractDeleteBody.toByteString()).build();
		try {
			accessor = new ContractDeleteAccessor(new SwirldTransaction(contractDeleteTxn.toByteArray()), aliasManager);
		} catch (InvalidProtocolBufferException e) {
			e.printStackTrace();
		}
	}

	private void givenOpWithAccountObtainer(final ContractID target, final AccountID obtainer) {
		contractDeleteBody = baseOp(target).setTransferAccountID(obtainer).build();
		setUpAccessor();
	}

	private ContractDeleteTransactionBody.Builder baseOp(final ContractID target) {
		return ContractDeleteTransactionBody.newBuilder()
				.setContractID(target);
	}
}
