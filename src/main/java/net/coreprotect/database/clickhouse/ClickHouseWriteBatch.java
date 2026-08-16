package net.coreprotect.database.clickhouse;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ClickHouseWriteBatch implements AutoCloseable {

    private final ClickHouseBatchIdentity identity;
    private final ClickHouseEventBatch events;
    private final ClickHouseStateBatch state;
    private final Set<Integer> publishedPartitions = new HashSet<>();
    private ClickHouseBatchReceipt receipt;
    private boolean closed;
    private boolean published;

    ClickHouseWriteBatch(ClickHouseBatchIdentity identity, ClickHouseRowIdAllocator rowIdAllocator) {
        this.identity = Objects.requireNonNull(identity, "identity");
        events = new ClickHouseEventBatch(identity, rowIdAllocator);
        state = new ClickHouseStateBatch(identity);
    }

    public ClickHouseEventBatch events() {
        ensureOpen();
        return events;
    }

    public void addRollback(ClickHouseEventPointer pointer, int rolledBack) throws SQLException {
        ensureOpen();
        state.addRollback(pointer, rolledBack);
    }

    void addRollback(ClickHouseFamily family, long rowId, int time, int worldId, int x, int z, int rolledBack) throws SQLException {
        ensureOpen();
        state.addRollback(family, rowId, time, worldId, x, z, rolledBack);
    }

    public void addEntityState(ClickHouseEntityState entityState) throws SQLException {
        ensureOpen();
        state.addEntityState(entityState);
    }

    ClickHouseBatchReceipt seal() throws SQLException {
        ensureOpen();
        if (receipt != null) {
            return receipt;
        }
        int eventCount = events.size();
        int rollbackCount = state.getRollbackCount();
        int entityStateCount = state.getEntityStateCount();
        int rowCount = Math.addExact(eventCount, Math.addExact(rollbackCount, entityStateCount));
        int logicalRowCount = Math.subtractExact(Math.addExact(events.logicalSize(), Math.addExact(rollbackCount, entityStateCount)), state.getLocalOverlapCount(eventCount));
        Map<Integer, Integer> partitionRowCounts = events.seal(state);
        receipt = new ClickHouseBatchReceipt(identity.getBatchSequence(), identity.getBatchId(), rowCount, logicalRowCount, partitionRowCounts);
        return receipt;
    }

    boolean isPublished() {
        ensureOpen();
        return published;
    }

    void markPublished() {
        ensureOpen();
        if (receipt == null || !publishedPartitions.containsAll(receipt.getPartitionRowCounts().keySet())) {
            throw new IllegalStateException("ClickHouse batch has unpublished partitions");
        }
        published = true;
    }

    boolean isPartitionPublished(int partitionId) {
        ensureOpen();
        return publishedPartitions.contains(partitionId);
    }

    void markPartitionPublished(int partitionId) {
        ensureOpen();
        if (receipt == null || !receipt.getPartitionRowCounts().containsKey(partitionId)) {
            throw new IllegalArgumentException("ClickHouse batch does not contain partition " + partitionId);
        }
        publishedPartitions.add(partitionId);
    }

    ClickHouseBatchIdentity getIdentity() {
        return identity;
    }

    int size() {
        ensureOpen();
        return Math.addExact(events.size(), Math.addExact(state.getRollbackCount(), state.getEntityStateCount()));
    }

    InputStream openRows() {
        if (receipt == null) {
            throw new IllegalStateException("ClickHouse write batch is not sealed");
        }
        return events.openRows();
    }

    InputStream openRows(int partitionId) {
        if (receipt == null) {
            throw new IllegalStateException("ClickHouse write batch is not sealed");
        }
        return events.openRows(partitionId);
    }

    Checkpoint checkpoint() {
        ensureOpen();
        if (receipt != null) {
            throw new IllegalStateException("ClickHouse write batch is already sealed");
        }
        return new Checkpoint(events.checkpoint(), state.checkpoint());
    }

    void restore(Checkpoint checkpoint) {
        ensureOpen();
        Objects.requireNonNull(checkpoint, "checkpoint");
        if (receipt != null) {
            throw new IllegalStateException("ClickHouse write batch is already sealed");
        }
        events.restore(checkpoint.events);
        state.restore(checkpoint.state);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            events.close();
            state.close();
            publishedPartitions.clear();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("ClickHouse write batch is closed");
        }
    }

    static final class Checkpoint {

        private final ClickHouseEventBatch.Checkpoint events;
        private final ClickHouseStateBatch.Checkpoint state;

        private Checkpoint(ClickHouseEventBatch.Checkpoint events, ClickHouseStateBatch.Checkpoint state) {
            this.events = events;
            this.state = state;
        }
    }

}
