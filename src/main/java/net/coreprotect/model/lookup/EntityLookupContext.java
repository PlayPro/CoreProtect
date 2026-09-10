package net.coreprotect.model.lookup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EntityLookupContext {

    private static final int MAXIMUM_REUSABLE_ROWS = 4_096;

    private final Set<UUID> loadedEntityUuids;
    private final Set<UUID> loadedEntityCandidates;
    private final List<Row> rows;
    private final Set<Integer> entitySpawnRowIds;
    private final Set<Long> blockRowIds;
    private final boolean reusable;

    private EntityLookupContext(Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Collection<Row> rows, boolean reusable) {
        this.loadedEntityUuids = immutableSet(loadedEntityUuids);
        this.loadedEntityCandidates = immutableSet(loadedEntityCandidates);
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));

        Set<Integer> entityIds = new LinkedHashSet<>();
        Set<Long> blockIds = new LinkedHashSet<>();
        for (Row row : rows) {
            entityIds.add(row.rowId);
            if (row.blockRowId != null && row.blockRowId > 0L) {
                blockIds.add(row.blockRowId);
            }
        }
        entitySpawnRowIds = Collections.unmodifiableSet(entityIds);
        blockRowIds = Collections.unmodifiableSet(blockIds);
        this.reusable = reusable && rows.size() <= MAXIMUM_REUSABLE_ROWS;
    }

    public static EntityLookupContext legacy(Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates) {
        return new EntityLookupContext(loadedEntityUuids, loadedEntityCandidates, Collections.emptyList(), false);
    }

    public static EntityLookupContext reusable(Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Collection<Row> rows) {
        return new EntityLookupContext(loadedEntityUuids, loadedEntityCandidates, rows, true);
    }

    public Set<UUID> getLoadedEntityUuids() {
        return loadedEntityUuids;
    }

    public Set<UUID> getLoadedEntityCandidates() {
        return loadedEntityCandidates;
    }

    public List<Row> getRows() {
        return rows;
    }

    public Set<Integer> getEntitySpawnRowIds() {
        return entitySpawnRowIds;
    }

    public Set<Long> getBlockRowIds() {
        return blockRowIds;
    }

    public boolean isReusable() {
        return reusable;
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    public static final class Row {

        private final int rowId;
        private final Long blockRowId;
        private final Long time;
        private final UUID uuid;
        private final Integer currentWorldId;
        private final Double x;
        private final Double y;
        private final Double z;

        public Row(int rowId, Long blockRowId, long time, UUID uuid, int currentWorldId, double x, double y, double z) {
            this(rowId, blockRowId, Long.valueOf(time), uuid, Integer.valueOf(currentWorldId), Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
        }

        public Row(int rowId, Long blockRowId, Long time, UUID uuid, Integer currentWorldId, Double x, Double y, Double z) {
            this.rowId = rowId;
            this.blockRowId = blockRowId;
            this.time = time;
            this.uuid = uuid;
            this.currentWorldId = currentWorldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getRowId() {
            return rowId;
        }

        public Long getBlockRowId() {
            return blockRowId;
        }

        public Long getTime() {
            return time;
        }

        public UUID getUuid() {
            return uuid;
        }

        public Integer getCurrentWorldId() {
            return currentWorldId;
        }

        public Double getX() {
            return x;
        }

        public Double getY() {
            return y;
        }

        public Double getZ() {
            return z;
        }
    }
}
