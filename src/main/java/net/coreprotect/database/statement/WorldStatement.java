package net.coreprotect.database.statement;

import java.sql.PreparedStatement;
import net.coreprotect.utility.ErrorReporter;

public class WorldStatement {

    private WorldStatement() {
        throw new IllegalStateException("Database class");
    }

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
            ErrorReporter.report(e);
        }
    }
}
