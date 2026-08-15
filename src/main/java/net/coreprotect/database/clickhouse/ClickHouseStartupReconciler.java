package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ClickHouseStartupReconciler {

    private ClickHouseStartupReconciler() {
        throw new IllegalStateException("Utility class");
    }

    public static ClickHouseHighWaterMarks readRemote(Connection connection, String database, String prefix) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        String validatedPrefix = prefix == null || prefix.isEmpty() ? "" : ClickHouseIdentifiers.requireIdentifier(prefix, "ClickHouse table prefix");
        String eventTable = table(database, validatedPrefix, "event_data");
        String retentionHighWater = table(database, validatedPrefix, "retention_high_water");
        return new ClickHouseHighWaterMarks(readBatchSequence(connection, eventTable, retentionHighWater), readRawRowIds(connection, eventTable, retentionHighWater));
    }

    private static long readBatchSequence(Connection connection, String eventTable, String retentionHighWater) throws SQLException {
        String sql = "SELECT max(batch_sequence) FROM ("
                + "SELECT batch_sequence FROM " + eventTable + " UNION ALL "
                + "SELECT batch_sequence FROM " + retentionHighWater + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? requireNonNegative(resultSet.getLong(1), "batch sequence") : 0;
            }
        }
    }

    private static Map<ClickHouseFamily, Long> readRawRowIds(Connection connection, String eventTable, String retentionHighWater) throws SQLException {
        String sql = "SELECT family,max(rowid) FROM ("
                + "SELECT family,rowid FROM " + eventTable + " UNION ALL "
                + "SELECT family,rowid FROM " + retentionHighWater + ") GROUP BY family";
        Map<ClickHouseFamily, Long> rowIds = new EnumMap<>(ClickHouseFamily.class);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
