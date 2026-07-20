package net.coreprotect.database.clickhouse;

import java.util.Objects;
import java.util.UUID;

public final class ClickHouseEventPointer {

    private final ClickHouseFamily family;
    private final UUID datasetId;
    private final UUID producerId;
    private final long producerSequence;
    private final int batchOrdinal;
    private final long rowId;
    private final int time;
    private final int worldId;
    private final int x;
    private final int z;

    public ClickHouseEventPointer(UUID datasetId, ClickHouseFamily family, UUID producerId, long producerSequence, int batchOrdinal, long rowId, int time, int worldId, int x, int z) {
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId");
        this.family = Objects.requireNonNull(family, "family");
        this.producerId = Objects.requireNonNull(producerId, "producerId");
        if (producerSequence < 1 || batchOrdinal < 0 || rowId < 1) {
            throw new IllegalArgumentException("ClickHouse event pointers require positive sequence/row IDs and a non-negative ordinal");
        }
        this.producerSequence = producerSequence;
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

    public UUID getProducerId() {
        return producerId;
    }

    public long getProducerSequence() {
        return producerSequence;
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ClickHouseEventPointer)) {
            return false;
        }
        ClickHouseEventPointer pointer = (ClickHouseEventPointer) object;
        return producerSequence == pointer.producerSequence
                && batchOrdinal == pointer.batchOrdinal
                && rowId == pointer.rowId
                && time == pointer.time
                && worldId == pointer.worldId
                && x == pointer.x
                && z == pointer.z
                && family == pointer.family
                && datasetId.equals(pointer.datasetId)
                && producerId.equals(pointer.producerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, family, producerId, producerSequence, batchOrdinal, rowId, time, worldId, x, z);
    }

}
