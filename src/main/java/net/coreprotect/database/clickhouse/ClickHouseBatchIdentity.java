package net.coreprotect.database.clickhouse;

import java.util.Objects;
import java.util.UUID;

public final class ClickHouseBatchIdentity {

    private static final String TOKEN_VERSION = "coreprotect-v2";

    private final UUID datasetId;
    private final long batchSequence;
    private final UUID batchId;

    public ClickHouseBatchIdentity(UUID datasetId, long batchSequence, UUID batchId) {
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId");
        if (batchSequence < 1) {
            throw new IllegalArgumentException("Batch sequence must be positive");
        }
        this.batchSequence = batchSequence;
        this.batchId = Objects.requireNonNull(batchId, "batchId");
    }

    public static ClickHouseBatchIdentity create(UUID datasetId, long batchSequence) {
        return new ClickHouseBatchIdentity(datasetId, batchSequence, UUID.randomUUID());
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public long getBatchSequence() {
        return batchSequence;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public String getDeduplicationToken() {
        return TOKEN_VERSION + ":" + datasetId + ":" + batchSequence + ":" + batchId;
    }
}
