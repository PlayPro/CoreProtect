package net.coreprotect.database.statement;

import java.util.List;

import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;

public class BlockStatement {

    private BlockStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(ConsumerWriteBatch batch, int batchCount, int time, int id, int wid, int x, int y, int z, int type, int data, List<Object> meta, String blockData, int action, int rolledBack) {
        insertChecked(batch, batchCount, time, id, wid, x, y, z, type, data, meta, blockData, action, rolledBack);
    }

    public static boolean insertChecked(ConsumerWriteBatch batch, int batchCount, int time, int id, int wid, int x, int y, int z, int type, int data, List<Object> meta, String blockData, int action, int rolledBack) {
        try {
            byte[] bBlockData = BlockUtils.stringToByteData(blockData, type);
            byte[] byteData = meta == null ? null : ItemUtils.convertByteData(meta);
            batch.addBlock(batchCount, time, id, wid, x, y, z, type, data, byteData, bBlockData, action, rolledBack);
            return true;
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
            return false;
        }
    }

    public static long insertImmediate(ConsumerWriteBatch batch, int time, int id, int wid, int x, int y, int z, int type, int data, List<Object> meta, String blockData, int action, int rolledBack) throws Exception {
        byte[] bBlockData = BlockUtils.stringToByteData(blockData, type);
        byte[] byteData = meta == null ? null : ItemUtils.convertByteData(meta);
        return batch.addBlockReturningId(time, id, wid, x, y, z, type, data, byteData, bBlockData, action, rolledBack);
    }
}
