package net.coreprotect.database.statement;

import java.sql.PreparedStatement;

public class WorldStatement {

    public static void insert(PreparedStatement preparedStmt, int batchCount, int id, String world) {
        try {
            preparedStmt.setInt(1, id);
            preparedStmt.setString(2, world);
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
