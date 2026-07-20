package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import net.coreprotect.config.ConfigHandler;

final class ClickHouseBatchPublisher {

    private static final long MAX_INSERT_ROWS = 1_000_000;
    static final int MAX_PUBLICATION_ATTEMPTS = 3;
    static final int MAX_RECONCILIATION_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 100L;
    private static final long MAX_RETRY_DELAY_MILLIS = 30_000L;

    private final ClickHouseJdbc jdbc;
    private final ClickHouseNativeClient nativeClient;
    private final ClickHouseWriterRegistration writerRegistration;
    private final String eventTable;

    ClickHouseBatchPublisher(ClickHouseJdbc jdbc, ClickHouseNativeClient nativeClient, ClickHouseWriterRegistration writerRegistration, String database, String prefix) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.nativeClient = Objects.requireNonNull(nativeClient, "nativeClient");
        this.writerRegistration = Objects.requireNonNull(writerRegistration, "writerRegistration");
        String validatedPrefix = prefix == null || prefix.isEmpty() ? "" : ClickHouseIdentifiers.requireIdentifier(prefix, "ClickHouse table prefix");
        eventTable = ClickHouseIdentifiers.qualified(database, validatedPrefix + "event_data");
    }

    ClickHouseBatchReceipt publish(ClickHouseWriteBatch batch) throws SQLException {
        Objects.requireNonNull(batch, "batch");
        ClickHouseBatchReceipt receipt = batch.seal();
        if (batch.isPublished()) {
            return receipt;
        }
        if (receipt.getRowCount() == 0) {
            batch.markPublished();
            return receipt;
        }
        if (receipt.getRowCount() > MAX_INSERT_ROWS) {
            throw new SQLException("ClickHouse batches cannot exceed " + MAX_INSERT_ROWS + " physical rows");
        }

        SQLException publicationFailure = null;
        int attempt = 1;
        while (true) {
            try {
                writerRegistration.verifyOwned();
                nativeClient.insert(eventTable, ClickHouseSchema.EVENT_COLUMNS, batch.openRows(), batch.getIdentity(), "records");
                batch.markPublished();
                return receipt;
            }
            catch (ClickHouseWriterRegistration.OwnershipException exception) {
                throw exception;
            }
            catch (SQLException exception) {
                publicationFailure = exception;
            }

            RawStatus status;
            try {
                status = readStatus(batch.getIdentity());
            }
            catch (SQLException reconciliationFailure) {
                reconciliationFailure.addSuppressed(publicationFailure);
                throw reconciliationFailure;
            }
            if (status.matches(receipt)) {
                batch.markPublished();
                return receipt;
            }
            if (!status.isEmpty() && !status.isRetryablePartial(receipt)) {
                SQLException conflict = conflict(receipt);
                conflict.addSuppressed(publicationFailure);
                throw conflict;
            }
            if (attempt >= MAX_PUBLICATION_ATTEMPTS && !shouldContinueRecovery()) {
                throw new SQLException("ClickHouse batch remained incomplete after " + attempt + " publication attempts", publicationFailure);
            }
            pauseBeforeRetry(attempt++, "republishing a ClickHouse batch");
        }
    }

    private RawStatus readStatus(ClickHouseBatchIdentity identity) throws SQLException {
        SQLException failure = null;
        for (int attempt = 1; attempt <= MAX_RECONCILIATION_ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw interrupted("reconciling a ClickHouse batch", failure);
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
            if (attempt < MAX_RECONCILIATION_ATTEMPTS) {
                pauseBeforeRetry(attempt, "reconciling a ClickHouse batch");
            }
        }
        throw new SQLException("ClickHouse batch status remained unavailable after " + MAX_RECONCILIATION_ATTEMPTS + " attempts", failure);
    }

    private RawStatus readRawStatus(ClickHouseBatchIdentity identity) throws SQLException {
        String sql = "SELECT count(),uniqExact(tuple(family,rowid)),uniqExact(batch_id),any(toString(batch_id)),uniqExact(batch_ordinal),max(batch_ordinal)"
                + " FROM " + eventTable
                + " WHERE dataset_id=? AND producer_id=? AND producer_sequence=?";
        try (Connection connection = jdbc.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, identity.getDatasetId());
            statement.setObject(2, identity.getProducerId());
            statement.setLong(3, identity.getProducerSequence());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("ClickHouse did not return batch reconciliation data");
                }
                return new RawStatus(resultSet.getLong(1), resultSet.getLong(2), resultSet.getLong(3), resultSet.getString(4), resultSet.getLong(5), resultSet.getLong(6));
            }
        }
    }

    private static SQLException conflict(ClickHouseBatchReceipt receipt) {
        return new SQLException("ClickHouse batch is partial or conflicting for producer sequence " + receipt.getProducerSequence());
    }

    static SQLException interrupted(String operation, SQLException failure) {
        InterruptedException interruption = new InterruptedException("Interrupted while " + operation);
        SQLException exception = new SQLException(interruption.getMessage(), interruption);
        if (failure != null) {
            exception.addSuppressed(failure);
        }
        return exception;
    }

    static void pauseBeforeRetry(int attempt, String operation) throws SQLException {
        try {
            int shift = Math.min(Math.max(0, attempt - 1), 8);
            Thread.sleep(Math.min(RETRY_DELAY_MILLIS << shift, MAX_RETRY_DELAY_MILLIS));
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while " + operation, exception);
        }
    }

    static boolean shouldContinueRecovery() {
        return ConfigHandler.serverRunning || ConfigHandler.shutdownDrainRunning;
    }

    private static final class RawStatus {

        private final long count;
        private final long logicalRows;
        private final long uniqueBatchIds;
        private final String batchId;
        private final long uniqueOrdinals;
        private final long maximumOrdinal;

        private RawStatus(long count, long logicalRows, long uniqueBatchIds, String batchId, long uniqueOrdinals, long maximumOrdinal) {
            this.count = count;
            this.logicalRows = logicalRows;
            this.uniqueBatchIds = uniqueBatchIds;
            this.batchId = batchId;
            this.uniqueOrdinals = uniqueOrdinals;
            this.maximumOrdinal = maximumOrdinal;
        }

        private boolean isEmpty() {
            return count == 0;
        }

        private boolean matches(ClickHouseBatchReceipt receipt) {
            long expectedLogicalRows = receipt.getLogicalRowCount();
            return count >= expectedLogicalRows
                    && count <= receipt.getRowCount()
                    && logicalRows == expectedLogicalRows
                    && uniqueBatchIds == 1
                    && uniqueOrdinals == count
                    && maximumOrdinal == receipt.getRowCount() - 1L
                    && receipt.getBatchId().toString().equals(batchId);
        }

        private boolean isRetryablePartial(ClickHouseBatchReceipt receipt) {
            return count > 0
                    && count < receipt.getRowCount()
                    && logicalRows > 0
                    && logicalRows <= receipt.getLogicalRowCount()
                    && uniqueBatchIds == 1
                    && uniqueOrdinals == count
                    && maximumOrdinal < receipt.getRowCount()
                    && receipt.getBatchId().toString().equals(batchId);
        }
    }

}
