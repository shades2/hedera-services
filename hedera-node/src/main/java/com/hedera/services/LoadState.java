package com.hedera.services;

import com.swirlds.blob.internal.db.DbManager;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
            final Pair<Hash, SignedState> pair = SignedStateFileManager.readSignedStateFromFile(stateFile);

            System.out.println("loading state @ " + stateFile);
            final State state = pair.getRight().getState();
            System.out.println("hashing state @ " + stateFile);
            CryptoFactory.getInstance().digestTreeAsync(state).get();
            return state;

        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void compareStates(final State state1, final State state2) {

        final Map<MerkleRoute, Hash> state1Hashes = new HashMap<>();

        state1.forEachNode((final MerkleNode node) -> {
            if (node != null) {
                state1Hashes.put(node.getRoute(), node.getHash());
            }
        });

        state2.forEachNode((final MerkleNode node2) -> {

            if (node2 != null) {
                final Hash node1Hash = state1Hashes.get(node2.getRoute());

                if (!node2.getHash().equals(node1Hash)) {
                    System.out.println("differing hashes, node type = " +
                            node2.getClass().getSimpleName() + " @ " + node2.getRoute());
                }
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

            final File file1 = new File(args[0]);
            final File file2 = new File(args[1]);

            System.out.println("comparing " + file1 + " with " + file2);

            final State state1 = loadState(file1);
            final State state2 = loadState(file2);

            compareStates(state1, state2);

        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }

}
