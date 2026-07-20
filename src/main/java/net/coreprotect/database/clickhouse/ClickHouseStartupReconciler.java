package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ClickHouseStartupReconciler {

    private ClickHouseStartupReconciler() {
        throw new IllegalStateException("Utility class");
    }

    public static ClickHouseHighWaterMarks readRemote(Connection connection, String database, String prefix, UUID datasetId, UUID producerId) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(producerId, "producerId");
        String validatedPrefix = prefix == null || prefix.isEmpty() ? "" : ClickHouseIdentifiers.requireIdentifier(prefix, "ClickHouse table prefix");
        String eventTable = table(database, validatedPrefix, "event_data");
        String retentionHighWater = table(database, validatedPrefix, "retention_high_water");
        rejectDifferentDataset(connection, eventTable, datasetId);
        rejectDifferentProducer(connection, eventTable, datasetId, producerId);
        return new ClickHouseHighWaterMarks(readProducerSequence(connection, eventTable, retentionHighWater, datasetId, producerId), readRawRowIds(connection, eventTable, retentionHighWater, datasetId));
    }

    private static void rejectDifferentDataset(Connection connection, String table, UUID datasetId) throws SQLException {
        String sql = "SELECT dataset_id FROM " + table + " WHERE dataset_id != ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, datasetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    throw new SQLException("ClickHouse table namespace is already assigned to dataset " + resultSet.getString(1));
                }
            }
        }
    }

    private static void rejectDifferentProducer(Connection connection, String table, UUID datasetId, UUID producerId) throws SQLException {
        String sql = "SELECT producer_id FROM " + table + " WHERE dataset_id=? AND producer_id != ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, datasetId);
            statement.setObject(2, producerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    throw new SQLException("ClickHouse dataset " + datasetId + " is already written by producer " + resultSet.getString(1));
                }
            }
        }
    }

    private static long readProducerSequence(Connection connection, String eventTable, String retentionHighWater, UUID datasetId, UUID producerId) throws SQLException {
        String sql = "SELECT max(producer_sequence) FROM ("
                + "SELECT producer_sequence FROM " + eventTable + " WHERE dataset_id=? AND producer_id=? UNION ALL "
                + "SELECT producer_sequence FROM " + retentionHighWater + " WHERE dataset_id=? AND producer_id=?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, datasetId);
            statement.setObject(2, producerId);
            statement.setObject(3, datasetId);
            statement.setObject(4, producerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? requireNonNegative(resultSet.getLong(1), "producer sequence") : 0;
            }
        }
    }

    private static Map<ClickHouseFamily, Long> readRawRowIds(Connection connection, String eventTable, String retentionHighWater, UUID datasetId) throws SQLException {
        String sql = "SELECT family,max(rowid) FROM ("
                + "SELECT family,rowid FROM " + eventTable + " WHERE dataset_id=? UNION ALL "
                + "SELECT family,rowid FROM " + retentionHighWater + " WHERE dataset_id=?) GROUP BY family";
        Map<ClickHouseFamily, Long> rowIds = new EnumMap<>(ClickHouseFamily.class);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, datasetId);
            statement.setObject(2, datasetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    putMax(rowIds, resultSet.getString(1), resultSet.getLong(2));
                }
            }
        }
        return rowIds;
    }

    private static void putMax(Map<ClickHouseFamily, Long> rowIds, String familyName, long rowId) throws SQLException {
        ClickHouseFamily family;
        try {
            family = ClickHouseFamily.fromTableName(familyName);
        }
        catch (IllegalArgumentException exception) {
            throw new SQLException("Unknown ClickHouse event family in identity storage", exception);
        }
        rowIds.merge(family, requireNonNegative(rowId, "compatibility row ID"), Math::max);
    }

    private static long requireNonNegative(long value, String name) throws SQLException {
        if (value < 0) {
            throw new SQLException("ClickHouse " + name + " exceeds the supported signed 64-bit range");
        }
        return value;
    }

    private static String table(String database, String prefix, String suffix) {
        return ClickHouseIdentifiers.qualified(database, prefix + suffix);
    }

}
