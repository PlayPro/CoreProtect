package net.coreprotect.database.statement;

import java.sql.PreparedStatement;

import net.coreprotect.utility.ItemUtils;

public class ContainerStatement {

    private ContainerStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, int time, int id, int wid, int x, int y, int z, int type, int data, int amount, Object metadata, int action, int rolledBack) {
        try {
            byte[] byteData = ItemUtils.convertByteData(metadata);
            preparedStmt.setInt(1, time);
            preparedStmt.setInt(2, id);
            preparedStmt.setInt(3, wid);
            preparedStmt.setInt(4, x);
            preparedStmt.setInt(5, y);
            preparedStmt.setInt(6, z);
            preparedStmt.setInt(7, type);
            preparedStmt.setInt(8, data);
            preparedStmt.setInt(9, amount);
            preparedStmt.setObject(10, byteData);
            preparedStmt.setInt(11, action);
            preparedStmt.setInt(12, rolledBack);
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
