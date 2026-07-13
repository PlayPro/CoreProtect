package net.coreprotect.database.statement;

import java.sql.PreparedStatement;
import java.sql.SQLException;

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
            setValues(preparedStmt, time, id, wid, x, y, z, type, data, meta, blockData, action, rolledBack, nextRowId(preparedStmt));
            preparedStmt.addBatch();

            if (batchCount > 0 && batchCount % 1000 == 0) {
                preparedStmt.executeBatch();
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static long insertImmediate(PreparedStatement preparedStmt, int time, int id, int wid, int x, int y, int z, int type, long data, SerializedBlockMeta meta, String blockData, int action, int rolledBack) throws Exception {
        long rowid = nextRowId(preparedStmt);
        setValues(preparedStmt, time, id, wid, x, y, z, type, data, meta, blockData, action, rolledBack, rowid);
        int updated = preparedStmt.executeUpdate();
        if (updated != 1) {
            throw new SQLException("Expected one row for block insert, updated " + updated);
        }
        return rowid;
    }

    private static long nextRowId(PreparedStatement preparedStmt) throws Exception {
        return CoreProtect.getInstance().rowNumbers().nextRowNumber("block", preparedStmt.getConnection());
    }

    private static void setValues(PreparedStatement preparedStmt, int time, int id, int wid, int x, int y, int z, int type, long data, SerializedBlockMeta meta, String blockData, int action, int rolledBack, long rowid) throws Exception {
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
        preparedStmt.setLong(13, rowid);
    }
}
