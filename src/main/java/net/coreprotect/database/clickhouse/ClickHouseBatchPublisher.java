package net.coreprotect.database.clickhouse;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLNonTransientException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.clickhouse.client.api.ServerException;

final class ClickHouseBatchPublisher {

    private static final long MAX_INSERT_ROWS = 1_000_000;
    static final int MAX_PUBLICATION_ATTEMPTS = 3;
    static final int MAX_RECONCILIATION_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 100L;
    private static final int MEMORY_LIMIT_EXCEEDED = 241;
    private static final int NOT_ENOUGH_SPACE = 243;
    private static final int TOO_MANY_PARTS = 252;
    private static final int ACCESS_DENIED = 497;

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
            throw new SQLException("ClickHouse batches cannot exceed " + MAX_INSERT_ROWS + " data rows");
        }
        for (Map.Entry<Integer, Integer> partition : receipt.getPartitionRowCounts().entrySet()) {
            if (partition.getValue() >= MAX_INSERT_ROWS) {
                throw new SQLException("ClickHouse batch partition " + partition.getKey() + " cannot exceed " + (MAX_INSERT_ROWS - 1) + " data rows");
            }
            if (!batch.isPartitionPublished(partition.getKey())) {
                publishPartition(batch, receipt, partition.getKey(), partition.getValue());
            }
        }
        batch.markPublished();
        return receipt;
    }

    private void publishPartition(ClickHouseWriteBatch batch, ClickHouseBatchReceipt receipt, int partitionId, int expectedRowCount) throws SQLException {
        SQLException publicationFailure = null;
        int attempt = 1;
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw interrupted("publishing a ClickHouse batch", publicationFailure);
            }
            try {
                writerRegistration.verifyOwned();
                InputStream rows = batch.openRows(partitionId);
                batch.markPublicationAttempted();
                nativeClient.insert(eventTable, ClickHouseSchema.EVENT_COLUMNS, rows, batch.getIdentity(), "records_" + partitionId);
                batch.markPartitionPublished(partitionId);
                return;
            }
            catch (ClickHouseWriterRegistration.OwnershipException exception) {
                throw exception;
            }
            catch (SQLException exception) {
                publicationFailure = exception;
            }

            ReceiptStatus status;
            try {
                status = readStatus(batch.getIdentity(), partitionId);
            }
            catch (SQLException reconciliationFailure) {
                reconciliationFailure.addSuppressed(publicationFailure);
                throw reconciliationFailure;
            }
            if (status.matches(receipt, expectedRowCount)) {
                batch.markPartitionPublished(partitionId);
                return;
            }
            if (!status.isEmpty()) {
                SQLException conflict = conflict(receipt, partitionId);
                conflict.addSuppressed(publicationFailure);
                throw conflict;
            }
            if (attempt >= MAX_PUBLICATION_ATTEMPTS || isPermanent(publicationFailure)) {
                throw new SQLException("ClickHouse batch partition " + partitionId + " remained incomplete after " + attempt + " publication attempts", publicationFailure);
            }
            pauseBeforeRetry(attempt++, "republishing a ClickHouse batch partition");
        }
    }

    private ReceiptStatus readStatus(ClickHouseBatchIdentity identity, int partitionId) throws SQLException {
        SQLException failure = null;
        int attempt = 1;
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw interrupted("reconciling a ClickHouse batch", failure);
            }
            try {
                return readReceiptStatus(identity, partitionId);
            }
            catch (SQLException exception) {
                failure = exception;
            }
            if (attempt >= MAX_RECONCILIATION_ATTEMPTS || isPermanent(failure)) {
                throw new SQLException("ClickHouse batch status remained unavailable after " + attempt + " attempts", failure);
            }
            pauseBeforeRetry(attempt++, "reconciling a ClickHouse batch");
        }
    }

    private ReceiptStatus readReceiptStatus(ClickHouseBatchIdentity identity, int partitionId) throws SQLException {
        String sql = "SELECT toString(batch_id),ifNull(amount,-1)"
                + " FROM " + eventTable
                + " WHERE family=? AND batch_sequence=? AND rowid=? AND wid=? AND x=0 AND z=0 AND lookup_kind=0"
                + " GROUP BY batch_id,amount LIMIT 2";
        try (Connection connection = jdbc.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ClickHouseSchema.BATCH_RECEIPT_FAMILY);
            statement.setLong(2, identity.getBatchSequence());
            statement.setLong(3, identity.getBatchSequence());
            statement.setInt(4, partitionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return ReceiptStatus.EMPTY;
                }
                ReceiptStatus status = new ReceiptStatus(resultSet.getString(1), resultSet.getInt(2), false);
                return resultSet.next() ? ReceiptStatus.CONFLICTING : status;
            }
        }
    }

    private static SQLException conflict(ClickHouseBatchReceipt receipt, int partitionId) {
        return new SQLException("ClickHouse batch receipt is conflicting for batch sequence " + receipt.getBatchSequence() + " partition " + partitionId);
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
            Thread.sleep(RETRY_DELAY_MILLIS * attempt);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while " + operation, exception);
        }
    }

    static boolean isPermanent(SQLException failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLNonTransientException && !(cause instanceof SQLNonTransientConnectionException)) {
                return true;
            }
            if (cause instanceof SQLException) {
                String state = ((SQLException) cause).getSQLState();
                if (state != null && (state.startsWith("22") || state.startsWith("23") || state.startsWith("28") || state.startsWith("42"))) {
                    return true;
                }
            }
            int code = cause instanceof ServerException ? ((ServerException) cause).getCode() : cause instanceof SQLException ? ((SQLException) cause).getErrorCode() : 0;
            if (code == MEMORY_LIMIT_EXCEEDED || code == NOT_ENOUGH_SPACE || code == TOO_MANY_PARTS || code == ACCESS_DENIED) {
                return true;
            }
        }
        return false;
    }

    private static final class ReceiptStatus {

        private static final ReceiptStatus EMPTY = new ReceiptStatus(null, -1, false);
        private static final ReceiptStatus CONFLICTING = new ReceiptStatus(null, -1, true);

        private final String batchId;
        private final int rowCount;
        private final boolean conflicting;

        private ReceiptStatus(String batchId, int rowCount, boolean conflicting) {
            this.batchId = batchId;
            this.rowCount = rowCount;
            this.conflicting = conflicting;
        }

        private boolean isEmpty() {
            return this == EMPTY;
        }

        private boolean matches(ClickHouseBatchReceipt receipt, int expectedRowCount) {
            return !conflicting
                    && rowCount == expectedRowCount
                    && receipt.getBatchId().toString().equals(batchId);
        }
    }

}
