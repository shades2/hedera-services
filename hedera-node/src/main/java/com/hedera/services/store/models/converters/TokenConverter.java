package com.hedera.services.store.models.converters;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;

import java.util.function.Function;

public class TokenConverter {
	private TokenConverter() {
		throw new UnsupportedOperationException("Utility Class");
	}

	public static Token convertToModel(
			MerkleEntityId id,
			MerkleToken merkleToken,
			Function<Id, Account> accountLoader
	) {
		final var token = new Token(new Id(0, 0, id.getNum()));
		token.setType(merkleToken.type());
		token.setSupplyType(merkleToken.supplyType());
		token.setDecimals(merkleToken.decimals());
		token.setLastUsedSerialNumber(merkleToken.lastUsedSerialNumber());
		token.setExpiry(merkleToken.expiry());
		token.setMaxSupply(merkleToken.maxSupply());
		token.setTotalSupply(merkleToken.totalSupply());
		token.setAutoRenewPeriod(merkleToken.autoRenewPeriod());
		token.setAdminKey(merkleToken.adminKeyIfPresent());
		token.setKycKey(merkleToken.kycKeyIfPresent());
		token.setWipeKey(merkleToken.wipeKeyIfPresent());
		token.setSupplyKey(merkleToken.supplyKeyIfPresent());
		token.setFreezeKey(merkleToken.freezeKeyIfPresent());
		token.setFeeScheduleKey(merkleToken.feeScheduleKeyIfPresent());
		token.setSymbol(merkleToken.symbol());
		token.setName(merkleToken.name());
		token.setMemo(merkleToken.memo());
		token.setDeleted(merkleToken.isDeleted());
		token.setFrozenByDefault(merkleToken.frozenByDefault());
		token.setKycGrantedByDefault(merkleToken.kycGrantedByDefault());
		token.setTreasury(accountLoader.apply(new Id(0, 0, merkleToken.treasury().num())));
		token.setAutoRenewAccount(accountLoader.apply(new Id(0, 0, merkleToken.autoRenewAccount().num())));
//		token.setFeeSchedule(merkleToken.feeSchedule().stream().map(CustomFee::from));
		return token;
	}

	public static MerkleToken convertToMerkle(Token token) {
		throw new AssertionError("Not implemented");
	}
}
