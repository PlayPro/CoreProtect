package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

final class ClickHouseHighWaterPublisher {

    private final ClickHouseJdbc jdbc;
    private final ClickHouseNativeClient nativeClient;
    private final ClickHouseWriterRegistration writerRegistration;
    private final String table;

    ClickHouseHighWaterPublisher(ClickHouseJdbc jdbc, ClickHouseNativeClient nativeClient, ClickHouseWriterRegistration writerRegistration, String database, String prefix) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.nativeClient = Objects.requireNonNull(nativeClient, "nativeClient");
        this.writerRegistration = Objects.requireNonNull(writerRegistration, "writerRegistration");
        String validatedPrefix = prefix == null || prefix.isEmpty() ? "" : ClickHouseIdentifiers.requireIdentifier(prefix, "ClickHouse table prefix");
        table = ClickHouseIdentifiers.qualified(database, validatedPrefix + "retention_high_water");
    }

    void publish(ClickHouseBatchIdentity identity, Map<ClickHouseFamily, Long> marks) throws SQLException {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(marks, "marks");
        if (marks.isEmpty()) {
            return;
        }

        try (ClickHouseRowBinaryBuffer rows = encode(identity, marks)) {
            SQLException publicationFailure = null;
            int attempt = 1;
            while (true) {
                try {
                    writerRegistration.verifyOwned();
                    nativeClient.insert(table, ClickHouseSchema.RETENTION_HIGH_WATER_COLUMNS, rows.openStream(), identity, "high_water");
                    return;
                }
                catch (ClickHouseWriterRegistration.OwnershipException exception) {
                    throw exception;
                }
                catch (SQLException exception) {
                    publicationFailure = exception;
                }

                Status status;
                try {
                    status = readStatus(identity);
                }
                catch (SQLException reconciliationFailure) {
                    reconciliationFailure.addSuppressed(publicationFailure);
                    throw reconciliationFailure;
                }
                if (status.matches(marks)) {
                    return;
                }
                if (!status.isRetryableSubsetOf(marks)) {
                    SQLException conflict = new SQLException("ClickHouse high-water batch is partial or conflicting for producer sequence " + identity.getProducerSequence());
                    conflict.addSuppressed(publicationFailure);
                    throw conflict;
                }
                if (attempt >= ClickHouseBatchPublisher.MAX_PUBLICATION_ATTEMPTS && !ClickHouseBatchPublisher.shouldContinueRecovery()) {
                    throw new SQLException("ClickHouse high-water batch remained incomplete after " + attempt + " publication attempts", publicationFailure);
                }
                ClickHouseBatchPublisher.pauseBeforeRetry(attempt++, "republishing ClickHouse high-water marks");
            }
        }
    }

    private ClickHouseRowBinaryBuffer encode(ClickHouseBatchIdentity identity, Map<ClickHouseFamily, Long> marks) throws SQLException {
        ClickHouseRowBinaryBuffer rows = new ClickHouseRowBinaryBuffer(ClickHouseSchema.RETENTION_HIGH_WATER_COLUMNS, ClickHouseSchema.RETENTION_HIGH_WATER_COLUMN_TYPES);
        try {
            LocalDateTime recordedAt = LocalDateTime.now(ZoneOffset.UTC);
            for (Map.Entry<ClickHouseFamily, Long> mark : marks.entrySet()) {
                rows.beginRow();
                rows.set("dataset_id", identity.getDatasetId());
                rows.set("producer_id", identity.getProducerId());
                rows.set("producer_sequence", identity.getProducerSequence());
                rows.set("family", mark.getKey().getTableName());
                rows.set("rowid", mark.getValue());
                rows.set("recorded_at", recordedAt);
                rows.commitRow("retention high-water row");
            }
            rows.seal();
            return rows;
        }
        catch (SQLException | RuntimeException exception) {
            rows.close();
            throw exception;
        }
    }

    private Status readStatus(ClickHouseBatchIdentity identity) throws SQLException {
        SQLException failure = null;
        for (int attempt = 1; attempt <= ClickHouseBatchPublisher.MAX_RECONCILIATION_ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw ClickHouseBatchPublisher.interrupted("reconciling ClickHouse high-water marks", failure);
            }
            try {
                return readRawStatus(identity);
            }
            catch (SQLException exception) {
                if (failure != null && failure != exception) {
                    exception.addSuppressed(failure);
                }
                failure = exception;
            }
            if (attempt < ClickHouseBatchPublisher.MAX_RECONCILIATION_ATTEMPTS) {
                ClickHouseBatchPublisher.pauseBeforeRetry(attempt, "reconciling ClickHouse high-water marks");
            }
        }
        throw new SQLException("ClickHouse high-water status remained unavailable after " + ClickHouseBatchPublisher.MAX_RECONCILIATION_ATTEMPTS + " attempts", failure);
    }

    private Status readRawStatus(ClickHouseBatchIdentity identity) throws SQLException {
        String sql = "SELECT family,rowid,count() FROM " + table
                + " WHERE dataset_id=? AND producer_id=? AND producer_sequence=? GROUP BY family,rowid";
        EnumMap<ClickHouseFamily, Long> values = new EnumMap<>(ClickHouseFamily.class);
        boolean conflicting = false;
        try (Connection connection = jdbc.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, identity.getDatasetId());
            statement.setObject(2, identity.getProducerId());
            statement.setLong(3, identity.getProducerSequence());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ClickHouseFamily family;
                    try {
                        family = ClickHouseFamily.fromTableName(resultSet.getString(1));
                    }
                    catch (IllegalArgumentException exception) {
                        throw new SQLException("Unknown ClickHouse family in high-water reconciliation", exception);
                    }
                    if (resultSet.getLong(3) != 1L || values.put(family, resultSet.getLong(2)) != null) {
                        conflicting = true;
                    }
                }
            }
        }
        return new Status(values, conflicting);
    }

    private static final class Status {

        private final Map<ClickHouseFamily, Long> values;
        private final boolean conflicting;

        private Status(Map<ClickHouseFamily, Long> values, boolean conflicting) {
            this.values = values;
            this.conflicting = conflicting;
        }

        private boolean matches(Map<ClickHouseFamily, Long> expected) {
            return !conflicting && values.equals(expected);
        }

        private boolean isRetryableSubsetOf(Map<ClickHouseFamily, Long> expected) {
            if (conflicting || values.size() >= expected.size()) {
                return values.isEmpty() && !conflicting;
            }
            for (Map.Entry<ClickHouseFamily, Long> value : values.entrySet()) {
                if (!value.getValue().equals(expected.get(value.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }
}
