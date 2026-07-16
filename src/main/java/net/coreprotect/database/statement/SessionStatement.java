package net.coreprotect.database.statement;

import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;

public class SessionStatement {

    private SessionStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(ConsumerWriteBatch batch, int batchCount, int time, int user, int wid, int x, int y, int z, int action) {
        try {
            batch.addSession(batchCount, time, user, wid, x, y, z, action);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }
}
