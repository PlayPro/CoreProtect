package net.coreprotect.database.statement;

import java.sql.PreparedStatement;

import net.coreprotect.utility.ItemUtils;

public class ItemStatement {

    private ItemStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, int time, int id, int wid, int x, int y, int z, int type, Object data, int amount, int action) {
        try {
            byte[] byteData = ItemUtils.convertByteData(data);
            preparedStmt.setInt(1, time);
            preparedStmt.setInt(2, id);
            preparedStmt.setInt(3, wid);
            preparedStmt.setInt(4, x);
            preparedStmt.setInt(5, y);
            preparedStmt.setInt(6, z);
            preparedStmt.setInt(7, type);
            preparedStmt.setObject(8, byteData);
            preparedStmt.setInt(9, amount);
            preparedStmt.setInt(10, action);
            preparedStmt.setInt(11, 0); // rolled_back
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
