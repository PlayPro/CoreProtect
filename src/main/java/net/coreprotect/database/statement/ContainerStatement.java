package net.coreprotect.database.statement;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import net.coreprotect.config.ConfigHandler;
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

    public static void insertVirtual(ConsumerWriteBatch batch, int batchCount, int time, int id, int containerId, int type, int data, int amount, Object metadata, int action, int rolledBack) throws SQLException {
        byte[] byteData = ItemUtils.convertByteData(metadata);
        try {
            batch.addVirtualContainer(batchCount, time, id, containerId, type, data, amount, byteData, action, rolledBack);
        }
        catch (SQLException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new SQLException("Unable to write virtual container transaction", exception);
        }
    }

    /**
     * Loads virtual container keys from the database rather than the identifier cache, which misses keys that another
     * server sharing the database created.
     */
    public static Map<Integer, String> loadVirtualKeys(Connection connection, Collection<Integer> containerIds) throws SQLException {
        Map<Integer, String> keys = new HashMap<>();
        if (containerIds.isEmpty()) {
            return keys;
        }

        StringJoiner ids = new StringJoiner(",");
        for (Integer containerId : containerIds) {
            ids.add(containerId.toString());
        }
        String query = "SELECT id,container FROM " + ConfigHandler.prefix + "virtual_container_map WHERE id IN(" + ids + ")";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                keys.put(resultSet.getInt(1), resultSet.getString(2));
            }
        }
        return keys;
    }
}
