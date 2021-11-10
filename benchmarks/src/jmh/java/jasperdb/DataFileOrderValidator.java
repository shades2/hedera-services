package jasperdb;

import com.hedera.services.state.virtual.ContractKeySerializer;
import com.hedera.services.state.virtual.ContractKeySupplier;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.ContractValueSupplier;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.jasperdb.files.DataFileIterator;
import com.swirlds.jasperdb.files.DataFileMetadata;
import com.swirlds.jasperdb.files.DataItemSerializer;
import com.swirlds.jasperdb.files.hashmap.BucketSerializer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class DataFileOrderValidator {
	public static void main(String[] args) throws IOException {
		System.out.println("args = " + Arrays.toString(args));
		for (var filePath : args) {
			System.out.println("==========================================================================================");
			Path dataFile = Path.of(filePath).toAbsolutePath();
			System.out.println("dataFile = " + dataFile);
			DataFileMetadata dataFileMetadata = new DataFileMetadata(dataFile);
			System.out.println("dataFileMetadata = " + dataFileMetadata);

			final DataItemSerializer dataItemSerializer;
			if (dataFile.toString().contains("internalHashes")) {
				dataItemSerializer = new VirtualInternalRecordSerializer();
			} else if (dataFile.toString().contains("objectKeyToPath")) {
				dataItemSerializer = new BucketSerializer(new ContractKeySerializer());
			} else if (dataFile.toString().contains("pathToHashKeyValue")) {
				dataItemSerializer = new VirtualLeafRecordSerializer<>(
								(short) 1, DigestType.SHA_384,
								(short) 1, DataFileCommon.VARIABLE_DATA_SIZE, new ContractKeySupplier(),
								(short) 1,ContractValue.SERIALIZED_SIZE, new ContractValueSupplier(),
								true);
			} else {
				throw new IOException("Unknown file type: "+dataFile);
			}


			try (DataFileIterator dataFileIterator = new DataFileIterator(dataFile,dataFileMetadata, dataItemSerializer)) {
				long lastKey = -1;
				while (dataFileIterator.next()) {
					final var key = dataFileIterator.getDataItemsKey();
					if (key < lastKey) {
						System.err.println("!!!!! Key ["+key+"] is lower than lastKey ["+lastKey+"]");
						System.exit(1);
					}
					lastKey = key;
				}
			}

		}
	}
}
