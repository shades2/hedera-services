package com.hedera.services.store.models.converters;

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.test.factories.fees.CustomFeeBuilder;
import com.hedera.test.utils.IdUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hedera.services.state.enums.TokenSupplyType.FINITE;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.store.models.converters.TokenConverter.convertToMerkle;
import static com.hedera.services.store.models.converters.TokenConverter.convertToModel;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class TokenConverterTest {
	@Mock
	private Function<Id, Account> accountLoader;

	@Test
	void allFieldsAreConverted() {
		final var original = fullyDetailedMerkle();

		final var reconstructed = convertToMerkle(convertToModel(merkleId, original, accountLoader));

		assertEquals(original, reconstructed);
	}

	private MerkleToken fullyDetailedMerkle() {
		final var token = new MerkleToken();
		token.setTokenType(tokenType);
		token.setSupplyType(tokenSupplyType);
		token.setDecimals(decimals);
		token.setLastUsedSerialNumber(lastUsedSerialNumber);
		token.setExpiry(expiry);
		token.setMaxSupply(maxSupply);
		token.setTotalSupply(totalSupply);
		token.setAutoRenewPeriod(autoRenewPeriod);
		token.setAdminKey(adminKey);
		token.setKycKey(kycKey);
		token.setWipeKey(wipeKey);
		token.setSupplyKey(supplyKey);
		token.setFreezeKey(freezeKey);
		token.setFeeScheduleKey(feeScheduleKey);
		token.setSymbol(symbol);
		token.setName(name);
		token.setMemo(memo);
		token.setDeleted(deleted);
		token.setAccountsFrozenByDefault(freezeDefault);
		token.setAccountsKycGrantedByDefault(accountsKycGrantedByDefault);
		token.setTreasury(treasury);
		token.setAutoRenewAccount(autoRenew);
		token.setFeeSchedule(feeSchedule);
		return token;
	}

	private static final MerkleEntityId merkleId = new MerkleEntityId(0, 0, 7890);

	private static final TokenType tokenType = NON_FUNGIBLE_UNIQUE;
	private static final TokenSupplyType tokenSupplyType = FINITE;
	private static final int decimals = 2;
	private static final long lastUsedSerialNumber = 666;
	private static final long expiry = 1_234_567L;
	private static final long maxSupply = 888;
	private static final long totalSupply = 777;
	private static final long autoRenewPeriod = 1_234_567L;
	private static final JKey adminKey = new JEd25519Key("not-a-real-admin-key".getBytes());
	private static final JKey kycKey = new JEd25519Key("not-a-real-kyc-key".getBytes());
	private static final JKey wipeKey = new JEd25519Key("not-a-real-wipe-key".getBytes());
	private static final JKey supplyKey = new JEd25519Key("not-a-real-supply-key".getBytes());
	private static final JKey freezeKey = new JEd25519Key("not-a-real-freeze-key".getBytes());
	private static final JKey feeScheduleKey = new JEd25519Key("not-a-real-fee-schedule-key".getBytes());
	private static final String symbol = "NotAnHbar";
	private static final String name = "NotAnHbarName";
	private static final String memo = "NotAMemo";
	private static final boolean deleted = true;
	private static final boolean freezeDefault = true;
	private static final boolean accountsKycGrantedByDefault = true;
	private static final EntityId treasury = new EntityId(0, 0, 1234);
	private static final EntityId autoRenew = new EntityId(0, 0, 2345);
	private static final Account treasuryAccount = new Account(new Id(0, 0, 1234));
	private static final Account autoRenewAccount = new Account(new Id(0, 0, 2345));
	private static final List<FcCustomFee> feeSchedule = List.of(
		new CustomFeeBuilder(IdUtils.asAccount("0.0.4321"))
				.withFixedFee(CustomFeeBuilder.fixedHbar(666))
	).stream().map(fee -> FcCustomFee.fromGrpc(fee, null)).collect(Collectors.toList());
}