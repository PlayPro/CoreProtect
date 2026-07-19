package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

import org.bukkit.Location;

import net.coreprotect.database.ConsumerEntitySpawnUpdates;
import net.coreprotect.database.Database;
import net.coreprotect.database.EntitySpawnUpdateCoordinator;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.entity.EntityContainerRollbackUpdate;
import net.coreprotect.model.entity.EntitySpawnData;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.WorldUtils;

final class ClickHouseEntitySpawnUpdates implements ConsumerEntitySpawnUpdates {

    private static final int SELECT_BATCH_SIZE = 500;
    private static final String COLUMNS = "rowid,if(block_rowid_present=1,block_rowid,NULL) AS block_rowid,if(kill_rowid_present=1,kill_rowid,NULL) AS kill_rowid,uuid,current_wid,current_x,current_y,current_z,yaw,pitch,"
            + ClickHouseSchema.binary("if(entity_data_present=1,entity_data,NULL)", "data")
            + ",removed,producer_id,producer_sequence,batch_ordinal,time,wid,x AS key_x,z AS key_z";

    private final ClickHouseConsumerWriteBatch owner;
    private final String eventTable;
    private final UUID datasetId;
    private final Map<Integer, ClickHouseEntityState> states = new LinkedHashMap<>();
    private final Set<Integer> loadedTrackingRowIds = new HashSet<>();
    private final Set<UUID> loadedUuids = new HashSet<>();
    private final Set<Integer> loadedKillRowIds = new HashSet<>();
    private final EntitySpawnUpdateCoordinator coordinator = new EntitySpawnUpdateCoordinator();
    private boolean closed;

    ClickHouseEntitySpawnUpdates(ClickHouseConsumerWriteBatch owner, String eventTable) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.eventTable = Objects.requireNonNull(eventTable, "eventTable");
        datasetId = owner.database().getDatasetId();
    }

    void beginBatch() {
        ensureOpen();
        clearStateCache();
    }

    void batchPublished() {
        clearStateCache();
    }

    void batchDiscarded() {
        clearStateCache();
    }

    void register(ClickHouseEntityState state) {
        ensureOpen();
        cacheState(state, true);
    }

    @Override
    public void prefetch(List<EntitySpawnData> updates) throws Exception {
        ensureOpen();
        Set<Integer> rowIds = new LinkedHashSet<>();
        Set<UUID> uuids = new LinkedHashSet<>();
        Set<Integer> killRowIds = new LinkedHashSet<>();
        Set<Long> blockRowIds = new LinkedHashSet<>();
        for (EntitySpawnData data : updates) {
            if (data.getTrackingRowId() > 0) {
                rowIds.add(data.getTrackingRowId());
            }
            if (data.getUuid() != null) {
                uuids.add(data.getUuid());
            }
            if (data.getPreviousUuid() != null) {
                uuids.add(data.getPreviousUuid());
            }
            if (data.getKillRowId() > 0) {
                killRowIds.add(data.getKillRowId());
            }
            EntitySpawnData.Operation operation = data.getOperation();
            if (operation == null) {
                continue;
            }
            switch (operation) {
                case ROLLBACK:
                case RESTORE:
                case KILL_ROLLBACK:
                case KILL_RESTORE:
                    addPositive(blockRowIds, data.getBlockRowId());
                    break;
                case COMPOSITE_ROLLBACK:
                case COMPOSITE_RESTORE:
                    addPositive(blockRowIds, data.getBlockRowId());
                    addPositive(blockRowIds, data.getPairedBlockRowId());
                    break;
                default:
                    break;
            }
        }
        loadTrackingRows(rowIds);
        loadUuids(uuids);
        loadKillRowIds(killRowIds);
        owner.prefetchBlockTargets(blockRowIds);
    }

    void linkBlock(int trackingRowId, long blockRowId) throws Exception {
        ClickHouseEntityState state = requireState(trackingRowId);
        if (state.getBlockRowId() != null) {
            throw new SQLException("Entity spawn tracking row already has a block link");
        }
        append(trackingRowId, state.withBlockRowId(blockRowId));
    }

    void linkKill(UUID uuid, int killRowId) throws Exception {
        ClickHouseEntityState state = findByUuid(Objects.requireNonNull(uuid, "uuid"), null);
        if (state == null) {
            return;
        }
        int trackingRowId = toTrackingRowId(state.getPointer().getRowId());
        requireAvailable(uuid, killRowId, trackingRowId);
        if (Objects.equals(state.getKillRowId(), killRowId)) {
            return;
        }
        append(trackingRowId, state.withKillRowId(killRowId));
    }

    void requireAvailable(UUID uuid, Integer killRowId, Integer allowedTrackingRowId) throws Exception {
        Objects.requireNonNull(uuid, "uuid");
        rejectDifferentOwner(findByUuid(uuid, null), allowedTrackingRowId, "entity UUID " + uuid);
        if (killRowId != null) {
            rejectDifferentOwner(findByKillRowId(killRowId), allowedTrackingRowId, "entity kill row " + killRowId);
        }
    }

    boolean checkpointLocation(int trackingRowId, int worldId, double x, double y, double z, float yaw, float pitch) throws Exception {
        ClickHouseEntityState state = requireState(trackingRowId);
        if (state.isRemoved()) {
            return false;
        }
        append(trackingRowId, state.withLocation(worldId, x, y, z, yaw, pitch));
        return true;
    }

    @Override
    public EntitySpawnIdentity apply(EntitySpawnData data) {
        Objects.requireNonNull(data, "data");
        ensureOpen();
        if (!coordinator.begin(data)) {
            return null;
        }
        EntitySpawnIdentity createdIdentity = null;
        try {
            switch (data.getOperation()) {
                case VERIFY:
                    verify(data);
                    break;
                case LOCATION:
                    updateLocation(data);
                    break;
                case REMOVED:
                    createdIdentity = remove(data);
                    break;
                case REVIVED:
                    revive(data);
                    break;
                default:
                    owner.executeAtomically("entity_spawn_transition", () -> applyTransition(data));
                    break;
            }
            coordinator.applied(data);
        }
        catch (PermanentTransitionException exception) {
            coordinator.permanentTransitionFailed(data, exception);
            ErrorReporter.report(exception);
        }
        catch (Exception exception) {
            coordinator.failed(data);
            Database.handleWriteFailure(exception);
        }
        return createdIdentity;
    }

    @Override
    public void applyCombined(EntityContainerRollbackUpdate update, Database.SavepointOperation rowUpdate) {
        Objects.requireNonNull(update, "update");
        Objects.requireNonNull(rowUpdate, "rowUpdate");
        EntitySpawnData data = update.getTransition();
        int trackingRowId = data.getTrackingRowId();
        coordinator.beginCombined(update);
        try {
            owner.executeAtomically("entity_container_transition", () -> {
                applyCombinedTransition(data);
                rowUpdate.execute();
            });
            coordinator.combinedApplied(trackingRowId);
        }
        catch (PermanentTransitionException exception) {
            coordinator.permanentCombinedFailed(data, exception);
            ErrorReporter.report(exception);
        }
        catch (Exception exception) {
            coordinator.combinedFailed(data);
            Database.handleWriteFailure(exception);
        }
    }

    @Override
    public void identityFound(UUID uuid) {
        coordinator.entityFound(uuid);
    }

    @Override
    public void afterCommit(boolean committed) {
        coordinator.afterCommit(committed);
        clearStateCache();
    }

    @Override
    public void afterDiscard() {
        coordinator.afterDiscard();
        clearStateCache();
    }

    @Override
    public void close() {
        if (!closed) {
            afterCommit(false);
            closed = true;
        }
    }

    Checkpoint checkpoint() {
        ensureOpen();
        return new Checkpoint(this);
    }

    void restore(Checkpoint checkpoint) {
        ensureOpen();
        Objects.requireNonNull(checkpoint, "checkpoint");
        replace(states, checkpoint.states);
        replace(loadedTrackingRowIds, checkpoint.loadedTrackingRowIds);
        replace(loadedUuids, checkpoint.loadedUuids);
        replace(loadedKillRowIds, checkpoint.loadedKillRowIds);
        coordinator.restore(checkpoint.coordinator);
    }

    private void verify(EntitySpawnData data) throws Exception {
        if (findByUuid(data.getUuid(), false) == null) {
            coordinator.verificationMissing(data);
        }
        else {
            coordinator.verificationFound(data);
        }
    }

    private void updateLocation(EntitySpawnData data) throws Exception {
        ClickHouseEntityState state = findByUuid(data.getUuid(), false);
        if (state == null) {
            coordinator.locationMissing(data);
            return;
        }
        append(toTrackingRowId(state.getPointer().getRowId()), withLocation(state, data.getLocation()));
        coordinator.locationFound(data);
    }

    private EntitySpawnIdentity remove(EntitySpawnData data) throws Exception {
        ClickHouseEntityState state = findByUuid(data.getUuid(), false);
        if (state != null) {
            append(toTrackingRowId(state.getPointer().getRowId()), withLocation(state, data.getLocation()).withData(null).withRemoved(true));
            return null;
        }
        if (findByUuid(data.getUuid(), true) != null) {
            return null;
        }
        return EntitySpawnStatement.insertTerminalIdentity(owner, data);
    }

    private void revive(EntitySpawnData data) throws Exception {
        ClickHouseEntityState state = findByUuid(data.getPreviousUuid(), true);
        if (state != null) {
            requireAvailable(data.getUuid(), null, toTrackingRowId(state.getPointer().getRowId()));
            ClickHouseEntityState revived = withLocation(state.withUuid(data.getUuid()), data.getLocation()).withData(null).withRemoved(false);
            append(toTrackingRowId(state.getPointer().getRowId()), revived);
            coordinator.entityFound(data.getUuid());
        }
        else if (findByUuid(data.getUuid(), false) == null) {
            coordinator.entityMissing(data.getUuid());
        }
        else {
            coordinator.entityFound(data.getUuid());
        }
    }

    private void applyTransition(EntitySpawnData data) throws Exception {
        switch (data.getOperation()) {
            case ROLLBACK:
                applyRollback(data);
                break;
            case RESTORE:
                applyRestore(data);
                break;
            case KILL_ROLLBACK:
                applyKillRollback(data);
                break;
            case KILL_RESTORE:
                applyKillRestore(data);
                break;
            case COMPOSITE_ROLLBACK:
                applyCompositeRollback(data);
                break;
            case COMPOSITE_RESTORE:
                applyCompositeRestore(data);
                break;
            case CLAIM_RELEASE:
                requireState(data.getTrackingRowId());
                coordinator.transitionApplied(data.getTrackingRowId());
                break;
            default:
                break;
        }
    }

    private void applyCombinedTransition(EntitySpawnData data) throws Exception {
        if (!isTransition(data)) {
            throw new PermanentTransitionException("Unsupported combined entity spawn transition " + data.getOperation());
        }
        applyTransition(data);
    }

    private void applyRollback(EntitySpawnData data) throws Exception {
        ClickHouseEntityState state = requireState(data.getTrackingRowId());
        ClickHouseEntityState updated = withLocation(state, data.getLocation()).withData(data.getState()).withKillRowId(null).withRemoved(true);
        updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_SPAWN);
        append(data.getTrackingRowId(), updated);
        coordinator.transitionApplied(data.getTrackingRowId());
    }

    private void applyRestore(EntitySpawnData data) throws Exception {
        ClickHouseEntityState state = requireState(data.getTrackingRowId());
        requireAvailable(data.getUuid(), null, data.getTrackingRowId());
        ClickHouseEntityState updated = withLocation(state.withUuid(data.getUuid()), data.getLocation()).withData(null).withKillRowId(null).withRemoved(false);
        updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_SPAWN);
        append(data.getTrackingRowId(), updated);
        coordinator.entityFound(data.getUuid());
        coordinator.transitionApplied(data.getTrackingRowId());
    }

    private void applyKillRollback(EntitySpawnData data) throws Exception {
        ClickHouseEntityState state = requireKillState(data);
        requireAvailable(data.getUuid(), null, data.getTrackingRowId());
        ClickHouseEntityState updated = withLocation(state.withUuid(data.getUuid()), data.getLocation()).withData(null).withRemoved(false);
        updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_KILL);
        append(data.getTrackingRowId(), updated);
        coordinator.entityFound(data.getUuid());
        coordinator.transitionApplied(data.getTrackingRowId());
    }

    private void applyKillRestore(EntitySpawnData data) throws Exception {
        ClickHouseEntityState state = requireKillState(data);
        ClickHouseEntityState updated = withLocation(state, data.getLocation()).withData(null).withRemoved(true);
        updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_KILL);
        append(data.getTrackingRowId(), updated);
        coordinator.transitionApplied(data.getTrackingRowId());
    }

    private void applyCompositeRollback(EntitySpawnData data) throws Exception {
        ClickHouseEntityState state = requireKillState(data);
        ClickHouseEntityState updated = withLocation(state, data.getLocation()).withData(data.getState()).withRemoved(true);
        updateBlockState(data.getBlockRowId(), 1, LookupActions.ENTITY_SPAWN);
        updateBlockState(data.getPairedBlockRowId(), 1, LookupActions.ENTITY_KILL);
        append(data.getTrackingRowId(), updated);
        coordinator.transitionApplied(data.getTrackingRowId());
    }

    private void applyCompositeRestore(EntitySpawnData data) throws Exception {
        ClickHouseEntityState state = requireKillState(data);
        ClickHouseEntityState updated = state.withData(null).withRemoved(true);
        updateBlockState(data.getBlockRowId(), 0, LookupActions.ENTITY_SPAWN);
        updateBlockState(data.getPairedBlockRowId(), 0, LookupActions.ENTITY_KILL);
        append(data.getTrackingRowId(), updated);
        coordinator.transitionApplied(data.getTrackingRowId());
    }

    private ClickHouseEntityState requireKillState(EntitySpawnData data) throws Exception {
        ClickHouseEntityState state = requireState(data.getTrackingRowId());
        if (state.getKillRowId() == null || state.getKillRowId() != data.getKillRowId()) {
            throw new PermanentTransitionException("Entity spawn tracking row does not match kill row " + data.getKillRowId());
        }
        return state;
    }

    private void updateBlockState(long rowId, int rolledBack, int expectedAction) throws Exception {
        if (!owner.updateBlockState(rowId, rolledBack, expectedAction)) {
            throw new PermanentTransitionException("ClickHouse block row " + rowId + " does not match action " + expectedAction);
        }
    }

    private ClickHouseEntityState requireState(int trackingRowId) throws Exception {
        ClickHouseEntityState state = findByRowId(trackingRowId);
        if (state == null) {
            throw new PermanentTransitionException("Missing entity spawn tracking row " + trackingRowId);
        }
        return state;
    }

    private ClickHouseEntityState findByRowId(int trackingRowId) throws Exception {
        if (!loadedTrackingRowIds.contains(trackingRowId)) {
            loadTrackingRows(Collections.singleton(trackingRowId));
        }
        return states.get(trackingRowId);
    }

    private ClickHouseEntityState findByUuid(UUID uuid, Boolean removed) throws Exception {
        Objects.requireNonNull(uuid, "uuid");
        if (!loadedUuids.contains(uuid)) {
            loadUuids(Collections.singleton(uuid));
        }
        Map<Integer, ClickHouseEntityState> matches = new LinkedHashMap<>();
        for (Map.Entry<Integer, ClickHouseEntityState> entry : states.entrySet()) {
            if (matches(entry.getValue(), uuid, removed)) {
                matches.put(entry.getKey(), entry.getValue());
            }
        }
        if (matches.size() > 1) {
            throw new SQLException("ClickHouse entity UUID resolves to multiple tracking rows: " + uuid);
        }
        return matches.isEmpty() ? null : matches.values().iterator().next();
    }

    private ClickHouseEntityState findByKillRowId(int killRowId) throws Exception {
        if (!loadedKillRowIds.contains(killRowId)) {
            loadKillRowIds(Collections.singleton(killRowId));
        }
        Map<Integer, ClickHouseEntityState> matches = new LinkedHashMap<>();
        for (Map.Entry<Integer, ClickHouseEntityState> entry : states.entrySet()) {
            if (Objects.equals(entry.getValue().getKillRowId(), killRowId)) {
                matches.put(entry.getKey(), entry.getValue());
            }
        }
        if (matches.size() > 1) {
            throw new SQLException("ClickHouse kill row resolves to multiple entity spawn rows: " + killRowId);
        }
        return matches.isEmpty() ? null : matches.values().iterator().next();
    }

    private void loadTrackingRows(Set<Integer> rowIds) throws Exception {
        loadValues("rowid", missing(rowIds, loadedTrackingRowIds), "");
        loadedTrackingRowIds.addAll(rowIds);
    }

    private void loadUuids(Set<UUID> uuids) throws Exception {
        List<UUID> missing = missing(uuids, loadedUuids);
        List<String> values = new ArrayList<>(missing.size());
        for (UUID uuid : missing) {
            values.add(uuid.toString());
        }
        loadValues("uuid", values, "");
        loadedUuids.addAll(uuids);
    }

    private void loadKillRowIds(Set<Integer> killRowIds) throws Exception {
        loadValues("kill_rowid", missing(killRowIds, loadedKillRowIds), " AND kill_rowid_present=1");
        loadedKillRowIds.addAll(killRowIds);
    }

    private void loadValues(String column, List<?> values, String additionalFilter) throws Exception {
        if (values.isEmpty()) {
            return;
        }
        try (Connection connection = owner.openConnection()) {
            for (int offset = 0; offset < values.size(); offset += SELECT_BATCH_SIZE) {
                List<?> batch = values.subList(offset, Math.min(offset + SELECT_BATCH_SIZE, values.size()));
                StringJoiner placeholders = new StringJoiner(",");
                for (int ignored = 0; ignored < batch.size(); ignored++) {
                    placeholders.add("?");
                }
                String sql = "SELECT " + COLUMNS + " FROM " + eventTable + " FINAL WHERE dataset_id=? AND family=? AND " + column + " IN(" + placeholders + ")" + additionalFilter;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, datasetId);
                    statement.setString(2, ClickHouseFamily.ENTITY_SPAWN.getTableName());
                    for (int index = 0; index < batch.size(); index++) {
                        statement.setObject(index + 3, batch.get(index));
                    }
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            ClickHouseEntityState state = readState(resultSet);
                            cacheState(state, false);
                        }
                    }
                }
            }
        }
    }

    private static <T> List<T> missing(Set<T> requested, Set<T> loaded) {
        List<T> values = new ArrayList<>(requested.size());
        for (T value : requested) {
            if (!loaded.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private static void addPositive(Set<Long> values, long value) {
        if (value > 0) {
            values.add(value);
        }
    }

    private static void rejectDifferentOwner(ClickHouseEntityState owner, Integer allowedTrackingRowId, String identity) throws SQLException {
        if (owner == null) {
            return;
        }
        int ownerRowId = toTrackingRowId(owner.getPointer().getRowId());
        if (allowedTrackingRowId == null || ownerRowId != allowedTrackingRowId) {
            throw new SQLException("ClickHouse " + identity + " already belongs to entity spawn row " + ownerRowId);
        }
    }

    private ClickHouseEntityState readState(ResultSet resultSet) throws Exception {
        int rowId = resultSet.getInt("rowid");
        ClickHouseEventPointer pointer = new ClickHouseEventPointer(datasetId, ClickHouseFamily.ENTITY_SPAWN, UUID.fromString(resultSet.getString("producer_id")), resultSet.getLong("producer_sequence"), resultSet.getInt("batch_ordinal"), rowId, resultSet.getInt("time"), resultSet.getInt("wid"), resultSet.getInt("key_x"), resultSet.getInt("key_z"));
        return new ClickHouseEntityState(pointer, nullableLong(resultSet, "block_rowid"), nullableInteger(resultSet, "kill_rowid"), UUID.fromString(resultSet.getString("uuid")), resultSet.getInt("current_wid"), resultSet.getDouble("current_x"), resultSet.getDouble("current_y"), resultSet.getDouble("current_z"), resultSet.getFloat("yaw"), resultSet.getFloat("pitch"), DatabaseUtils.getBytes(resultSet, "data"), resultSet.getInt("removed") == 1);
    }

    private void append(int trackingRowId, ClickHouseEntityState state) throws SQLException {
        if (toTrackingRowId(state.getPointer().getRowId()) != trackingRowId) {
            throw new SQLException("ClickHouse entity state does not match tracking row " + trackingRowId);
        }
        owner.requireBatch().addEntityState(state);
        cacheState(state, true);
    }


    private void cacheState(ClickHouseEntityState state, boolean replace) {
        int rowId = toTrackingRowId(state.getPointer().getRowId());
        if (replace) {
            states.put(rowId, state);
        }
        else {
            states.putIfAbsent(rowId, state);
        }
        loadedTrackingRowIds.add(rowId);
        loadedUuids.add(state.getUuid());
        if (state.getKillRowId() != null) {
            loadedKillRowIds.add(state.getKillRowId());
        }
    }

    private void clearStateCache() {
        states.clear();
        loadedTrackingRowIds.clear();
        loadedUuids.clear();
        loadedKillRowIds.clear();
    }

    private static boolean isTransition(EntitySpawnData data) {
        EntitySpawnData.Operation operation = data.getOperation();
        return operation == EntitySpawnData.Operation.ROLLBACK || operation == EntitySpawnData.Operation.RESTORE || operation == EntitySpawnData.Operation.KILL_ROLLBACK || operation == EntitySpawnData.Operation.KILL_RESTORE || operation == EntitySpawnData.Operation.COMPOSITE_ROLLBACK || operation == EntitySpawnData.Operation.COMPOSITE_RESTORE || operation == EntitySpawnData.Operation.CLAIM_RELEASE;
    }

    private static boolean matches(ClickHouseEntityState state, UUID uuid, Boolean removed) {
        return state.getUuid().equals(uuid) && (removed == null || state.isRemoved() == removed);
    }

    private static ClickHouseEntityState withLocation(ClickHouseEntityState state, Location location) {
        Objects.requireNonNull(location, "location");
        return state.withLocation(WorldUtils.getWorldId(location.getWorld().getName()), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static int toTrackingRowId(long rowId) {
        if (rowId > Integer.MAX_VALUE) {
            throw new IllegalStateException("ClickHouse entity spawn row ID exceeds CoreProtect's signed integer compatibility range");
        }
        return (int) rowId;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("ClickHouse entity spawn updates are closed");
        }
    }

    private static <K, V> void replace(Map<K, V> target, Map<K, V> source) {
        target.clear();
        target.putAll(source);
    }

    private static <E> void replace(Set<E> target, Set<E> source) {
        target.clear();
        target.addAll(source);
    }

    static final class Checkpoint {

        private final Map<Integer, ClickHouseEntityState> states;
        private final Set<Integer> loadedTrackingRowIds;
        private final Set<UUID> loadedUuids;
        private final Set<Integer> loadedKillRowIds;
        private final EntitySpawnUpdateCoordinator.Checkpoint coordinator;

        private Checkpoint(ClickHouseEntitySpawnUpdates updates) {
            states = new LinkedHashMap<>(updates.states);
            loadedTrackingRowIds = new HashSet<>(updates.loadedTrackingRowIds);
            loadedUuids = new HashSet<>(updates.loadedUuids);
            loadedKillRowIds = new HashSet<>(updates.loadedKillRowIds);
            coordinator = updates.coordinator.checkpoint();
        }
    }

    private static final class PermanentTransitionException extends SQLException {

        private static final long serialVersionUID = 1L;

        private PermanentTransitionException(String message) {
            super(message);
        }
    }

}
