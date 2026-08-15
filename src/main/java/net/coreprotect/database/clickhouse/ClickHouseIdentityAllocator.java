package net.coreprotect.database.clickhouse;

import java.util.EnumMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class ClickHouseIdentityAllocator implements ClickHouseRowIdAllocator {

    private final UUID datasetId;
    private final AtomicLong batchSequence;
    private final EnumMap<ClickHouseFamily, AtomicLong> rowIds = new EnumMap<>(ClickHouseFamily.class);

    public ClickHouseIdentityAllocator(UUID datasetId, ClickHouseHighWaterMarks highWaterMarks) {
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(highWaterMarks, "highWaterMarks");
        batchSequence = new AtomicLong(highWaterMarks.getBatchSequence());
        for (ClickHouseFamily family : ClickHouseFamily.values()) {
            rowIds.put(family, new AtomicLong(highWaterMarks.getCompatibilityRowId(family)));
        }
    }

    public ClickHouseBatchIdentity nextBatchIdentity() {
        long sequence = increment(batchSequence, "batch sequence");
        return ClickHouseBatchIdentity.create(datasetId, sequence);
    }

    @Override
    public long nextRowId(ClickHouseFamily family) {
        Objects.requireNonNull(family, "family");
        long rowId = increment(rowIds.get(family), family.getTableName() + " row ID");
        validateRowId(family, rowId);
        return rowId;
    }

    @Override
    public void observeRowId(ClickHouseFamily family, long rowId) {
        Objects.requireNonNull(family, "family");
        validateRowId(family, rowId);
        rowIds.get(family).accumulateAndGet(rowId, Math::max);
    }

    private static void validateRowId(ClickHouseFamily family, long rowId) {
        if (rowId < 1) {
            throw new IllegalArgumentException("ClickHouse compatibility row IDs must be positive");
        }
        if (family != ClickHouseFamily.BLOCK && rowId > Integer.MAX_VALUE) {
            throw new IllegalStateException("ClickHouse " + family.getTableName() + " row IDs exceed CoreProtect's signed 32-bit compatibility range");
        }
    }

    private static long increment(AtomicLong value, String name) {
        long next = value.incrementAndGet();
        if (next < 1) {
            throw new IllegalStateException("ClickHouse " + name + " exhausted its signed 64-bit range");
        }
        return next;
    }

}
