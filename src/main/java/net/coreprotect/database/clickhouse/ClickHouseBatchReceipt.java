package net.coreprotect.database.clickhouse;

import java.util.Objects;
import java.util.UUID;

public final class ClickHouseBatchReceipt {

    private final long batchSequence;
    private final UUID batchId;
    private final int rowCount;
    private final int logicalRowCount;

    ClickHouseBatchReceipt(long batchSequence, UUID batchId, int rowCount, int logicalRowCount) {
        if (batchSequence < 1 || rowCount < 0 || logicalRowCount < 0 || logicalRowCount > rowCount) {
            throw new IllegalArgumentException("Invalid ClickHouse batch receipt");
        }
        this.batchSequence = batchSequence;
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.rowCount = rowCount;
        this.logicalRowCount = logicalRowCount;
    }

    public long getBatchSequence() {
        return batchSequence;
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
