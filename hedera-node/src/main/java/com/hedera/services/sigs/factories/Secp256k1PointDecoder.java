package com.hedera.services.sigs.factories;

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

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.utils.BytesKey;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.services.sigs.utils.MiscCryptoUtils.decompressSecp256k1;

@Singleton
public class Secp256k1PointDecoder {
	final LoadingCache<BytesKey, byte[]> cache;

	@Inject
	public Secp256k1PointDecoder(final NodeLocalProperties properties) {
		this(properties.sec256k1PointCacheMaxSize());
	}

	public Secp256k1PointDecoder(final long cacheMaxSize) {
		final CacheLoader<BytesKey, byte[]> loader = compressedPoint -> decompressSecp256k1(compressedPoint.getArray());

		this.cache = Caffeine.newBuilder()
				.maximumSize(cacheMaxSize)
				.weakValues()
				.build(loader);
	}

	public byte[] getDecoded(final byte[] compressedKey) {
		return cache.get(new BytesKey(compressedKey));
	}
}
