package net.coreprotect.database.statement;

import java.sql.PreparedStatement;

import net.coreprotect.CoreProtect;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.serialize.JsonSerialization;

public class ContainerStatement {

    private ContainerStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, int time, int id, int wid, int x, int y, int z, int type, int data, int amount, String itemData, int action, int rolledBack) {
        try {
            preparedStmt.setInt(1, time);
            preparedStmt.setInt(2, id);
            preparedStmt.setInt(3, wid);
            preparedStmt.setInt(4, x);
            preparedStmt.setInt(5, y);
            preparedStmt.setInt(6, z);
            preparedStmt.setInt(7, type);
            preparedStmt.setInt(8, data);
            preparedStmt.setInt(9, amount);
            preparedStmt.setString(10, itemData);
            preparedStmt.setInt(11, action);
            preparedStmt.setInt(12, rolledBack);
            preparedStmt.setLong(13, CoreProtect.getInstance().rowNumbers().nextRowNumber("container", preparedStmt.getConnection()));
            preparedStmt.addBatch();

            if (batchCount > 0 && batchCount % 1000 == 0) {
                preparedStmt.executeBatch();
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static void insertEntity(PreparedStatement preparedStmt, int batchCount, int time, int id, int entitySpawnRowId, int wid, int x, int y, int z, int type, int data, int amount, Object metadata, int action, int rolledBack) {
        try {
            preparedStmt.setInt(1, time);
            preparedStmt.setInt(2, id);
            preparedStmt.setInt(3, entitySpawnRowId);
            preparedStmt.setInt(4, wid);
            preparedStmt.setInt(5, x);
            preparedStmt.setInt(6, y);
            preparedStmt.setInt(7, z);
            preparedStmt.setInt(8, type);
            preparedStmt.setInt(9, data);
            preparedStmt.setInt(10, amount);
            preparedStmt.setString(11, metadata == null ? null : JsonSerialization.GSON.toJson(metadata));
            preparedStmt.setInt(12, action);
            preparedStmt.setInt(13, rolledBack);
            preparedStmt.setLong(14, CoreProtect.getInstance().rowNumbers().nextRowNumber("entity_container", preparedStmt.getConnection()));
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
