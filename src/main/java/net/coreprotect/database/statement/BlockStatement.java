package net.coreprotect.database.statement;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import net.coreprotect.database.Database;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.ErrorReporter;

public class BlockStatement {

    private BlockStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, int time, int id, int wid, int x, int y, int z, int type, int data, List<Object> meta, String blockData, int action, int rolledBack) {
        insertChecked(preparedStmt, batchCount, time, id, wid, x, y, z, type, data, meta, blockData, action, rolledBack);
    }

    public static boolean insertChecked(PreparedStatement preparedStmt, int batchCount, int time, int id, int wid, int x, int y, int z, int type, int data, List<Object> meta, String blockData, int action, int rolledBack) {
        try {
            setValues(preparedStmt, time, id, wid, x, y, z, type, data, meta, blockData, action, rolledBack);
            preparedStmt.addBatch();

            if (batchCount > 0 && batchCount % 1000 == 0) {
                preparedStmt.executeBatch();
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    public static long insertImmediate(PreparedStatement preparedStmt, int time, int id, int wid, int x, int y, int z, int type, int data, List<Object> meta, String blockData, int action, int rolledBack) throws Exception {
        setValues(preparedStmt, time, id, wid, x, y, z, type, data, meta, blockData, action, rolledBack);
        if (Database.hasReturningKeys()) {
            try (ResultSet resultSet = preparedStmt.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Block insert did not return a row id");
                }
                return resultSet.getLong(1);
            }
        }

        int updated = preparedStmt.executeUpdate();
        if (updated != 1) {
            throw new SQLException("Expected one row for block insert, updated " + updated);
        }
        try (ResultSet resultSet = preparedStmt.getGeneratedKeys()) {
            if (!resultSet.next()) {
                throw new SQLException("Block insert did not generate a row id");
            }
            return resultSet.getLong(1);
        }
    }

    private static void setValues(PreparedStatement preparedStmt, int time, int id, int wid, int x, int y, int z, int type, int data, List<Object> meta, String blockData, int action, int rolledBack) throws Exception {
        byte[] bBlockData = BlockUtils.stringToByteData(blockData, type);
        byte[] byteData = meta == null ? null : ItemUtils.convertByteData(meta);
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
    }
}
