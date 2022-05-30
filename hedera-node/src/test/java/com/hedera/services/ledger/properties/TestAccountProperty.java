package com.hedera.services.ledger.properties;

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

import com.hedera.services.ledger.accounts.TestAccount;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjLongConsumer;
import java.util.function.ToLongFunction;

public enum TestAccountProperty implements BeanProperty<TestAccount> {
	FLAG {
		@Override
		public BiConsumer<TestAccount, Object> setter() {
			return (a, f) -> a.setFlag((boolean) f);
		}

		@Override
		public Function<TestAccount, Object> getter() {
			return TestAccount::isFlag;
		}
	},
	LONG {
		private static final ObjLongConsumer<TestAccount> SETTER = TestAccount::setValue;
		private static final ToLongFunction<TestAccount> GETTER = TestAccount::getValue;

		@Override
		public BiConsumer<TestAccount, Object> setter() {
			return (a, v) -> a.setValue((long) v);
		}

		@Override
		public Function<TestAccount, Object> getter() {
			return TestAccount::getValue;
		}

		@Override
		public boolean isPrimitiveLong() {
			return true;
		}

		@Override
		public ObjLongConsumer<TestAccount> longSetter() {
			return SETTER;
		}

		@Override
		public ToLongFunction<TestAccount> longGetter() {
			return GETTER;
		}
	},
	OBJ {
		@Override
		public BiConsumer<TestAccount, Object> setter() {
			return TestAccount::setThing;
		}

		@Override
		public Function<TestAccount, Object> getter() {
			return TestAccount::getThing;
		}
	},
	TOKEN_LONG {
		@Override
		public BiConsumer<TestAccount, Object> setter() {
			return (a, v) -> a.setTokenThing((long) v);
		}

		@Override
		public Function<TestAccount, Object> getter() {
			return TestAccount::getTokenThing;
		}
	},
	HBAR_ALLOWANCES {
		@Override
		public BiConsumer<TestAccount, Object> setter() {
			return (a, v) -> a.setValidHbarAllowances((TestAccount.Allowance) v);
		}

		@Override
		public Function<TestAccount, Object> getter() {
			return TestAccount::getValidHbarAllowances;
		}
	},
}
