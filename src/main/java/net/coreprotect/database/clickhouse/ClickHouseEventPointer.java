package net.coreprotect.database.clickhouse;

import java.util.Objects;
import java.util.UUID;

public final class ClickHouseEventPointer {

    private final ClickHouseFamily family;
    private final UUID datasetId;
    private final long batchSequence;
    private final int batchOrdinal;
    private final long rowId;
    private final int time;
    private final int worldId;
    private final int x;
    private final int z;

    public ClickHouseEventPointer(UUID datasetId, ClickHouseFamily family, long batchSequence, int batchOrdinal, long rowId, int time, int worldId, int x, int z) {
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId");
        this.family = Objects.requireNonNull(family, "family");
        if (batchSequence < 1 || batchOrdinal < 0 || rowId < 1) {
            throw new IllegalArgumentException("ClickHouse event pointers require positive sequence/row IDs and a non-negative ordinal");
        }
        this.batchSequence = batchSequence;
        this.batchOrdinal = batchOrdinal;
        this.rowId = rowId;
        this.time = time;
        this.worldId = worldId;
        this.x = x;
        this.z = z;
    }

    public ClickHouseFamily getFamily() {
        return family;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public long getBatchSequence() {
        return batchSequence;
    }

    public int getBatchOrdinal() {
        return batchOrdinal;
    }

    public long getRowId() {
        return rowId;
    }

    public int getTime() {
        return time;
    }

    public int getWorldId() {
        return worldId;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

}
