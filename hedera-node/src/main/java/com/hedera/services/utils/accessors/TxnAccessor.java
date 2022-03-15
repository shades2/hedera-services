package com.hedera.services.utils.accessors;

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

import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.order.LinkedRefs;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.usage.crypto.CryptoTransferMeta;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.Map;
import java.util.function.Function;

import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;

/**
 * Provides convenient access to the universal parts of a gRPC transaction---
 * the memo, the payer...the "source" of the transaction, whether HAPI or
 * from a MerkleSchedule.
 *
 * NOTE: only these methods are needed to handle a transaction
 * after the point all its signatures have been verified.
 *
 * A triggered transaction only needs an implementation of this type.
 */
public interface TxnAccessor {
    boolean isTriggeredTxn();
    ScheduleID getScheduleRef();
    boolean canTriggerTxn();
    void setTriggered(boolean b);
    void setScheduleRef(ScheduleID parent);

    long getOfferedFee();
    AccountID getPayer();
    TransactionID getTxnId();
    HederaFunctionality getFunction();
    SubType getSubType();
    byte[] getHash();
    byte[] getTxnBytes();
    TransactionBody getTxn();
    void setPayer(AccountID payer);

    byte[] getMemoUtf8Bytes();
    String getMemo();
    boolean memoHasZeroByte();

    void setNumAutoCreations(int numAutoCreations);
    int getNumAutoCreations();
    boolean areAutoCreationsCounted();
    void countAutoCreationsWith(AliasManager aliasManager);

    void setLinkedRefs(LinkedRefs linkedRefs);
    LinkedRefs getLinkedRefs();

    long getGasLimitForContractTx();

    Map<String, Object> getSpanMap();
    ExpandHandleSpanMapAccessor getSpanMapAccessor();

    Transaction getSignedTxnWrapper();
    byte[] getSignedTxnWrapperBytes();
    BaseTransactionMeta baseUsageMeta();

    int sigMapSize();
    int numSigPairs();
    void setSigMapSize(int sigMapSize);
    void setNumSigPairs(int numPairs);
}
