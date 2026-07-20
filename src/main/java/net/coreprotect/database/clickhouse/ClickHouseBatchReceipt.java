package net.coreprotect.database.clickhouse;

import java.util.Objects;
import java.util.UUID;

public final class ClickHouseBatchReceipt {

    private final long producerSequence;
    private final UUID batchId;
    private final int rowCount;
    private final int logicalRowCount;

    ClickHouseBatchReceipt(long producerSequence, UUID batchId, int rowCount, int logicalRowCount) {
        if (producerSequence < 1 || rowCount < 0 || logicalRowCount < 0 || logicalRowCount > rowCount) {
            throw new IllegalArgumentException("Invalid ClickHouse batch receipt");
        }
        this.producerSequence = producerSequence;
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.rowCount = rowCount;
        this.logicalRowCount = logicalRowCount;
    }

    public long getProducerSequence() {
        return producerSequence;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getLogicalRowCount() {
        return logicalRowCount;
    }

}
