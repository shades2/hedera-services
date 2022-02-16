package com.hedera.services;

import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.internals.FilePart;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.SolidityFnResult;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.swirlds.blob.internal.db.DbManager;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleDepthFirstIterator;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.route.MerkleRouteFactory;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class LoadState {

	private static void registerConstructables() {
		try {

			DbManager.getInstance(true);

			ConstructableRegistry.registerConstructables("com.swirlds");
			ConstructableRegistry.registerConstructables("com.hedera");
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(MerkleAccountState.class, MerkleAccountState::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(ExpirableTxnRecord.class, ExpirableTxnRecord::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(TxnReceipt.class, TxnReceipt::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(TxnId.class, TxnId::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(CurrencyAdjustments.class, CurrencyAdjustments::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(SolidityFnResult.class, SolidityFnResult::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(EntityId.class, EntityId::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(MerkleAccountTokens.class, MerkleAccountTokens::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(MerkleNetworkContext.class, MerkleNetworkContext::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(MerkleSpecialFiles.class, MerkleSpecialFiles::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(FilePart.class, FilePart::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(RecordsRunningHashLeaf.class, RecordsRunningHashLeaf::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(ContractKey.class, ContractKey::new));
			ConstructableRegistry.registerConstructable(
					new ClassConstructorPair(ContractValue.class, ContractValue::new));

		} catch (final ConstructableRegistryException ex) {
			throw new RuntimeException(ex);
		}
	}

	private static State loadState(final File stateFile) {

		try {
			System.out.println("loading state @ " + stateFile);
			final Pair<Hash, SignedState> pair = SignedStateFileManager.readSignedStateFromFile(stateFile);
			final State state = pair.getRight().getState();
			System.out.println("hashing state @ " + stateFile);
			CryptoFactory.getInstance().digestTreeAsync(state).get();
			return state;

		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private static class ComparisonIterator extends MerkleDepthFirstIterator<MerkleNode, MerkleNode> {

		private final MerkleNode rootB;

		public ComparisonIterator(final MerkleNode rootA, final MerkleNode rootB) {
			super(rootA);
			this.rootB = rootB;
		}

		@Override
		protected boolean shouldNodeBeVisited(MerkleNode nodeA) {
			if (nodeA == null) {
				return false;
			}

			MerkleNode nodeB = null;
			try {
				nodeB = rootB.getNodeAtRoute(nodeA.getRoute());
			} catch (final Exception ignored) {

			}

			if (nodeB == null) {
				return true;
			}

			final Hash hashA = nodeA.getHash();
			final Hash hashB = nodeB.getHash();

			return !hashA.equals(hashB);
		}
	}

	private static List<MerkleLeaf> compareStates(final State stateA, final State stateB) {
//		final Iterator<MerkleNode> iterator = new ComparisonIterator(stateA, stateB);
		final Iterator<MerkleNode> iterator = new MerkleDepthFirstIterator<>(stateA);

		final List<MerkleLeaf> nodes = new LinkedList<>();


		iterator.forEachRemaining((final MerkleNode nodeA) -> {
			MerkleNode nodeB = null;
			try {
				nodeB = stateB.getNodeAtRoute(nodeA.getRoute());
			} catch (final Exception ignored) {

			}

			if (nodeA == null) {
				return;
			}

			if (nodeB != null && !nodeA.getHash().equals(nodeB.getHash())) {
				// These nodes are equal, don't report them.
				// This check is not necessary if ComparisonIterator is used
				return;
			}

			if (nodeA instanceof MerkleLeaf || nodeB instanceof MerkleLeaf) {
				if (nodeA instanceof MerkleInternal) {
					nodes.add(null);
				} else {
					nodes.add((MerkleLeaf) nodeA);
				}

				if (nodeB instanceof MerkleInternal) {
					nodes.add(null);
				} else {
					nodes.add((MerkleLeaf) nodeB);
				}
			}

			if (nodeB == null) {

				final String classA = nodeA.getClass().getSimpleName();
				final String position = nodeA.getRoute().toString();

				System.out.println("Mismatched types. Type A = " + classA + ", Type B = NULL @ " + position);
				return;
			}

			if (nodeA.getClassId() != nodeB.getClassId()) {
				System.out.println("mismatched class IDs @ " + nodeA.getRoute() + ": " +
						nodeA.getClass().getSimpleName() + " vs " + nodeB.getClass().getSimpleName());
				return;
			}

			if (!nodeA.getHash().equals(nodeB.getHash())) {
				System.out.println("mismatched hashes @ " + nodeA.getRoute() + " for type " +
						nodeA.getClass().getSimpleName());
			}
		});

		return nodes;
	}

	private static final List<Integer> steps =
			List.of(0, 4, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0, 1, 1, 1);
	private static final MerkleRoute route = MerkleRouteFactory.buildRoute(steps);

	private static void lookAtBadLeaves(final State stateA, final State stateB) {

		final FCQueue<ExpirableTxnRecord> queueA = stateA.getNodeAtRoute(route).cast();
		final FCQueue<ExpirableTxnRecord> queueB = stateB.getNodeAtRoute(route).cast();

		System.out.println("Size of queue A: " + queueA.size());
		System.out.println("Size of queue B: " + queueB.size());

		System.out.println("Elements in A: ");
		for (final ExpirableTxnRecord record : queueA) {
			System.out.println("  " + record);
		}

		System.out.println("-------------------------------------------------------");

		System.out.println("Elements in B: ");
		for (final ExpirableTxnRecord record : queueB) {
			System.out.println("  " + record);
		}

	}

	private static void extractContract(final State state) throws IOException {

		final ServicesState sstate = state.getSwirldState().cast();

		final VirtualBlobValue value =
				sstate.storage().get(new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE, 88011));

		final BufferedOutputStream out =
				new BufferedOutputStream(new FileOutputStream(new File("badContract.dat")));
		out.write(value.getData());
		out.close();

	}

	public static void readMismatchedNodes(final String path) throws IOException {
		final SerializableDataInputStream in = new SerializableDataInputStream(new FileInputStream(path));
		final List<MerkleLeaf> nodes = in.readSerializableList(Integer.MAX_VALUE);

		for (int index = 0; index + 1 < nodes.size(); index += 2) {
			final MerkleLeaf nodeA = nodes.get(index);
			final MerkleLeaf nodeB = nodes.get(index + 1);

			System.out.println("-------------");
			System.out.println(nodeA);
			System.out.println("---");
			System.out.println(nodeB);
		}
	}

	public static void main(final String[] args) throws IOException {


		try {

			registerConstructables();

			if (args.length != 2) {
				System.err.println("Invalid number of arguments, 2 arguments expected (states to compare)");
				return;
			}

			final File fileA = new File(args[0]);
			final File fileB = new File(args[1]);

			System.out.println("comparing " + fileA + " with " + fileB);

			final State stateA = loadState(fileA);
			final State stateB = loadState(fileB);

			System.out.println("Hash A: " + stateA.getHash());
			System.out.println("Hash B: " + stateB.getHash());

			final List<MerkleLeaf> nodes = compareStates(stateA, stateB);

			final SerializableDataOutputStream out = new SerializableDataOutputStream(new FileOutputStream
					("mismatchedNodes.dat"));
			out.writeSerializableList(nodes, true, false);
			out.close();

//			lookAtBadLeaves(stateA, stateB);
//			extractContract(stateA);

		} catch (final Exception ex) {
			ex.printStackTrace();
		}
	}

}
