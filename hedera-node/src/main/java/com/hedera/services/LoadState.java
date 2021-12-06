package com.hedera.services;

import com.swirlds.blob.internal.db.DbManager;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;

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

    public static void main(final String[] args) {

        if (args.length != 2) {
            System.err.println("Invalid number of arguments, 2 arguments expected (states to compare)");
            return;
        }

        final File state1 = new File(args[0]);
        final File state2 = new File(args[1]);


    }

}
