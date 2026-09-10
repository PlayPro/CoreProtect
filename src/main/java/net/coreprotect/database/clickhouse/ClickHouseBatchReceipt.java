package net.coreprotect.database.clickhouse;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public final class ClickHouseBatchReceipt {

    private final long batchSequence;
    private final UUID batchId;
    private final int rowCount;
    private final int logicalRowCount;
    private final Map<Integer, Integer> partitionRowCounts;

    ClickHouseBatchReceipt(long batchSequence, UUID batchId, int rowCount, int logicalRowCount, Map<Integer, Integer> partitionRowCounts) {
        if (batchSequence < 1 || rowCount < 0 || logicalRowCount < 0 || logicalRowCount > rowCount) {
            throw new IllegalArgumentException("Invalid ClickHouse batch receipt");
        }
        TreeMap<Integer, Integer> partitions = new TreeMap<>(Objects.requireNonNull(partitionRowCounts, "partitionRowCounts"));
        int partitionRowCount = 0;
        for (Map.Entry<Integer, Integer> partition : partitions.entrySet()) {
            if (partition.getKey() < 0 || partition.getValue() == null || partition.getValue() < 1) {
                throw new IllegalArgumentException("Invalid ClickHouse batch partition receipt");
            }
            partitionRowCount = Math.addExact(partitionRowCount, partition.getValue());
        }
        if (partitionRowCount != rowCount) {
            throw new IllegalArgumentException("ClickHouse batch partition counts do not match the row count");
        }
        this.batchSequence = batchSequence;
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.rowCount = rowCount;
        this.logicalRowCount = logicalRowCount;
        this.partitionRowCounts = Collections.unmodifiableMap(partitions);
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

    Map<Integer, Integer> getPartitionRowCounts() {
        return partitionRowCounts;
    }

}
