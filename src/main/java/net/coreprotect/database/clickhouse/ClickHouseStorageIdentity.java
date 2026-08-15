package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

final class ClickHouseStorageIdentity {

    private final UUID datasetId;

    private ClickHouseStorageIdentity(UUID datasetId) {
        this.datasetId = datasetId;
    }

    static ClickHouseStorageIdentity loadOrCreate(Connection connection, String database, String prefix) throws SQLException {
        String tableName = prefix + "storage_metadata";
        String table = ClickHouseIdentifiers.qualified(database, tableName);
        ClickHouseStorageIdentity identity = read(connection, table);
        if (identity == null) {
            UUID datasetId = readTableUuid(connection, database, tableName);
            insert(connection, table, datasetId);
            identity = read(connection, table);
        }
        if (identity == null) {
            throw new SQLException("ClickHouse storage identity is not visible after creation");
        }
        return identity;
    }

    static ClickHouseStorageIdentity load(Connection connection, String database, String prefix) throws SQLException {
        return read(connection, ClickHouseIdentifiers.qualified(database, prefix + "storage_metadata"));
    }

    UUID getDatasetId() {
        return datasetId;
    }

    private static ClickHouseStorageIdentity read(Connection connection, String table) throws SQLException {
        String sql = "SELECT dataset_id,schema_version FROM " + table
                + " GROUP BY dataset_id,schema_version LIMIT 2";
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return null;
            }
            UUID datasetId = UUID.fromString(resultSet.getString(1));
            int schemaVersion = resultSet.getInt(2);
            if (resultSet.next()) {
                throw new SQLException("ClickHouse storage metadata contains conflicting identities or schema versions");
            }
            if (schemaVersion != ClickHouseSchema.VERSION) {
                throw new SQLException("Unsupported ClickHouse schema version " + schemaVersion + " (expected " + ClickHouseSchema.VERSION + ")");
            }
            return new ClickHouseStorageIdentity(datasetId);
        }
    }

    private static UUID readTableUuid(Connection connection, String database, String tableName) throws SQLException {
        String sql = "SELECT uuid FROM system.tables WHERE database=? AND name=? LIMIT 2";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("ClickHouse storage metadata table identity is not available");
                }
                UUID tableUuid = UUID.fromString(resultSet.getString(1));
                if (resultSet.next()) {
                    throw new SQLException("ClickHouse storage metadata table identity is ambiguous");
                }
                if (tableUuid.getMostSignificantBits() == 0L && tableUuid.getLeastSignificantBits() == 0L) {
                    throw new SQLException("ClickHouse storage metadata requires an Atomic database table UUID");
                }
                return tableUuid;
            }
        }
    }

    private static void insert(Connection connection, String table, UUID datasetId) throws SQLException {
        String sql = "INSERT INTO " + table
                + " (dataset_id,schema_version,created_at) VALUES (?,?,now64(3, 'UTC'))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, datasetId);
            statement.setInt(2, ClickHouseSchema.VERSION);
            statement.execute();
        }
    }

}
