package net.coreprotect.database.statement;

import java.sql.SQLException;

import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.utility.ItemUtils;

public class ContainerStatement {

    private ContainerStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(ConsumerWriteBatch batch, int batchCount, int time, int id, int wid, int x, int y, int z, int type, int data, int amount, Object metadata, int action, int rolledBack) {
        try {
            byte[] byteData = ItemUtils.convertByteData(metadata);
            batch.addContainer(batchCount, time, id, wid, x, y, z, type, data, amount, byteData, action, rolledBack);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

    public static void insertEntity(ConsumerWriteBatch batch, int batchCount, int time, int id, int entitySpawnRowId, int wid, int x, int y, int z, int type, int data, int amount, Object metadata, int action, int rolledBack) throws SQLException {
        byte[] byteData = ItemUtils.convertByteData(metadata);
        try {
            batch.addEntityContainer(batchCount, time, id, entitySpawnRowId, wid, x, y, z, type, data, amount, byteData, action, rolledBack);
        }
        catch (SQLException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new SQLException("Unable to write entity container transaction", exception);
        }
    }
}
