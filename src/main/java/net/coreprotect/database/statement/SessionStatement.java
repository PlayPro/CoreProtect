package net.coreprotect.database.statement;

import java.sql.PreparedStatement;

public class SessionStatement {

    public static void insert(PreparedStatement preparedStmt, int batchCount, int time, int user, int wid, int x, int y, int z, int action) {
        try {
            preparedStmt.setInt(1, time);
            preparedStmt.setInt(2, user);
            preparedStmt.setInt(3, wid);
            preparedStmt.setInt(4, x);
            preparedStmt.setInt(5, y);
            preparedStmt.setInt(6, z);
            preparedStmt.setInt(7, action);
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
