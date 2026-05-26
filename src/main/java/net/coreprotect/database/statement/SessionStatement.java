package net.coreprotect.database.statement;

import net.coreprotect.CoreProtect;

import java.sql.PreparedStatement;

public class SessionStatement {

    private SessionStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, int time, int user, int wid, int x, int y, int z, int action) {
        try {
            preparedStmt.setInt(1, time);
            preparedStmt.setInt(2, user);
            preparedStmt.setInt(3, wid);
            preparedStmt.setInt(4, x);
            preparedStmt.setInt(5, y);
            preparedStmt.setInt(6, z);
            preparedStmt.setInt(7, action);
            preparedStmt.setLong(8, CoreProtect.getInstance().rowNumbers().nextRowNumber("session", preparedStmt.getConnection()));
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
