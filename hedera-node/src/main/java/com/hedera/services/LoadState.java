package com.hedera.services;

import com.swirlds.blob.internal.db.DbManager;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleDepthFirstIterator;
import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.util.Iterator;

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

	public static void main(final String[] args) {

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

			compareStates(stateA, stateB);

		} catch (final Exception ex) {
			ex.printStackTrace();
		}
	}

}
