package net.coreprotect.database.statement;

import net.coreprotect.CoreProtect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class MaterialStatement {

    private MaterialStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, int id, String name, String tableName) {
        try {
            preparedStmt.setInt(1, id);
            preparedStmt.setString(2, name);
            preparedStmt.setLong(3, CoreProtect.getInstance().rowNumbers().nextRowNumber(tableName, preparedStmt.getConnection()));
            preparedStmt.addBatch();

            if (batchCount > 0 && batchCount % 1000 == 0) {
                preparedStmt.executeBatch();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean hasMaterial(Statement statement, String query) {
        boolean result = false;

        try {
            ResultSet resultSet = statement.executeQuery(query);
            if (resultSet.next()) {
                result = true;
            }
            resultSet.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
