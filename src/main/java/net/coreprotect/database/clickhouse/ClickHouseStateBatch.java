package net.coreprotect.database.clickhouse;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class ClickHouseStateBatch implements AutoCloseable {

    private final ClickHouseBatchIdentity identity;
    private final EnumMap<ClickHouseFamily, LinkedHashMap<Long, RollbackUpdate>> rollbackUpdates = new EnumMap<>(ClickHouseFamily.class);
    private final LinkedHashMap<Long, ClickHouseEntityState> entityStateUpdates = new LinkedHashMap<>();
    private boolean sealed;

    ClickHouseStateBatch(ClickHouseBatchIdentity identity) {
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    void addRollback(ClickHouseEventPointer pointer, int rolledBack) {
        ensureWritable();
        Objects.requireNonNull(pointer, "pointer");
        if (!identity.getDatasetId().equals(pointer.getDatasetId())) {
            throw new IllegalArgumentException("ClickHouse rollback target belongs to a different dataset");
        }
        addRollback(new RollbackUpdate(pointer, rolledBack));
    }

    void addRollback(ClickHouseFamily family, long rowId, int time, int worldId, int x, int z, int rolledBack) {
        ensureWritable();
        addRollback(new RollbackUpdate(family, rowId, time, worldId, x, z, rolledBack));
    }

    void addEntityState(ClickHouseEntityState state) {
        ensureWritable();
        Objects.requireNonNull(state, "state");
        ClickHouseEventPointer pointer = state.getPointer();
        if (!identity.getDatasetId().equals(pointer.getDatasetId())) {
            throw new IllegalArgumentException("ClickHouse entity state belongs to a different dataset");
        }
        if (pointer.getFamily() != ClickHouseFamily.ENTITY_SPAWN) {
            throw new IllegalArgumentException("ClickHouse entity state requires an entity spawn target");
        }
        entityStateUpdates.put(pointer.getRowId(), state);
    }

    void appendTo(ClickHouseRowBinaryBuffer rows, int firstOrdinal) throws SQLException {
        if (sealed) {
            throw new IllegalStateException("ClickHouse state batch is already appended");
        }
        ensureWritable();
        Objects.requireNonNull(rows, "rows");
        int ordinal = firstOrdinal;
        for (Map<Long, RollbackUpdate> familyUpdates : rollbackUpdates.values()) {
            for (RollbackUpdate update : familyUpdates.values()) {
                beginSparseRow(rows, update, ordinal++);
                rows.set("rolled_back", update.rolledBack);
                rows.commitRow("rollback state update");
            }
        }
        for (ClickHouseEntityState state : entityStateUpdates.values()) {
            ClickHouseEventPointer pointer = state.getPointer();
            beginSparseRow(rows, pointer, ordinal++);
            Long blockRowId = state.getBlockRowId();
            Integer killRowId = state.getKillRowId();
            rows.set("block_rowid", blockRowId);
            rows.set("kill_rowid", killRowId == null ? null : killRowId.longValue());
            rows.set("block_rowid_present", blockRowId == null ? 0 : 1);
            rows.set("kill_rowid_present", killRowId == null ? 0 : 1);
            rows.set("uuid", state.getUuid().toString());
            rows.set("current_wid", state.getWorldId());
            rows.set("current_x", state.getX());
            rows.set("current_y", state.getY());
            rows.set("current_z", state.getZ());
            rows.set("yaw", state.getYaw());
            rows.set("pitch", state.getPitch());
            byte[] data = state.getData();
            rows.set("entity_data", data);
            rows.set("entity_data_present", data == null ? 0 : 1);
            rows.set("removed", state.isRemoved() ? 1 : 0);
            rows.commitRow("entity state update");
        }
        sealed = true;
    }

    int getRollbackCount() {
        int count = 0;
        for (Map<Long, RollbackUpdate> updates : rollbackUpdates.values()) {
            count += updates.size();
        }
        return count;
    }

    int getEntityStateCount() {
        return entityStateUpdates.size();
    }

    int getLocalOverlapCount(int eventCount) {
        int count = 0;
        for (Map<Long, RollbackUpdate> updates : rollbackUpdates.values()) {
            for (RollbackUpdate update : updates.values()) {
                if (isLocal(update, eventCount)) {
                    count++;
                }
            }
        }
        for (ClickHouseEntityState state : entityStateUpdates.values()) {
            if (isLocal(state.getPointer(), eventCount)) {
                count++;
            }
        }
        return count;
    }

    Checkpoint checkpoint() {
        ensureWritable();
        return new Checkpoint(rollbackUpdates, entityStateUpdates);
    }

    void restore(Checkpoint checkpoint) {
        ensureWritable();
        Objects.requireNonNull(checkpoint, "checkpoint");
        rollbackUpdates.clear();
        checkpoint.rollbackUpdates.forEach((family, updates) -> rollbackUpdates.put(family, new LinkedHashMap<>(updates)));
        entityStateUpdates.clear();
        entityStateUpdates.putAll(checkpoint.entityStateUpdates);
    }

    @Override
    public void close() {
        sealed = true;
        rollbackUpdates.clear();
        entityStateUpdates.clear();
    }

    private void addRollback(RollbackUpdate update) {
        if (!isRollbackFamily(update.family)) {
            throw new IllegalArgumentException("ClickHouse rollback state is unsupported for " + update.family.getTableName());
        }
        if (update.rowId < 1) {
            throw new IllegalArgumentException("ClickHouse rollback row IDs must be positive");
        }
        if (update.rolledBack < 0 || update.rolledBack > 3) {
            throw new IllegalArgumentException("CoreProtect rolled-back state must be between 0 and 3");
        }
        rollbackUpdates.computeIfAbsent(update.family, ignored -> new LinkedHashMap<>()).put(update.rowId, update);
    }

    private void beginSparseRow(ClickHouseRowBinaryBuffer rows, RollbackUpdate update, int ordinal) {
        beginSparseRow(rows, update.family, update.rowId, update.time, update.worldId, update.x, update.z, ordinal);
    }

    private void beginSparseRow(ClickHouseRowBinaryBuffer rows, ClickHouseEventPointer pointer, int ordinal) {
        beginSparseRow(rows, pointer.getFamily(), pointer.getRowId(), pointer.getTime(), pointer.getWorldId(), pointer.getX(), pointer.getZ(), ordinal);
    }

    private void beginSparseRow(ClickHouseRowBinaryBuffer rows, ClickHouseFamily family, long rowId, int time, int worldId, int x, int z, int ordinal) {
        rows.beginRow();
        rows.set("dataset_id", identity.getDatasetId());
        rows.set("producer_id", identity.getProducerId());
        rows.set("producer_sequence", identity.getProducerSequence());
        rows.set("batch_id", identity.getBatchId());
        rows.set("batch_ordinal", ordinal);
        rows.set("family", family.getTableName());
        rows.set("rowid", rowId);
        rows.set("time", time);
        rows.set("wid", worldId);
        rows.set("x", x);
        rows.set("z", z);
    }

    private void ensureWritable() {
        if (sealed) {
            throw new IllegalStateException("ClickHouse state batch is sealed");
        }
    }

    private boolean isLocal(RollbackUpdate update, int eventCount) {
        return update.pointer != null && isLocal(update.pointer, eventCount);
    }

    private boolean isLocal(ClickHouseEventPointer pointer, int eventCount) {
        return identity.getProducerId().equals(pointer.getProducerId())
                && identity.getProducerSequence() == pointer.getProducerSequence()
                && pointer.getBatchOrdinal() < eventCount;
    }

    private static boolean isRollbackFamily(ClickHouseFamily family) {
        return family == ClickHouseFamily.BLOCK
                || family == ClickHouseFamily.CONTAINER
                || family == ClickHouseFamily.ENTITY_CONTAINER
                || family == ClickHouseFamily.ITEM;
    }

    static final class Checkpoint {

        private final EnumMap<ClickHouseFamily, LinkedHashMap<Long, RollbackUpdate>> rollbackUpdates = new EnumMap<>(ClickHouseFamily.class);
        private final LinkedHashMap<Long, ClickHouseEntityState> entityStateUpdates;

        private Checkpoint(EnumMap<ClickHouseFamily, LinkedHashMap<Long, RollbackUpdate>> rollbackUpdates, LinkedHashMap<Long, ClickHouseEntityState> entityStateUpdates) {
            rollbackUpdates.forEach((family, updates) -> this.rollbackUpdates.put(family, new LinkedHashMap<>(updates)));
            this.entityStateUpdates = new LinkedHashMap<>(entityStateUpdates);
        }
    }

    private static final class RollbackUpdate {

        private final ClickHouseFamily family;
        private final long rowId;
        private final int time;
        private final int worldId;
        private final int x;
        private final int z;
        private final ClickHouseEventPointer pointer;
        private final int rolledBack;

        private RollbackUpdate(ClickHouseEventPointer pointer, int rolledBack) {
            this.family = pointer.getFamily();
            this.rowId = pointer.getRowId();
            this.time = pointer.getTime();
            this.worldId = pointer.getWorldId();
            this.x = pointer.getX();
            this.z = pointer.getZ();
            this.pointer = pointer;
            this.rolledBack = rolledBack;
        }

        private RollbackUpdate(ClickHouseFamily family, long rowId, int time, int worldId, int x, int z, int rolledBack) {
            this.family = Objects.requireNonNull(family, "family");
            this.rowId = rowId;
            this.time = time;
            this.worldId = worldId;
            this.x = x;
            this.z = z;
            this.pointer = null;
            this.rolledBack = rolledBack;
        }
    }

}
