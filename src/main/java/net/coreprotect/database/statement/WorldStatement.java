package net.coreprotect.database.statement;

import net.coreprotect.CoreProtect;

import java.sql.PreparedStatement;

public class WorldStatement {

    private WorldStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, int id, String world) {
        try {
            preparedStmt.setInt(1, id);
            preparedStmt.setString(2, world);
            preparedStmt.setLong(3, CoreProtect.getInstance().rowNumbers().nextRowNumber("world", preparedStmt.getConnection()));
            preparedStmt.addBatch();

            if (batchCount > 0 && batchCount % 1000 == 0) {
                preparedStmt.executeBatch();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
