package net.coreprotect.database.clickhouse;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ClickHouseHighWaterMarks {

    private final long producerSequence;
    private final Map<ClickHouseFamily, Long> compatibilityRowIds;

    public ClickHouseHighWaterMarks(long producerSequence, Map<ClickHouseFamily, Long> compatibilityRowIds) {
        if (producerSequence < 0) {
            throw new IllegalArgumentException("Producer sequence cannot be negative");
        }
        Objects.requireNonNull(compatibilityRowIds, "compatibilityRowIds");
        EnumMap<ClickHouseFamily, Long> validatedRowIds = new EnumMap<>(ClickHouseFamily.class);
        for (Map.Entry<ClickHouseFamily, Long> entry : compatibilityRowIds.entrySet()) {
            ClickHouseFamily family = Objects.requireNonNull(entry.getKey(), "event family");
            Long rowId = Objects.requireNonNull(entry.getValue(), "compatibility row ID");
            if (rowId < 0) {
                throw new IllegalArgumentException("Compatibility row ID cannot be negative");
            }
            validatedRowIds.put(family, rowId);
        }
        this.producerSequence = producerSequence;
        this.compatibilityRowIds = Collections.unmodifiableMap(validatedRowIds);
    }

    public long getProducerSequence() {
        return producerSequence;
    }

    public long getCompatibilityRowId(ClickHouseFamily family) {
        return compatibilityRowIds.getOrDefault(Objects.requireNonNull(family, "family"), 0L);
    }

}
