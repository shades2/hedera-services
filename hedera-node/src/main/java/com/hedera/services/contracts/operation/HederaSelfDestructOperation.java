package com.hedera.services.contracts.operation;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.BiPredicate;

import static com.hedera.services.ledger.properties.AccountProperty.NUM_POSITIVE_BALANCES;

/**
 * Hedera adapted version of the {@link SelfDestructOperation}.
 * <p>
 * Performs an existence check on the beneficiary {@link Address}
 * Halts the execution of the EVM transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if
 * the account does not exist or it is deleted.
 * <p>
 * Halts the execution of the EVM transaction with {@link HederaExceptionalHaltReason#SELF_DESTRUCT_TO_SELF} if the
 * beneficiary address is the same as the address being destructed
 */
public class HederaSelfDestructOperation extends SelfDestructOperation {

	private HederaTokenStore globalTokenStore;
	private final BiPredicate<Address, MessageFrame> addressValidator;

	@Inject
	public HederaSelfDestructOperation(final GasCalculator gasCalculator,
									   final HederaTokenStore globalTokenStore,
									   final BiPredicate<Address, MessageFrame> addressValidator) {
		super(gasCalculator);
		this.globalTokenStore = globalTokenStore;
		this.addressValidator = addressValidator;
	}

	@Override
	public OperationResult execute(final MessageFrame frame, final EVM evm) {
		final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
		final var beneficiaryAddressOrAlias = Words.toAddress(frame.getStackItem(0));
		final var beneficiaryAddress = updater.priorityAddress(beneficiaryAddressOrAlias);
		if (!addressValidator.test(beneficiaryAddress, frame)) {
			return new OperationResult(errorGasCost(null),
					Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
		}

		// This address is already the EIP-1014 address if applicable; so we can compare it directly to
		// the "priority" address we computed above for the beneficiary
		final var address = frame.getRecipientAddress();
		final var accountId = EntityIdUtils.accountIdFromEvmAddress(address);

		// TODO: fix this null errorGasCost with contract/account in place
		if (address.equals(beneficiaryAddress)) {
			final var account = frame.getWorldUpdater().get(beneficiaryAddress);
			return new OperationResult(errorGasCost(account),
					Optional.of(HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF));
		} else if (isKnownTreasury(accountId, updater)) {
			return new OperationResult(errorGasCost(null),
					Optional.of(HederaExceptionalHaltReason.ACCOUNT_IS_TOKEN_TREASURY));
		} else if (hasNonEmptyBalances(accountId, updater)) {
			return new OperationResult(errorGasCost(null),
					Optional.of(HederaExceptionalHaltReason.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES));
		} else {
			return super.execute(frame, evm);
		}
	}

	private boolean hasNonEmptyBalances(AccountID accountID, HederaStackedWorldStateUpdater updater) {
		return (int) updater.trackingLedgers().accounts().get(accountID, NUM_POSITIVE_BALANCES) == 0;
	}

	private boolean isKnownTreasury(AccountID accountId, HederaStackedWorldStateUpdater updater) {
		// iterate through all stacked updaters to check whether an account has become treasury in the meantime
		var parent = updater.parentUpdater();
		while(parent.isPresent()) {
			final var stackedParentUpdater = (HederaStackedWorldStateUpdater) parent.get();
			if (stackedParentUpdater.isTreasury(accountId)) {
				return true;
			}
			parent = stackedParentUpdater.parentUpdater();
		}

		// check the singleton knownTreasuries collection for the specified account id
		return globalTokenStore.isKnownTreasury(accountId);
	}

	@NotNull
	private Optional<Gas> errorGasCost(final Account account) {
		final Gas cost = gasCalculator().selfDestructOperationGasCost(account, Wei.ONE);
		return Optional.of(cost);
	}
}
