package com.hedera.services.legacy.core;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.Base64;

/**
 * Common utilities.
 */
public class CommonUtils {
	/**
	 * Decode base64 string to bytes
	 *
	 * @param base64string
	 * 		base64 string to be decoded
	 * @return decoded bytes
	 */
	public static byte[] base64decode(String base64string) {
		byte[] rv = null;
		rv = Base64.getDecoder().decode(base64string);
		return rv;
	}

	/**
	 * Deserialize a Java object from given bytes
	 *
	 * @param bytes
	 * 		given byte array to be deserialized
	 * @return the Java object constructed after deserialization
	 */
	public static Object convertFromBytes(byte[] bytes) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(
				bytes); ObjectInput in = new ObjectInputStream(bis)) {
			return in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
}
