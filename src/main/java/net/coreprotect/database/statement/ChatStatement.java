package net.coreprotect.database.statement;

import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;

public class ChatStatement {

    private ChatStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(ConsumerWriteBatch batch, int batchCount, long time, int user, int wid, int x, int y, int z, String message) {
        try {
            batch.addChat(batchCount, time, user, wid, x, y, z, message);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }
}
