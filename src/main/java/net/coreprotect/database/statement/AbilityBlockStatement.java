package net.coreprotect.database.statement;

import net.coreprotect.utility.Util;

import java.sql.PreparedStatement;
import java.util.List;

public class AbilityBlockStatement {

    private AbilityBlockStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, int time, int id, int wid, int x, int y, int z, int type, int data, List<Object> meta, String blockData, int action, int rolledBack, String player, String ability) {
        try {
            byte[] bBlockData = Util.stringToByteData(blockData, type);
            byte[] byteData = null;

            if (meta != null) {
                byteData = Util.convertByteData(meta);
            }

            preparedStmt.setInt(1, time);
            preparedStmt.setInt(2, id);
            preparedStmt.setInt(3, wid);
            preparedStmt.setInt(4, x);
            preparedStmt.setInt(5, y);
            preparedStmt.setInt(6, z);
            preparedStmt.setInt(7, type);
            preparedStmt.setInt(8, data);
            preparedStmt.setObject(9, byteData);
            preparedStmt.setObject(10, bBlockData);
            preparedStmt.setInt(11, action);
            preparedStmt.setInt(12, rolledBack);
            preparedStmt.setString(13, player);
            preparedStmt.setString(14, ability);
            preparedStmt.addBatch();

//            if (batchCount > 0 && batchCount % 1000 == 0) {
                preparedStmt.executeBatch();
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
