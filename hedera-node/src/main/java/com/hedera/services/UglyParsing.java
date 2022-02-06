package com.hedera.services;

import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongycastle.util.Arrays;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hedera.services.utils.MiscUtils.timestampToInstant;
import static com.swirlds.common.CommonUtils.hex;
import static com.swirlds.common.CommonUtils.unhex;
import static java.util.stream.Collectors.toList;

public class UglyParsing {
	private static final Logger log = LogManager.getLogger(UglyParsing.class);

	private static final int MAX_BODY_SIZE = 1024;

	private static final String BASE_DIR = "/Users/tinkerm/Dev/rosetta/mainnet_balance_file_issue";
	private static final String RECORD_STREAM_DIR = BASE_DIR + "/recordstreams";
	private static final String EVENT_STREAM_DIR = BASE_DIR + "/eventsStreams";

	public static void main(String... args) throws UnknownHederaFunctionality {
		final var rcdLoc = RECORD_STREAM_DIR + "/2019-09-14T11_18_30.114968Z.rcd";
		final var evtsLoc = EVENT_STREAM_DIR + "/2019-09-14T11_18_30.027183Z.evts";
		compareFiles(rcdLoc, evtsLoc, false);

//		final var rcdLocs = orderedFilesFrom(RECORD_STREAM_DIR, ".rcd");
//		final var evtLocs = orderedFilesFrom(EVENT_STREAM_DIR, ".evts");
//
//		System.out.println(rcdLocs.size() + " record files & " + evtLocs.size() + " event files");
//		final var N = rcdLocs.size();
//		var numFailures = 0;
//		for (int i = 0; i < N; i++) {
//			final var rcdLoc = rcdLocs.get(i);
//			final var evtLoc = evtLocs.get(i);
//			if (compareFiles(rcdLoc.getPath(), evtLoc.getPath(), false)) {
//				numFailures++;
//			}
//		}
//		System.out.println("Total differences: " + numFailures);
	}

	private static boolean compareFiles(
			final String rcdLoc,
			final String evtsLoc,
			final boolean verbose
	) {
		final var histories = parseOldRecordFile(rcdLoc);
		final var rN = histories.size();
		System.out.println("Size from legacy parser: " + rN);

		final var watch = StopWatch.createStarted();
		final var items = uglyParsing(evtsLoc, verbose);
		final var eN = items.size();
		System.out.println("Size from ugly parsing : " + eN
				+ " in " + watch.getTime(TimeUnit.SECONDS) + "s");

		if (rN != eN) {
			System.out.println("!!! COUNTS DIFFER " + rcdLoc + " vs " + evtsLoc + " !!!");
			final Set<String> inRecordStream = new HashSet<>();
			for (int i = 0; i < rN; i++) {
				final var history = histories.get(i);
				final var txn = history.getSignedTxn().getBody();
				final var summary = summarize(txn);
				System.out.println(summary + "::" + txn.getSerializedSize());
				inRecordStream.add(summary);
			}
			System.out.println("!!!---------------!!!");
			for (int j = 0; j < eN; j++) {
				final var item = items.get(j);
				final var txn = item.txn();
				final var summary = summarize(txn);
				System.out.println(summary);
				if (!inRecordStream.contains(summary)) {
					System.out.println(hex(item.hash));
					System.out.println(txn);
				}
			}
			System.out.println("!!!!!!!!!!!!!!!!!!!!!");
			return true;
		}
		return false;
	}

	private static String summarize(final TransactionBody txn) {
		var desc = "" + safeFunctionOf(txn);
		final var txnId = txn.getTransactionID();
		desc += "-";
		desc += txnId.getAccountID().getAccountNum();
		desc += "@";
		final var when = timestampToInstant(txnId.getTransactionValidStart());
		return desc + when;
	}

	private static String safeFunctionOf(final TransactionBody txn) {
		try {
			return "" + functionOf(txn);
		} catch (UnknownHederaFunctionality unknownHederaFunctionality) {
			unknownHederaFunctionality.printStackTrace();
			System.out.println(txn);
			return "N/A";
		}
	}

	private static List<Item> uglyParsing(final String loc, final boolean verbose) {
		final byte[] marker = unhex("0a");
		final var l  = marker.length;
		try {
			final var data = Files.readAllBytes(Paths.get(loc));
			final List<Item> ans = new ArrayList<>();

			final var verbosePrintModulus = 50_000;
			var found = 0;
			System.out.println("DATA bytes = " + data.length);
			for (int i = 0; i < data.length; i++) {
				if (verbose && (i % verbosePrintModulus == 0)) {
					System.out.println("+ " + i);
				}
				if (!java.util.Arrays.equals(marker, 0, l, data, i, i + l)) {
					continue;
				}
				final var maxLen = Math.min(data.length - i, MAX_BODY_SIZE);
				for (int j = 1; j <= maxLen; j++) {
					final var silly = Arrays.copyOfRange(data, i, i + j);
					try {
						final var txn = TransactionBody.parseFrom(silly);
						if (!txn.hasTransactionID()
								|| !txn.getTransactionID().hasAccountID()
								|| !txn.getUnknownFields().asMap().isEmpty()) {
							continue;
						}
						try {
							final var function = functionOf(txn);
							found++;
							if (verbose) {
								System.out.println("  #" + found + " @ ["
										+ i + ", " + (i + j) + ") "
										+ "-> (" + function
										+ ("-" + hex(silly, 8) + ")"));
								System.out.println(txn);
							}
							final var hash = CommonUtils.noThrowSha384HashOf(silly);
							ans.add(new Item(txn, hash));
							i += j - 2;
							break;
						} catch (UnknownHederaFunctionality nope) {
							/* NOPE */
						}
					} catch (IOException ignore) {
						/* NOPE */
					}
				}
			}
			return ans;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private record Item(TransactionBody txn, byte[] hash) {
	}

	private static List<RecordParser.TxnHistory> parseOldRecordFile(final String loc) {
		final var f = new File(loc);
		final var rcdFile = RecordParser.parseFrom(f);
		return rcdFile.getTxnHistories();
	}

	private static List<File> orderedFilesFrom(final String dir, final String suffix) {
		final var l = suffix.length();
		return uncheckedWalk(dir)
				.filter(path -> path.toString().endsWith(suffix))
				.map(Path::toString)
				.map(File::new)
				.sorted(Comparator.comparing(f -> consTimeOf(f.getPath(), l)))
				.collect(toList());
	}

	private static Instant consTimeOf(final String rcdFile, final int suffixLen) {
		final var s = rcdFile.lastIndexOf("/");
		final var n = rcdFile.length();
		final var pI = rcdFile.substring(s + 1, n - suffixLen).replace("_", ":");
		return Instant.parse(pI);
	}

	private static Stream<Path> uncheckedWalk(String dir) {
		try {
			return Files.walk(Path.of(dir));
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}
}
