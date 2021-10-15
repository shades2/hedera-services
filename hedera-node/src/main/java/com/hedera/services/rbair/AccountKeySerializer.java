/*
 * (c) 2016-2021 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.hedera.services.rbair;

import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AccountKeySerializer implements KeySerializer<AccountKey> {
	@Override
	public int getSerializedSize() {
		return 24;
	}

	@Override
	public long getCurrentDataVersion() {
		return 1;
	}

	@Override
	public AccountKey deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
		final AccountKey key = new AccountKey();
		key.deserialize(buffer, (int) dataVersion);
		return key;
	}

	@Override
	public int serialize(AccountKey data, SerializableDataOutputStream outputStream) throws IOException {
		data.serialize(outputStream);
		return 24;
	}

	@Override
	public int deserializeKeySize(ByteBuffer buffer) {
		return 24;
	}
}
