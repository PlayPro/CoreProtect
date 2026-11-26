package net.coreprotect.database.statement;

import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;

public class WorldStatement {

    private WorldStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(ConsumerWriteBatch batch, int batchCount, int id, String world) {
        try {
            batch.addReference(ConsumerWriteBatch.ReferenceKind.WORLD, batchCount, id, world);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }
}
