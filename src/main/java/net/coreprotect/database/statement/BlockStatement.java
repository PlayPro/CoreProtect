package net.coreprotect.database.statement;

import java.sql.PreparedStatement;

import net.coreprotect.CoreProtect;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.serialize.JsonSerialization;
import net.coreprotect.utility.serialize.SerializedBlockMeta;
import net.coreprotect.utility.ErrorReporter;

public class BlockStatement {

    private BlockStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, int time, int id, int wid, int x, int y, int z, int type, long data, SerializedBlockMeta meta, String blockData, int action, int rolledBack) {
        try {
            preparedStmt.setInt(1, time);
            preparedStmt.setInt(2, id);
            preparedStmt.setInt(3, wid);
            preparedStmt.setInt(4, x);
            preparedStmt.setInt(5, y);
            preparedStmt.setInt(6, z);
            preparedStmt.setInt(7, type);
            preparedStmt.setLong(8, data);
            preparedStmt.setString(9, meta != null ? JsonSerialization.GSON.toJson(meta) : null);
            preparedStmt.setString(10, BlockUtils.stringToStringData(blockData, type));
            preparedStmt.setInt(11, action);
            preparedStmt.setInt(12, rolledBack);
            preparedStmt.setLong(13, CoreProtect.getInstance().rowNumbers().nextRowNumber("block", preparedStmt.getConnection()));
            preparedStmt.addBatch();

            if (batchCount > 0 && batchCount % 1000 == 0) {
                preparedStmt.executeBatch();
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }
}
