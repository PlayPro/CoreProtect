package net.coreprotect.database.clickhouse;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class ClickHouseIdentityAllocator implements ClickHouseRowIdAllocator {

    private final UUID datasetId;
    private final ClickHouseIdentityReservation reservation;

    ClickHouseIdentityAllocator(UUID datasetId, ClickHouseIdentityReservation reservation, ClickHouseHighWaterMarks highWaterMarks) {
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId");
        this.reservation = Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(highWaterMarks, "highWaterMarks");
        if (highWaterMarks.getBatchSequence() > 0) {
            reservation.observe(ClickHouseIdentityReservation.BATCH_SEQUENCE, highWaterMarks.getBatchSequence());
        }
        for (ClickHouseFamily family : ClickHouseFamily.values()) {
            long rowId = highWaterMarks.getCompatibilityRowId(family);
            if (rowId > 0) {
                reservation.observe(family.getTableName(), rowId);
            }
        }
    }

    public ClickHouseBatchIdentity nextBatchIdentity() throws SQLException {
        long sequence = reservation.next(ClickHouseIdentityReservation.BATCH_SEQUENCE);
        return ClickHouseBatchIdentity.create(datasetId, sequence);
    }

    @Override
    public long nextRowId(ClickHouseFamily family) throws SQLException {
        Objects.requireNonNull(family, "family");
        long rowId = reservation.next(family.getTableName());
        validateRowId(family, rowId);
        return rowId;
    }

    @Override
    public void observeRowId(ClickHouseFamily family, long rowId) {
        Objects.requireNonNull(family, "family");
        validateRowId(family, rowId);
        reservation.observe(family.getTableName(), rowId);
    }

    private static void validateRowId(ClickHouseFamily family, long rowId) {
        if (rowId < 1) {
            throw new IllegalArgumentException("ClickHouse compatibility row IDs must be positive");
        }
        if (family != ClickHouseFamily.BLOCK && rowId > Integer.MAX_VALUE) {
            throw new IllegalStateException("ClickHouse " + family.getTableName() + " row IDs exceed CoreProtect's signed 32-bit compatibility range");
        }
    }

}
