package com.hedera.services.ledger.interceptors;

import com.hedera.services.utils.EntityNum;

public record StakeAdjustment(EntityNum account, long adjustment) {
}