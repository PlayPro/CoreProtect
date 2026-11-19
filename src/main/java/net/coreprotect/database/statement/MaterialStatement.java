package net.coreprotect.database.statement;

import java.sql.ResultSet;
import java.sql.Statement;

import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;

public class MaterialStatement {

    private MaterialStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(ConsumerWriteBatch batch, ConsumerWriteBatch.ReferenceKind kind, int batchCount, int id, String name) {
        try {
            batch.addReference(kind, batchCount, id, name);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

    public static boolean hasMaterial(Statement statement, String query) {
        try (ResultSet resultSet = statement.executeQuery(query)) {
            return resultSet.next();
        }
        catch (Exception e) {
            Database.reportDatabaseFailure(e);
            return false;
        }
    }
}
