package com.hedera.services.stream;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.HashAlgorithm;
import com.hederahashgraph.api.proto.java.HashObject;
import com.hederahashgraph.api.proto.java.RecordStreamFile;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.SignatureFile;
import com.hederahashgraph.api.proto.java.SignatureType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.LinkedObjectStreamUtilities;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.stream.StreamType;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateSigFilePath;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class RecordStreamFileWriterTest {
	RecordStreamFileWriterTest() {}

	@BeforeEach
	void setUp() throws NoSuchAlgorithmException {
		subject = new RecordStreamFileWriter<>(
				expectedExportDir(),
				logPeriodMs,
				signer,
				false,
				streamType
		);
		messageDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
	}

	@Test
	void recordAndSignatureFilesAreCreatedAsExpected() throws IOException, NoSuchAlgorithmException {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
		final var firstBlockEntireFileSignature = "entireSignatureBlock1".getBytes(StandardCharsets.UTF_8);
		final var firstBlockMetadataSignature = "metadataSignatureBlock1".getBytes(StandardCharsets.UTF_8);
		final var secondBlockEntireFileSignature = "entireSignatureBlock2".getBytes(StandardCharsets.UTF_8);
		final var secondBlockMetadataSignature = "metadataSignatureBlock2".getBytes(StandardCharsets.UTF_8);
		given(signer.sign(any()))
				.willReturn(firstBlockEntireFileSignature)
				.willReturn(firstBlockMetadataSignature)
				.willReturn(secondBlockEntireFileSignature)
				.willReturn(secondBlockMetadataSignature);
		final var firstTransactionInstant = LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);
		// set initial running hash
		messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
		final var startRunningHash = new Hash(messageDigest.digest());
		subject.setRunningHash(startRunningHash);

		// send RSOs for block 1
		final var firstBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(4, 1, firstTransactionInstant);
		firstBlockRSOs.forEach(subject::addObject);
		// send RSOs for block 2
		final var secondBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(8, 2,
				firstTransactionInstant.plusSeconds(logPeriodMs / 1000));
		secondBlockRSOs.forEach(subject::addObject);
		// send single RSO for block 3 in order to finish block 2 and create its files
		generateNRecordStreamObjectsForBlockMStartingFromT(1, 3, firstTransactionInstant.plusSeconds(2 * logPeriodMs / 1000))
				.forEach(subject::addObject);

		// then
		assertRecordStreamFiles(
				1L,
				firstBlockRSOs,
				startRunningHash,
				firstBlockEntireFileSignature,
				firstBlockMetadataSignature);
		assertRecordStreamFiles(
				2L,
				secondBlockRSOs,
				firstBlockRSOs.get(firstBlockRSOs.size() - 1).getRunningHash().getHash(),
				secondBlockEntireFileSignature,
				secondBlockMetadataSignature);
	}

	private List<RecordStreamObject> generateNRecordStreamObjectsForBlockMStartingFromT(
			final int numberOfRSOs,
			final int blockNumber,
			final Instant firstBlockTransactionInstant)
	{
		final var recordStreamObjects = new ArrayList<RecordStreamObject>();
		for (int i = 0; i < numberOfRSOs; i++) {
			final var timestamp =
					Timestamp.newBuilder()
							.setSeconds(firstBlockTransactionInstant.getEpochSecond())
							.setNanos(1000 * i);
			final var transactionRecord =
					TransactionRecord.newBuilder().setConsensusTimestamp(timestamp);
			final var transaction =
					Transaction.newBuilder()
							.setSignedTransactionBytes(ByteString.copyFrom(
									("block #" + blockNumber + ", transaction #" + i).getBytes(StandardCharsets.UTF_8)));
			final var recordStreamObject =
					new RecordStreamObject(
							transactionRecord.build(),
							transaction.build(),
							Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos())
					);
			final var hashInput = (HASH_PREFIX + (blockNumber + i)).getBytes(StandardCharsets.UTF_8);
			recordStreamObject.getRunningHash().setHash(new Hash(messageDigest.digest(hashInput)));
			recordStreamObject.withBlockNumber(blockNumber);
			recordStreamObjects.add(recordStreamObject);
		}
		return recordStreamObjects;
	}

	private void assertRecordStreamFiles(
			final long expectedBlock,
			final List<RecordStreamObject> blockRSOs,
			final Hash startRunningHash,
			final byte[] expectedEntireFileSignature,
			final byte[] expectedMetadataSignature
	) throws IOException, NoSuchAlgorithmException {
		final var firstTxnTimestamp = blockRSOs.get(0).getTimestamp();
		final var recordStreamFilePath =
				subject.generateStreamFilePath(
						Instant.ofEpochSecond(firstTxnTimestamp.getEpochSecond(), firstTxnTimestamp.getNano()));
		final var recordStreamFilePair = readRecordStreamFile(recordStreamFilePath);

		assertEquals(RECORD_STREAM_VERSION, recordStreamFilePair.getLeft());
		final var recordStreamFileOptional = recordStreamFilePair.getRight();
		assertTrue(recordStreamFileOptional.isPresent());
		final var recordStreamFile = recordStreamFileOptional.get();

		assertRecordFile(expectedBlock, blockRSOs, startRunningHash, recordStreamFile);
		assertSignatureFile(
				recordStreamFilePath,
				expectedEntireFileSignature,
				expectedMetadataSignature,
				recordStreamFilePair.getLeft(),
				recordStreamFile
		);
	}

	private void assertRecordFile(
			final long expectedBlock,
			final List<RecordStreamObject> blockRSOs,
			final Hash startRunningHash,
			final RecordStreamFile recordStreamFile
	) {
		// assert HAPI semantic version
		assertEquals(recordStreamFile.getHapiProtoVersion(), SemanticVersion.newBuilder()
				.setMajor(FILE_HEADER_VALUES[1])
				.setMinor(FILE_HEADER_VALUES[2])
				.setPatch(FILE_HEADER_VALUES[3]).build()
		);

		// assert startRunningHash
		assertEquals(toProto(startRunningHash), recordStreamFile.getStartObjectRunningHash());

		// assert RSOs
		assertEquals(blockRSOs.size(), recordStreamFile.getRecordFileObjectsCount());
		final var recordFileObjectsList = recordStreamFile.getRecordFileObjectsList();
		for (int i = 0; i < blockRSOs.size(); i++) {
			final var expectedRSO = blockRSOs.get(i);
			final var actualRSOProto = recordFileObjectsList.get(i);
			assertEquals(expectedRSO.getTransaction(), actualRSOProto.getTransaction());
			assertEquals(expectedRSO.getTransactionRecord(), actualRSOProto.getRecord());
		}

		// assert endRunningHash
		final var expectedHashInput = (HASH_PREFIX + (recordStreamFile.getBlockNumber() + (blockRSOs.size() - 1)))
				.getBytes(StandardCharsets.UTF_8);
		assertEquals(toProto(new Hash(messageDigest.digest(expectedHashInput))), recordStreamFile.getEndObjectRunningHash());

		// assert block number
		assertEquals(expectedBlock, recordStreamFile.getBlockNumber());
	}

	private void assertSignatureFile(
			final String streamFilePath,
			final byte[] expectedEntireFileSignature,
			final byte[] expectedMetadataSignature,
			final Integer recordStreamVersion,
			final RecordStreamFile recordStreamFileProto
	) throws IOException, NoSuchAlgorithmException {
		final var recordStreamFile = new File(streamFilePath);
		final var signatureFileOptional = readRecordStreamSignatureFile(generateSigFilePath(recordStreamFile));
		assertTrue(signatureFileOptional.isPresent());
		final var signatureFile = signatureFileOptional.get();

		/* --- assert entire file signature --- */
		final var entireFileSignatureObject = signatureFile.getFileSignature();
		// assert entire file hash
		final var expectedEntireHash = LinkedObjectStreamUtilities.computeEntireHash(recordStreamFile);
		final var actualEntireHash = entireFileSignatureObject.getHashObject();
		assertEquals(HashAlgorithm.SHA_384, actualEntireHash.getAlgorithm());
		assertEquals(expectedEntireHash.getDigestType().digestLength(), actualEntireHash.getLength());
		assertArrayEquals(expectedEntireHash.getValue(), actualEntireHash.getHash().toByteArray());
		// assert entire file signature
		assertEquals(SignatureType.SHA_384_WITH_RSA, entireFileSignatureObject.getType());
		assertEquals(expectedEntireFileSignature.length, entireFileSignatureObject.getLength());
		assertEquals(101 - expectedEntireFileSignature.length, entireFileSignatureObject.getChecksum());
		assertArrayEquals(expectedEntireFileSignature, entireFileSignatureObject.getSignature().toByteArray());

		/* --- assert metadata signature --- */
		final var expectedMetaHash = getMetadataHashFrom(recordStreamVersion, recordStreamFileProto);
		final var metadataSignatureObject = signatureFile.getMetadataSignature();
		final var actualMetaHash = metadataSignatureObject.getHashObject();
		// assert metadata hash
		assertEquals(HashAlgorithm.SHA_384, actualMetaHash.getAlgorithm());
		assertEquals(expectedMetaHash.getDigestType().digestLength(), actualMetaHash.getLength());
		assertArrayEquals(expectedMetaHash.getValue(), actualMetaHash.getHash().toByteArray());
		// assert metadata signature
		assertEquals(SignatureType.SHA_384_WITH_RSA, metadataSignatureObject.getType());
		assertEquals(expectedMetadataSignature.length, metadataSignatureObject.getLength());
		assertEquals(101 - expectedMetadataSignature.length, metadataSignatureObject.getChecksum());
		assertArrayEquals(expectedMetadataSignature, metadataSignatureObject.getSignature().toByteArray());
	}

	private HashObject toProto(final Hash hash) {
		return HashObject.newBuilder()
				.setAlgorithm(HashAlgorithm.SHA_384)
				.setLength(hash.getDigestType().digestLength())
				.setHash(ByteString.copyFrom(hash.getValue()))
				.build();
	}

	private Hash getMetadataHashFrom(final Integer version, final RecordStreamFile recordStreamFile) {
		try (final var outputStream = new SerializableDataOutputStream(new HashingOutputStream(messageDigest))) {
			// digest file header
			outputStream.writeInt(version);
			final var hapiProtoVersion = recordStreamFile.getHapiProtoVersion();
			outputStream.writeInt(hapiProtoVersion.getMajor());
			outputStream.writeInt(hapiProtoVersion.getMinor());
			outputStream.writeInt(hapiProtoVersion.getPatch());

			// digest startRunningHash
			outputStream.writeSerializable(
					new Hash(recordStreamFile.getStartObjectRunningHash().getHash().toByteArray(), DigestType.SHA_384),
					true
			);
			// digest endRunningHash
			outputStream.writeSerializable(
					new Hash(recordStreamFile.getEndObjectRunningHash().getHash().toByteArray(), DigestType.SHA_384),
					true
			);
			// digest block number
			outputStream.writeLong(recordStreamFile.getBlockNumber());

			return new Hash(messageDigest.digest(), DigestType.SHA_384);
		} catch (IOException e) {
			return new Hash("error".getBytes(StandardCharsets.UTF_8));
		}
	}

	private Pair<Integer, Optional<RecordStreamFile>> readRecordStreamFile(final String fileLoc) {
		try (final var fin = new FileInputStream(fileLoc)) {
			final int recordFileVersion = ByteBuffer.wrap(fin.readNBytes(4)).getInt();
			final var recordStreamFile = RecordStreamFile.parseFrom(fin);
			return Pair.of(recordFileVersion, Optional.ofNullable(recordStreamFile));
		} catch (IOException e) {
			return Pair.of(-1, Optional.empty());
		}
	}

	private Optional<SignatureFile> readRecordStreamSignatureFile(final String fileLoc) {
		try (final var fin = new FileInputStream(fileLoc)) {
			final var recordStreamSignatureFile = SignatureFile.parseFrom(fin);
			return Optional.ofNullable(recordStreamSignatureFile);
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	@BeforeAll
	static void beforeAll() {
		final var file = new File(expectedExportDir());
		if (!file.exists()) {
			assertTrue(file.mkdir());
		}
	}

	@AfterEach
	void tearDown() throws IOException {
		Files.walk(Path.of(expectedExportDir()))
				.map(Path::toFile)
				.forEach(File::delete);
	}

	@AfterAll
	static void afterAll() {
		final var file = new File(expectedExportDir());
		if (file.exists() && file.isDirectory()) {
			file.delete();
		}
	}

	private static String expectedExportDir() {
		return dynamicProperties.pathToBalancesExportDir() + File.separator + "recordStreamWriterTest";
	}

	private final static long logPeriodMs = 2000L;
	private static final String HASH_PREFIX = "randomPrefix";
	private static final int RECORD_STREAM_VERSION = 6;
	private static final int[] FILE_HEADER_VALUES = {
			RECORD_STREAM_VERSION,
			0,  // HAPI Major version
			27, // HAPI Minor version
			0   // HAPI Patch version
	};

	@Mock
	private StreamType streamType;
	@Mock
	private Signer signer;
	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private RecordStreamFileWriter<RecordStreamObject> subject;

	private MessageDigest messageDigest;
	private final static MockGlobalDynamicProps dynamicProperties = new MockGlobalDynamicProps();
}