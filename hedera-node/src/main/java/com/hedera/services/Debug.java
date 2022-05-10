package com.hedera.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.ERROR;

public class Debug {
	private static final Logger log = LogManager.getLogger(ServicesMain.class);
	public static final ConcurrentHashMap<String, Object> trackedStats = new ConcurrentHashMap<>();

	public static void setValue(String key, Object value) {
		trackedStats.put(key, value);
	}

	public static void incrementValue(String key, long delta) {
		trackedStats.compute(key, (k, v) -> v == null ? 1L : (long) v + delta);
	}

	public static void rotateValues(Object newestValue, String ... keys) {
		for (int i = 0; i < keys.length - 1; i++) {
			trackedStats.put(keys[i + 1], trackedStats.getOrDefault(keys[i], ""));
		}
		trackedStats.put(keys[0], newestValue);
	}

	public static void rotateN(String keyName, int n, Object newestValue) {
		for (int i = 0; i < n - 1; i++) {
			String newKey = keyName + "-" + (i+1);
			String prevKey = keyName + "-" + i;
			trackedStats.put(newKey, trackedStats.get(prevKey));
		}
		trackedStats.put(keyName + "-" + 0, newestValue);
	}

	public static void dumpStats() {
		log.atError()
				.withMarker(ERROR.getMarker())
				.withLocation()
				.log("-------[ BEGIN: Dumping stats map for ISS] -----------");
		for (var entry : trackedStats.entrySet()) {
			log.atError()
					.withMarker(ERROR.getMarker())
					.withLocation()
					.log(String.format("ISS-stats [%s] : %s", entry.getKey(), entry.getValue()));
		}
		log.atError()
				.withMarker(ERROR.getMarker())
				.withLocation()
				.log("-------[ END: Dumping stats map for ISS] -----------");
	}

}
