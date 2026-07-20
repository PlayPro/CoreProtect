package net.coreprotect.database.clickhouse;

import java.util.Objects;
import java.util.UUID;

public final class ClickHouseBatchIdentity {

    private static final String TOKEN_VERSION = "coreprotect-v1";

    private final UUID datasetId;
    private final UUID producerId;
    private final long producerSequence;
    private final UUID batchId;

    public ClickHouseBatchIdentity(UUID datasetId, UUID producerId, long producerSequence, UUID batchId) {
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId");
        this.producerId = Objects.requireNonNull(producerId, "producerId");
        if (producerSequence < 1) {
            throw new IllegalArgumentException("Producer sequence must be positive");
        }
        this.producerSequence = producerSequence;
        this.batchId = Objects.requireNonNull(batchId, "batchId");
    }

    public static ClickHouseBatchIdentity create(UUID datasetId, UUID producerId, long producerSequence) {
        return new ClickHouseBatchIdentity(datasetId, producerId, producerSequence, UUID.randomUUID());
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

    public UUID getBatchId() {
        return batchId;
    }

    public String getDeduplicationToken() {
        return TOKEN_VERSION + ":" + datasetId + ":" + producerId + ":" + producerSequence + ":" + batchId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ClickHouseBatchIdentity)) {
            return false;
        }
        ClickHouseBatchIdentity identity = (ClickHouseBatchIdentity) object;
        return producerSequence == identity.producerSequence && datasetId.equals(identity.datasetId) && producerId.equals(identity.producerId) && batchId.equals(identity.batchId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, producerId, producerSequence, batchId);
    }

    @Override
    public String toString() {
        return getDeduplicationToken();
    }
}
