package com.hedera.services;

import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.state.submerkle.NftAdjustments;
import com.hedera.services.state.submerkle.SolidityFnResult;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.utils.EntityNum;
import com.swirlds.blob.internal.db.DbManager;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleDepthFirstIterator;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.route.MerkleRouteFactory;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class LoadState {

	private static void registerConstructables() {
		try {

			DbManager.getInstance(true);

			ConstructableRegistry.registerConstructables("com.swirlds");
			ConstructableRegistry.registerConstructables("com.hedera");

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

	private static void compareStates(final State stateA, final State stateB) {
		final Iterator<MerkleNode> iterator = new ComparisonIterator(stateA, stateB);

		iterator.forEachRemaining((final MerkleNode nodeA) -> {
			MerkleNode nodeB = null;
			try {
				nodeB = stateB.getNodeAtRoute(nodeA.getRoute());
			} catch (final Exception ignored) {

			}

			if (nodeA == null) {
				return;
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

	}

	// Extract all FCQueue instances that are start with the route 0->4
	private static void extractFCQueues(final State stateA, final State stateB) throws IOException {

		final Set<EntityNum> fishyKeys = new HashSet<>();

		final Iterator<MerkleNode> iterator = new ComparisonIterator(stateA, stateB);

		iterator.forEachRemaining((final MerkleNode nodeA) -> {
			MerkleNode nodeB = null;
			try {
				nodeB = stateB.getNodeAtRoute(nodeA.getRoute());
			} catch (final Exception ignored) {

			}

			if (nodeA == null) {

				if (nodeB instanceof MerkleAccountState) {
					fishyKeys.add(new EntityNum(((MerkleAccountState) nodeB).number()));
				}

				return;
			}

			if (nodeB == null) {

				if (nodeA instanceof MerkleAccountState) {
					fishyKeys.add(new EntityNum(((MerkleAccountState) nodeA).number()));
				}

				return;
			}

			if (!nodeA.getHash().equals(nodeB.getHash())) {

				if (nodeA instanceof MerkleAccountState) {
					fishyKeys.add(new EntityNum(((MerkleAccountState) nodeA).number()));
				}

				if (nodeB instanceof MerkleAccountState) {
					fishyKeys.add(new EntityNum(((MerkleAccountState) nodeB).number()));
				}
			}
		});

		final MerkleMap<EntityNum, MerkleAccount> mapA = ((ServicesState) stateA.getSwirldState()).accounts();
		final MerkleMap<EntityNum, MerkleAccount> mapB = ((ServicesState) stateB.getSwirldState()).accounts();

		final List<FCQueue<ExpirableTxnRecord>> nodeAList = new LinkedList<>();
		final List<FCQueue<ExpirableTxnRecord>> nodeBList = new LinkedList<>();

		for (final EntityNum key : fishyKeys) {
			final MerkleAccount accountA = mapA.get(key);
			nodeAList.add(accountA == null ? null : accountA.records());

			final MerkleAccount accountB = mapB.get(key);
			nodeBList.add(accountB == null ? null : accountB.records());
		}

		final SerializableDataOutputStream out =
				new SerializableDataOutputStream(new FileOutputStream("fcqueue-dump.dat"));

		out.writeSerializableList(nodeAList, true, false);
		out.writeSerializableList(nodeBList, true, false);

		System.out.println("Wrote " + nodeAList + " fishy values");

		out.close();

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

	public static void main(final String[] args) throws ConstructableRegistryException {

//		ConstructableRegistry.registerConstructable(
//				new ClassConstructorPair(ExpirableTxnRecord.class, ExpirableTxnRecord::new));
//		ConstructableRegistry.registerConstructable(new ClassConstructorPair(TxnReceipt.class, TxnReceipt::new));
//		ConstructableRegistry.registerConstructable(new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
//		ConstructableRegistry.registerConstructable(new ClassConstructorPair(TxnId.class, TxnId::new));
//		ConstructableRegistry.registerConstructable(
//				new ClassConstructorPair(CurrencyAdjustments.class, CurrencyAdjustments::new));
//		ConstructableRegistry.registerConstructable(new ClassConstructorPair(EntityId.class, EntityId::new));
//		ConstructableRegistry.registerConstructable(new ClassConstructorPair(NftAdjustments.class,
//				NftAdjustments::new));
//		ConstructableRegistry.registerConstructable(
//				new ClassConstructorPair(FcTokenAssociation.class, FcTokenAssociation::new));
//		ConstructableRegistry.registerConstructable(
//				new ClassConstructorPair(SolidityFnResult.class, SolidityFnResult::new));

		try {

			registerConstructables();
//
//			final File file = new File("/Users/codylittley/fcqueue-dump.dat");
//			final SerializableDataInputStream in = new SerializableDataInputStream(new FileInputStream(file));
//
//			final List<FCQueue<ExpirableTxnRecord>> nodeAList = in.readSerializableList(Integer.MAX_VALUE);
//			final List<FCQueue<ExpirableTxnRecord>> nodeBList = in.readSerializableList(Integer.MAX_VALUE);
//
//			final List<FCQueue<ExpirableTxnRecord>> filteredA = new ArrayList<>();
//			final List<FCQueue<ExpirableTxnRecord>> filteredB = new ArrayList<>();
//
//			System.out.println("unfiltered size = " + nodeAList.size());
//
//			for (int i = 0; i < nodeAList.size(); i++) {
//				final FCQueue<ExpirableTxnRecord> a = nodeAList.get(i);
//				final FCQueue<ExpirableTxnRecord> b = nodeBList.get(i);
//
//				if (a == null || b == null) {
//					continue;
//				}
//
//				if (!a.getHash().equals(b.getHash())) {
//					filteredA.add(a);
//					filteredB.add(b);
//				}
//			}
//
//			System.out.println("filtered size = " + filteredA.size());
//
//			for (int i = 0; i < filteredA.size(); i++) {
//
//				final EntityId a = filteredA.get(i).peek() == null ? null : filteredA.get(
//						i).peek().getTxnId().getPayerAccount();
//				final EntityId b = filteredB.get(i).peek() == null ? null : filteredB.get(
//						i).peek().getTxnId().getPayerAccount();
//
//				System.out.println(i + ": " + a + " v " + b);
//
//				System.out.println(i + " sizes: " + filteredA.get(i).size() + ", " + filteredB.get(i).size());
//
//			}


//			/*


			if (args.length != 2) {
				System.err.println("Invalid number of arguments, 2 arguments expected (states to compare)");
				return;
			}

			final File fileA = new File(args[0]);
			final File fileB = new File(args[1]);

			System.out.println("comparing " + fileA + " with " + fileB);

			final State stateA = loadState(fileA);
			final State stateB = loadState(fileB);

			System.out.println("State A: " + stateA);
			System.out.println("State B: " + stateB);

//			System.out.println("Hash A: " + stateA.getHash());
//			System.out.println("Hash B: " + stateB.getHash());

//			compareStates(stateA, stateB);
//			lookAtBadLeaves(stateA, stateB);
//			extractContract(stateA);
//			extractFCQueues(stateA, stateB);
//			 */


		} catch (final Exception ex) {
			ex.printStackTrace();
		}
	}

}
