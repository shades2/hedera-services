package com.hedera.services.sysfiles.validation;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.Optional;

/**
 * Utility class for error codes
 */
public final class ErrorCodeUtils {
	private static final String EXC_MSG_TPL = "%s :: %s";

	private ErrorCodeUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}

	/**
	 * Exception message for the given response code and the cause
	 *
	 * @param error
	 * 		errored response code
	 * @param details
	 * 		error details
	 * @return exception message
	 */
	public static String exceptionMsgFor(final ResponseCodeEnum error, final String details) {
		return String.format(EXC_MSG_TPL, error, details);
	}

	/**
	 * Parse the response code from the given exception message
	 *
	 * @param exceptionMsg
	 * 		given exception message
	 * @return response code
	 */
	public static Optional<ResponseCodeEnum> errorFrom(final String exceptionMsg) {
		final var parts = exceptionMsg.split(" :: ");
		if (parts.length != 2) {
			return Optional.empty();
		}
		return Optional.of(ResponseCodeEnum.valueOf(parts[0]));
	}
}
