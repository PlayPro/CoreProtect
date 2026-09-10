package net.coreprotect.database.statement;

import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.utility.ItemUtils;

public class ItemStatement {

    private ItemStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(ConsumerWriteBatch batch, int batchCount, int time, int id, int wid, int x, int y, int z, int type, Object data, int amount, int action) {
        try {
            byte[] byteData = ItemUtils.convertByteData(data);
            batch.addItem(batchCount, time, id, wid, x, y, z, type, byteData, amount, action, 0);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }
}
