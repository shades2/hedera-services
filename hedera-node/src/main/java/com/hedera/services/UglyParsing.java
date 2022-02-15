package com.hedera.services;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hedera.services.utils.MiscUtils.timestampToInstant;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static com.swirlds.common.CommonUtils.hex;
import static com.swirlds.common.CommonUtils.unhex;
import static java.util.stream.Collectors.toList;

public class UglyParsing {
	private static final Logger log = LogManager.getLogger(UglyParsing.class);

	private static final int MAX_BODY_SIZE = 2048;

	private static final String FIRST_INTERVAL_BASE_DIR = "/Users/tinkerm/Dev/rosetta/mainnet_balance_file_issue";
	private static final String FIRST_RECORD_STREAM_DIR = FIRST_INTERVAL_BASE_DIR + "/recordstreams";
	private static final String FIRST_EVENT_STREAM_DIR = FIRST_INTERVAL_BASE_DIR + "/eventsStreams";

	private static final String SECOND_INTERVAL_BASE_DIR = "/Users/tinkerm/Dev/rosetta/batch2_3/batch2";
	private static final String SECOND_RECORD_STREAM_DIR = SECOND_INTERVAL_BASE_DIR + "/recordstreams";
	private static final String SECOND_EVENT_STREAM_DIR = SECOND_INTERVAL_BASE_DIR + "/eventsStreams";

	private static final String THIRD_INTERVAL_BASE_DIR = "/Users/tinkerm/Dev/rosetta/batch2_3/batch3";
	private static final String THIRD_RECORD_STREAM_DIR = THIRD_INTERVAL_BASE_DIR + "/recordstreams";
	private static final String THIRD_EVENT_STREAM_DIR = THIRD_INTERVAL_BASE_DIR + "/eventsStreams";

	public static void main(String... args) throws UnknownHederaFunctionality, IOException {
//		final var rcdLoc = FIRST_RECORD_STREAM_DIR + "/2019-09-14T11_18_30.114968Z.rcd";
//		final var evtsLoc = FIRST_EVENT_STREAM_DIR + "/2019-09-14T11_18_30.027183Z.evts";
//		compareFiles(rcdLoc, evtsLoc, false);

//		final var rcdLocs = orderedFilesFrom(THIRD_RECORD_STREAM_DIR, ".rcd");
//		final var evtLocs = orderedFilesFrom(THIRD_EVENT_STREAM_DIR, ".evts");
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

		/* ============================================================================== */
		// 0.0.16378
		final var firstProblemPayer = AccountID.newBuilder().setAccountNum(16378).build();
		final var firstAnalysisLoc = "payer16378-2019-09-14.txt";
		analyzeDiscrepantAccount(
				firstProblemPayer, FIRST_RECORD_STREAM_DIR, FIRST_EVENT_STREAM_DIR, firstAnalysisLoc,
				75_494_726);

		// 0.0.909
//		final var secondProblemPayer = AccountID.newBuilder().setAccountNum(909).build();
//		final var secondAnalysisLoc = "payer909-2019-09-17.txt";
//		analyzeDiscrepantAccount(
//				secondProblemPayer, SECOND_RECORD_STREAM_DIR, SECOND_EVENT_STREAM_DIR, secondAnalysisLoc);

		// 0.0.57
//		final var thirdProblemPayer = AccountID.newBuilder().setAccountNum(57).build();
//		final var thirdAnalysisLoc = "payer57-2019-09-18.txt";
//		analyzeDiscrepantAccount(
//				thirdProblemPayer, THIRD_RECORD_STREAM_DIR, THIRD_EVENT_STREAM_DIR, thirdAnalysisLoc);


//		final var bytes = Files.readAllBytes(Paths.get(firstEvtsLoc));
//		System.out.println(hex(Arrays.copyOfRange(bytes, 0, 2056)));
	}

	private static AtomicLong payerBalance = new AtomicLong();

	private static void analyzeDiscrepantAccount(
			final AccountID payer,
			final String recordStreamsLoc,
			final String eventStreamsLoc,
			final String analysisOutLoc,
			final long initialBalance
	) {
		payerBalance.set(initialBalance);

		final Map<ByteString, Pair<TransactionBody, Instant>> fromEvents =
				new TreeMap<>(ByteString.unsignedLexicographicalComparator());
		final Map<ByteString, Pair<TransactionBody, Instant>> fromRecords =
				new TreeMap<>(ByteString.unsignedLexicographicalComparator());
		final Map<ByteString, ExchangeRate> knownRates = new HashMap<>();
		final Map<ByteString, Long> payerBalanceChanges = new HashMap<>();

		for (final var rcdFile : orderedFilesFrom(recordStreamsLoc, ".rcd")) {
			final var histories = parseOldRecordFile(rcdFile.getPath());
			for (final var history : histories) {
				final var signedTxn = history.getSignedTxn();
				final var txn = txnFrom(signedTxn);
				final var key = txn.toByteString();
				final var record = history.getRecord();
				final var at = timestampToInstant(record.getConsensusTimestamp());
				knownRates.put(key, rateNow(record.getReceipt().getExchangeRate(), at));
				if (txn.getTransactionID().getAccountID().equals(payer)) {
					System.out.println("Found " + safeFunctionOf(txn) + "@" + at
							+ " (resolved to " + record.getReceipt().getStatus() + ")");
					final var meta = Pair.of(txn, at);
					if (fromRecords.containsKey(key)) {
						System.out.println("OOPS! Duplicate of: " + txn);
					} else {
						fromRecords.put(key, meta);
					}
					long changeHere = 0;
					for (final var adjust : record.getTransferList().getAccountAmountsList()) {
						if (adjust.getAccountID().equals(payer)) {
							changeHere += adjust.getAmount();
						}
					}
					payerBalanceChanges.put(key, changeHere);
				}
			}
		}

		for (final var evtsFile : orderedFilesFrom(eventStreamsLoc, ".evts")) {
			final var histories = horrorParsing(evtsFile.getPath());
//			System.out.println("Read " + histories.size() + " user txns from " + evtsFile.getPath());
			ExchangeRate closestRate = null;
			for (final var history : histories) {
				final var signedTxn = history.getSignedTxn();
				final var txn = txnFrom(signedTxn);
				final var key = txn.toByteString();
				if (knownRates.containsKey(key)) {
					closestRate = knownRates.get(key);
				}
				if (txn.getTransactionID().getAccountID().equals(payer)) {
					final var record = history.getRecord();
					final var at = timestampToInstant(record.getConsensusTimestamp());
					System.out.println("(EVTS) Found " + safeFunctionOf(txn) + "@" + at
							+ " (resolved to " + record.getReceipt().getStatus() + ")");
					if (payerBalanceChanges.containsKey(key)) {
						payerBalance.addAndGet(payerBalanceChanges.get(key));
						System.out.println(" -> this changed payer balance to " + payerBalance.get());
					} else {
						System.out.println(" ! closest rate-set for simulation is " + readableRate(closestRate));
					}
					final var meta = Pair.of(txn, at);
					if (fromEvents.containsKey(key)) {
						System.out.println("OOPS! (EVTS) Duplicate of: " + txn);
					} else {
						fromEvents.put(key, meta);
					}
				}
			}
		}

		try (final var out = Files.newBufferedWriter(Paths.get(analysisOutLoc))) {
			out.write(fromEvents.size() + " from events, " + fromRecords.size() +  " from records\n");
			out.write("--- IN events, NOT IN records ---\n");
			for (final var key : fromEvents.keySet()) {
				if (!fromRecords.containsKey(key)) {
					final var missing = fromEvents.get(key);
					out.write("@ inferred "
							+ missing.getRight() + ", missing record for event txn: " + missing.getKey() + "\n");
				}
			}

			out.write("--- IN records, NOT IN events ---\n");
			final var recordKeys = new HashSet<>(fromRecords.keySet());
			recordKeys.removeAll(fromEvents.keySet());
			out.write("  -> # = " + recordKeys.size() +  "\n");
			for (final var key : recordKeys) {
				final var item = fromRecords.get(key);
				out.write("@" + item.getValue() + " -> " + item.getKey() + "\n");
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String readableRate(ExchangeRate rate) {
		return rate.getHbarEquiv() + "ℏ <-> " + rate.getCentEquiv() + "¢";
	}

	private static ExchangeRate rateNow(final ExchangeRateSet rateSet, final Instant now) {
		final var firstExpiry = Instant.ofEpochSecond(rateSet.getCurrentRate().getExpirationTime().getSeconds());
		return now.isAfter(firstExpiry) ? rateSet.getNextRate() : rateSet.getCurrentRate();
	}

	private static String safeFunctionOf(final TransactionBody txn) {
		try {
			return "" + functionOf(txn);
		} catch (UnknownHederaFunctionality unknownHederaFunctionality) {
			return "N/A";
		}
	}

	private static List<RecordParser.TxnHistory> horrorParsing(final String loc) {
		try {
			final var eventsHere = LegacyDeserialization.loadEventFile(loc);
			final List<RecordParser.TxnHistory> results = new ArrayList<>();
			for (final var event : eventsHere) {
				final var firstConsTime = event.getKey();
				long nanoOffset = -1;
				for (final var txn : event.getRight()) {
					nanoOffset++;
					if (txn.getRight()) {
						// System txn, skip
						continue;
					}
					final var signedTxn = Transaction.parseFrom(txn.getLeft());
					final var mockRecord = TransactionRecord.newBuilder()
							.setConsensusTimestamp(MiscUtils.asTimestamp(firstConsTime.plusNanos(nanoOffset)))
//							.setConsensusTimestamp(MiscUtils.asTimestamp(firstConsTime))
							.setReceipt(TransactionReceipt.newBuilder().setStatus(UNKNOWN))
							.build();
					results.add(new RecordParser.TxnHistory(signedTxn, mockRecord));
				}
			}
			return results;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static List<Item> uglyParsing(final String loc, final boolean verbose) {
		final byte[] marker = unhex("0a");
		final var l = marker.length;
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
		final var histories = rcdFile.getTxnHistories();
//		System.out.println("Found " + histories.size() + " records in " + loc);
		return histories;
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

	private static TransactionBody txnFrom(final Transaction signedTxn) {
		var txn = signedTxn.getBody();
		if (!txn.hasTransactionID()) {
			try {
				txn = TransactionBody.parseFrom(signedTxn.getBodyBytes());
//				System.out.println("Had to re-parse to find payer 0.0."
//						+ txn.getTransactionID().getAccountID().getAccountNum());
			} catch (InvalidProtocolBufferException e) {
				e.printStackTrace();
			}
		}
		return txn;
	}
}
