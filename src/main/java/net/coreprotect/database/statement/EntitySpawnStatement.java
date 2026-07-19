package net.coreprotect.database.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Location;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.database.rollback.EntitySpawnRollbackHandler;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.entity.EntityContainerRollbackUpdate;
import net.coreprotect.model.entity.EntityInteractionOrigin;
import net.coreprotect.model.entity.EntitySpawnData;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.model.entity.EntitySpawnRecord;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.serialize.JsonSerialization;

public final class EntitySpawnStatement {

    private static final int SELECT_BATCH_SIZE = 500;
    private static final AtomicLong VERSION_COUNTER = new AtomicLong(System.currentTimeMillis() * 1000L);

    private EntitySpawnStatement() {
        throw new IllegalStateException("Database class");
    }

    public static int insert(PreparedStatement statement, int time, EntitySpawnData data, Location loggedLocation) throws Exception {
        Location currentLocation = data.getLocation();
        int rowid = Math.toIntExact(CoreProtect.getInstance().rowNumbers().nextRowNumber("entity_spawn", statement.getConnection()));
        setInsertValues(statement, rowid, time, 0, 0, data.getUuid().toString(), WorldUtils.getWorldId(loggedLocation.getWorld().getName()), WorldUtils.getWorldId(currentLocation.getWorld().getName()), loggedLocation.getX(), loggedLocation.getY(), loggedLocation.getZ(), currentLocation.getX(), currentLocation.getY(), currentLocation.getZ(), currentLocation.getYaw(), currentLocation.getPitch(), null, 0);
        if (statement.executeUpdate() != 1) {
            throw new SQLException("Entity spawn tracking insert did not insert one row");
        }
        return rowid;
    }

    public static EntitySpawnIdentity insertIdentity(PreparedStatement statement, int time, UUID uuid, EntityInteractionOrigin origin, Location currentLocation) throws Exception {
        int rowid = Math.toIntExact(CoreProtect.getInstance().rowNumbers().nextRowNumber("entity_spawn", statement.getConnection()));
        setInsertValues(statement, rowid, time, 0, 0, uuid.toString(), origin.getWorldId(), WorldUtils.getWorldId(currentLocation.getWorld().getName()), origin.getX(), origin.getY(), origin.getZ(), currentLocation.getX(), currentLocation.getY(), currentLocation.getZ(), currentLocation.getYaw(), currentLocation.getPitch(), null, 0);
        if (statement.executeUpdate() != 1) {
            throw new SQLException("Entity identity insert did not insert one row");
        }
        return new EntitySpawnIdentity(rowid, uuid, origin.getWorldId(), origin.getX(), origin.getY(), origin.getZ());
    }

    public static void checkpoint(PreparedStatement statement, EntitySpawnIdentity identity, Location location) throws Exception {
        EntitySpawnRecord record = loadRecord(statement.getConnection(), identity.getRowId());
        if (record == null || record.isRemoved()) {
            throw new SQLException("Entity interaction could not checkpoint its tracking row");
        }
        setInsertValues(statement, record.getRowId(), (int) (System.currentTimeMillis() / 1000L), record.getBlockRowId(), record.getKillRowId(), record.getUuid().toString(), record.getOriginalWorldId(), WorldUtils.getWorldId(location.getWorld().getName()), record.getOriginalX(), record.getOriginalY(), record.getOriginalZ(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(), stateString(record.getState()), 0);
        if (statement.executeUpdate() != 1) {
            throw new SQLException("Entity interaction checkpoint did not insert one tracking version");
        }
    }

    public static PreparedStatement prepareBlockLink(Connection connection) throws SQLException {
        return Database.prepareStatement(connection, Database.ENTITY_SPAWN);
    }

    public static void linkBlock(PreparedStatement statement, int trackingRowId, long blockRowId) throws SQLException {
        EntitySpawnRecord record = loadRecord(statement.getConnection(), trackingRowId);
        if (record == null) {
            throw new SQLException("Entity spawn tracking row did not link to its block row");
        }
        setInsertValues(statement, trackingRowId, (int) (System.currentTimeMillis() / 1000L), blockRowId, record.getKillRowId(), record.getUuid().toString(), record.getOriginalWorldId(), record.getWorldId(), record.getOriginalX(), record.getOriginalY(), record.getOriginalZ(), record.getX(), record.getY(), record.getZ(), record.getYaw(), record.getPitch(), stateString(record.getState()), record.isRemoved() ? 1 : 0);
        statement.executeUpdate();
    }

    public static PreparedStatement prepareKillLink(Connection connection) throws SQLException {
        return Database.prepareStatement(connection, Database.ENTITY_SPAWN);
    }

    public static void addKillLink(PreparedStatement statement, String uuid, long killRowId) throws SQLException {
        EntitySpawnIdentity identity = loadIdentity(statement.getConnection(), UUID.fromString(uuid));
        if (identity == null) {
            return;
        }
        EntitySpawnRecord record = loadRecord(statement.getConnection(), identity.getRowId());
        if (record == null) {
            return;
        }
        setInsertValues(statement, identity.getRowId(), (int) (System.currentTimeMillis() / 1000L), record.getBlockRowId(), killRowId, record.getUuid().toString(), record.getOriginalWorldId(), record.getWorldId(), record.getOriginalX(), record.getOriginalY(), record.getOriginalZ(), record.getX(), record.getY(), record.getZ(), record.getYaw(), record.getPitch(), stateString(record.getState()), record.isRemoved() ? 1 : 0);
        statement.addBatch();
    }

    private static long nextVersion() {
        return VERSION_COUNTER.incrementAndGet();
    }

    private static void setInsertValues(PreparedStatement statement, int rowid, int time, long blockRowId, long killRowId, String uuid, int wid, int currentWid, double originX, double originY, double originZ, double x, double y, double z, float yaw, float pitch, String state, int removed) throws SQLException {
        statement.setInt(1, rowid);
        statement.setLong(2, nextVersion());
        statement.setInt(3, time);
        statement.setLong(4, blockRowId);
        statement.setLong(5, killRowId);
        statement.setString(6, uuid);
        statement.setInt(7, wid);
        statement.setInt(8, currentWid);
        statement.setDouble(9, originX);
        statement.setDouble(10, originY);
        statement.setDouble(11, originZ);
        statement.setDouble(12, x);
        statement.setDouble(13, y);
        statement.setDouble(14, z);
        statement.setFloat(15, yaw);
        statement.setFloat(16, pitch);
        statement.setString(17, state);
        statement.setInt(18, removed);
    }

    private static String stateString(List<Object> state) {
        if (state == null) {
            return null;
        }
        return JsonSerialization.GSON.toJson(state);
    }

    public static Map<Integer, EntitySpawnRecord> loadRecords(Connection connection, Collection<Integer> rowIds) throws SQLException {
        return loadRecords(connection, rowIds, true);
    }

    public static Map<Integer, EntitySpawnRecord> loadLocationRecords(Connection connection, Collection<Integer> rowIds) throws SQLException {
        return loadRecords(connection, rowIds, false);
    }

    private static Map<Integer, EntitySpawnRecord> loadRecords(Connection connection, Collection<Integer> rowIds, boolean includeState) throws SQLException {
        Map<Integer, EntitySpawnRecord> records = new HashMap<>();
        if (rowIds.isEmpty()) {
            return records;
        }

        List<Integer> ids = new ArrayList<>(rowIds);
        for (int offset = 0; offset < ids.size(); offset += SELECT_BATCH_SIZE) {
            int end = Math.min(offset + SELECT_BATCH_SIZE, ids.size());
            loadRecordBatch(connection, ids.subList(offset, end), records, false, includeState);
        }

        return records;
    }

    public static Map<Integer, EntitySpawnRecord> loadRecordsByKillRowIds(Connection connection, Collection<Integer> killRowIds) throws SQLException {
        Map<Integer, EntitySpawnRecord> records = new HashMap<>();
        if (killRowIds.isEmpty()) {
            return records;
        }

        List<Integer> ids = new ArrayList<>(killRowIds);
        for (int offset = 0; offset < ids.size(); offset += SELECT_BATCH_SIZE) {
            int end = Math.min(offset + SELECT_BATCH_SIZE, ids.size());
            loadRecordBatch(connection, ids.subList(offset, end), records, true);
        }
        return records;
    }

    public static Map<UUID, EntitySpawnIdentity> loadIdentities(Connection connection, Collection<UUID> uuids) throws SQLException {
        Map<UUID, EntitySpawnIdentity> identities = new HashMap<>();
        if (uuids.isEmpty()) {
            return identities;
        }

        List<UUID> ids = new ArrayList<>(uuids);
        for (int offset = 0; offset < ids.size(); offset += SELECT_BATCH_SIZE) {
            int end = Math.min(offset + SELECT_BATCH_SIZE, ids.size());
            for (EntitySpawnIdentity identity : loadIdentityBatch(connection, ids.subList(offset, end), false)) {
                identities.put(identity.getUuid(), identity);
            }
        }
        return identities;
    }

    public static Map<Integer, EntitySpawnIdentity> loadIdentitiesByRowIds(Connection connection, Collection<Integer> rowIds) throws SQLException {
        Map<Integer, EntitySpawnIdentity> identities = new HashMap<>();
        if (rowIds.isEmpty()) {
            return identities;
        }

        List<Integer> ids = new ArrayList<>(rowIds);
        for (int offset = 0; offset < ids.size(); offset += SELECT_BATCH_SIZE) {
            int end = Math.min(offset + SELECT_BATCH_SIZE, ids.size());
            for (EntitySpawnIdentity identity : loadIdentityBatch(connection, ids.subList(offset, end), true)) {
                identities.put(identity.getRowId(), identity);
            }
        }
        return identities;
    }

    public static Set<UUID> loadActiveUuids(Connection connection, Location location, Integer[] radius) throws Exception {
        return loadActiveUuids(connection, location, radius, 0, 0);
    }

    public static Set<UUID> loadActiveUuids(Connection connection, Location location, Integer[] radius, long startTime, long endTime) throws Exception {
        Set<UUID> uuids = new HashSet<>();
        StringBuilder query = new StringBuilder("SELECT uuid FROM ").append(ConfigHandler.prefix).append("entity_spawn FINAL WHERE removed=0 AND current_wid=? AND x>=? AND x<? AND z>=? AND z<?");
        if (startTime > 0) {
            query.append(" AND time>?");
        }
        if (endTime > 0) {
            query.append(" AND time<=?");
        }
        int minX = Math.floorDiv(radius[1], 16) << 4;
        int maxX = (Math.floorDiv(radius[2], 16) << 4) + 16;
        int minZ = Math.floorDiv(radius[5], 16) << 4;
        int maxZ = (Math.floorDiv(radius[6], 16) << 4) + 16;

        try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
            statement.setInt(1, WorldUtils.getWorldId(location.getWorld().getName()));
            statement.setInt(2, minX);
            statement.setInt(3, maxX);
            statement.setInt(4, minZ);
            statement.setInt(5, maxZ);
            int parameterIndex = 6;
            if (startTime > 0) {
                statement.setLong(parameterIndex++, startTime);
            }
            if (endTime > 0) {
                statement.setLong(parameterIndex, endTime);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    uuids.add(UUID.fromString(resultSet.getString("uuid")));
                }
            }
        }
        return uuids;
    }

    private static void loadRecordBatch(Connection connection, List<Integer> ids, Map<Integer, EntitySpawnRecord> records, boolean byKillRowId) throws SQLException {
        loadRecordBatch(connection, ids, records, byKillRowId, !byKillRowId);
    }

    private static void loadRecordBatch(Connection connection, List<Integer> ids, Map<Integer, EntitySpawnRecord> records, boolean byKillRowId, boolean includeState) throws SQLException {
        StringJoiner placeholders = new StringJoiner(",");
        for (int ignored : ids) {
            placeholders.add("?");
        }

        String keyColumn = byKillRowId ? "kill_rowid" : "rowid";
        String columns = "rowid AS id,block_rowid,kill_rowid,uuid,wid,origin_x,origin_y,origin_z,current_wid,x,y,z,yaw,pitch,removed" + (includeState ? ",data" : "");
        String query = "SELECT " + columns + " FROM " + ConfigHandler.prefix + "entity_spawn FINAL WHERE " + keyColumn + " IN(" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            for (int index = 0; index < ids.size(); index++) {
                statement.setInt(index + 1, ids.get(index));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int rowId = resultSet.getInt("id");
                    int killRowId = resultSet.getInt("kill_rowid");
                    List<Object> state = null;
                    if (includeState) {
                        String serializedState = resultSet.getString("data");
                        state = serializedState == null ? null : EntityStatement.deserializeData(serializedState);
                        if (state != null && state.size() < 4) {
                            state = null;
                        }
                    }
                    EntitySpawnRecord record = new EntitySpawnRecord(rowId, resultSet.getLong("block_rowid"), killRowId, UUID.fromString(resultSet.getString("uuid")), resultSet.getInt("wid"), resultSet.getDouble("origin_x"), resultSet.getDouble("origin_y"), resultSet.getDouble("origin_z"), resultSet.getInt("current_wid"), resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z"), resultSet.getFloat("yaw"), resultSet.getFloat("pitch"), resultSet.getInt("removed") == 1, state);
                    records.put(byKillRowId ? killRowId : rowId, record);
                }
            }
        }
    }

    private static EntitySpawnRecord loadRecord(Connection connection, int rowId) throws SQLException {
        Map<Integer, EntitySpawnRecord> records = loadRecords(connection, List.of(rowId));
        return records.get(rowId);
    }

    private static EntitySpawnIdentity loadIdentity(Connection connection, UUID uuid) throws SQLException {
        Map<UUID, EntitySpawnIdentity> identities = loadIdentities(connection, List.of(uuid));
        return identities.get(uuid);
    }

    private static List<EntitySpawnIdentity> loadIdentityBatch(Connection connection, List<?> ids, boolean byRowId) throws SQLException {
        StringJoiner placeholders = new StringJoiner(",");
        for (int ignored = 0; ignored < ids.size(); ignored++) {
            placeholders.add("?");
        }

        String keyColumn = byRowId ? "rowid" : "uuid";
        String query = "SELECT rowid AS id,uuid,wid,origin_x,origin_y,origin_z FROM " + ConfigHandler.prefix + "entity_spawn FINAL WHERE " + keyColumn + " IN(" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            for (int index = 0; index < ids.size(); index++) {
                if (byRowId) {
                    statement.setInt(index + 1, (Integer) ids.get(index));
                }
                else {
                    statement.setString(index + 1, ids.get(index).toString());
                }
            }

            List<EntitySpawnIdentity> identities = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    identities.add(new EntitySpawnIdentity(resultSet.getInt("id"), UUID.fromString(resultSet.getString("uuid")), resultSet.getInt("wid"), resultSet.getDouble("origin_x"), resultSet.getDouble("origin_y"), resultSet.getDouble("origin_z")));
                }
            }
            return identities;
        }
    }

    public static final class Updates implements AutoCloseable {

        private final PreparedStatement entitySpawn;
        private final PreparedStatement exists;
        private final PreparedStatement trackingRowExists;
        private final PreparedStatement trackingKillStateMatches;
        private final PreparedStatement blockStateMatches;
        private final Statement transitionStatement;
        private final Map<UUID, LocationConfirmation> locationConfirmations = new LinkedHashMap<>();
        private final Map<UUID, Long> verificationConfirmations = new LinkedHashMap<>();
        private final Set<UUID> missingRows = new HashSet<>();
        private final List<EntitySpawnData> lifecycleData = new ArrayList<>();
        private final Set<EntitySpawnData> appliedLifecycleData = new HashSet<>();
        private final Set<UUID> deferredLifecycleUuids = new HashSet<>();
        private final Map<Integer, EntitySpawnData> transitionData = new LinkedHashMap<>();
        private final Set<Integer> transitionRows = new HashSet<>();
        private final Set<Integer> permanentTransitionRows = new HashSet<>();
        private final Map<Integer, EntityContainerRollbackUpdate> combinedTransitionData = new LinkedHashMap<>();
        private final Set<Integer> combinedTransitionRows = new HashSet<>();
        private final Set<Integer> permanentCombinedTransitionRows = new HashSet<>();

        public Updates(Connection connection) throws Exception {
            entitySpawn = Database.prepareStatement(connection, Database.ENTITY_SPAWN);
            exists = connection.prepareStatement("SELECT 1 FROM " + ConfigHandler.prefix + "entity_spawn FINAL WHERE uuid=? AND removed=0 LIMIT 1");
            trackingRowExists = connection.prepareStatement("SELECT 1 FROM " + ConfigHandler.prefix + "entity_spawn FINAL WHERE rowid=? LIMIT 1");
            trackingKillStateMatches = connection.prepareStatement("SELECT uuid,removed FROM " + ConfigHandler.prefix + "entity_spawn FINAL WHERE rowid=? AND kill_rowid=? LIMIT 1");
            blockStateMatches = connection.prepareStatement("SELECT 1 FROM " + ConfigHandler.prefix + "block FINAL WHERE rowid=? AND action=? AND rolled_back=? LIMIT 1");
            transitionStatement = connection.createStatement();
        }

        public void apply(EntitySpawnData data) {
            boolean lifecycleUpdate = data.getOperation() == EntitySpawnData.Operation.VERIFY || data.getOperation() == EntitySpawnData.Operation.LOCATION || data.getOperation() == EntitySpawnData.Operation.REMOVED || data.getOperation() == EntitySpawnData.Operation.REVIVED;
            if (lifecycleUpdate && dependsOnDeferredLifecycle(data)) {
                deferLifecycle(data);
                try {
                    Queue.queueEntitySpawnUpdate(data);
                }
                catch (Exception e) {
                    lifecycleData.add(data);
                    ErrorReporter.report(e);
                }
                return;
            }
            if (lifecycleUpdate) {
                lifecycleData.add(data);
            }
            if (data.getOperation() == EntitySpawnData.Operation.ROLLBACK || data.getOperation() == EntitySpawnData.Operation.RESTORE || data.getOperation() == EntitySpawnData.Operation.KILL_ROLLBACK || data.getOperation() == EntitySpawnData.Operation.KILL_RESTORE || data.getOperation() == EntitySpawnData.Operation.COMPOSITE_ROLLBACK || data.getOperation() == EntitySpawnData.Operation.COMPOSITE_RESTORE || data.getOperation() == EntitySpawnData.Operation.CLAIM_RELEASE) {
                transitionData.put(data.getTrackingRowId(), data);
            }
            try {
                switch (data.getOperation()) {
                    case VERIFY:
                        if (exists(data.getUuid())) {
                            missingRows.remove(data.getUuid());
                            verificationConfirmations.put(data.getUuid(), data.getVerificationEpoch());
                        }
                        else {
                            verificationConfirmations.remove(data.getUuid());
                            missingRows.add(data.getUuid());
                        }
                        break;
                    case LOCATION:
                        if (insertLocationVersion(data.getUuid(), data.getLocation(), null, null)) {
                            missingRows.remove(data.getUuid());
                            locationConfirmations.put(data.getUuid(), new LocationConfirmation(data));
                        }
                        else {
                            locationConfirmations.remove(data.getUuid());
                            missingRows.add(data.getUuid());
                        }
                        break;
                    case REMOVED:
                        insertLocationVersion(data.getUuid(), data.getLocation(), 1, null);
                        break;
                    case REVIVED:
                        if (insertRevivedVersion(data.getPreviousUuid(), data.getUuid(), data.getLocation())) {
                            missingRows.remove(data.getUuid());
                        }
                        else {
                            missingRows.add(data.getUuid());
                        }
                        break;
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
                        if (!trackingRowExists(data.getTrackingRowId())) {
                            throw new PermanentTransitionException("Missing entity spawn tracking row for claim release");
                        }
                        transitionRows.add(data.getTrackingRowId());
                        break;
                    default:
                        break;
                }
                if (lifecycleUpdate) {
                    appliedLifecycleData.add(data);
                }
            }
            catch (PermanentTransitionException e) {
                permanentTransitionRows.add(data.getTrackingRowId());
                ErrorReporter.report(e);
            }
            catch (Exception e) {
                if (lifecycleUpdate || introducesTrackedUuid(data)) {
                    deferLifecycle(data);
                }
                ErrorReporter.report(e);
            }
        }

        public void applyCombined(EntityContainerRollbackUpdate update, Database.SavepointOperation rowUpdate) {
            EntitySpawnData data = update.getTransition();
            int trackingRowId = data.getTrackingRowId();
            combinedTransitionData.put(trackingRowId, update);
            try {
                Database.executeSavepoint(transitionStatement, "entity_container_transition", () -> {
                    applyCombinedTransition(data);
                    rowUpdate.execute();
                });
                transitionRows.remove(trackingRowId);
                combinedTransitionRows.add(trackingRowId);
            }
            catch (PermanentTransitionException e) {
                transitionRows.remove(trackingRowId);
                if (e.getSuppressed().length == 0) {
                    permanentCombinedTransitionRows.add(trackingRowId);
                }
                else if (introducesTrackedUuid(data)) {
                    deferLifecycle(data);
                }
                ErrorReporter.report(e);
            }
            catch (Exception e) {
                transitionRows.remove(trackingRowId);
                if (introducesTrackedUuid(data)) {
                    deferLifecycle(data);
                }
                ErrorReporter.report(e);
            }
        }

        private void applyCombinedTransition(EntitySpawnData data) throws Exception {
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
                    if (!trackingRowExists(data.getTrackingRowId())) {
                        throw new PermanentTransitionException("Missing entity spawn tracking row for claim release");
                    }
                    transitionRows.add(data.getTrackingRowId());
                    break;
                default:
                    throw new PermanentTransitionException("Unsupported combined entity spawn transition " + data.getOperation());
            }
        }

        private boolean dependsOnDeferredLifecycle(EntitySpawnData data) {
            return deferredLifecycleUuids.contains(data.getUuid()) || (data.getOperation() == EntitySpawnData.Operation.REVIVED && data.getPreviousUuid() != null && deferredLifecycleUuids.contains(data.getPreviousUuid()));
        }

        private void deferLifecycle(EntitySpawnData data) {
            if (data.getUuid() != null) {
                deferredLifecycleUuids.add(data.getUuid());
            }
            if (data.getOperation() == EntitySpawnData.Operation.REVIVED && data.getPreviousUuid() != null) {
                deferredLifecycleUuids.add(data.getPreviousUuid());
            }
        }

        private boolean introducesTrackedUuid(EntitySpawnData data) {
            return data.getOperation() == EntitySpawnData.Operation.RESTORE || data.getOperation() == EntitySpawnData.Operation.KILL_ROLLBACK;
        }

        private void applyRollback(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_transition", () -> {
                insertTrackingVersion(data.getTrackingRowId(), null, data.getLocation(), data.getState(), 1, false, 0, "entity spawn tracking rollback");
                updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_SPAWN);
            });
            transitionRows.add(data.getTrackingRowId());
        }

        private void applyRestore(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_transition", () -> {
                insertTrackingVersion(data.getTrackingRowId(), data.getUuid(), data.getLocation(), null, 0, false, 0, "entity spawn tracking restore");
                updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_SPAWN);
            });
            missingRows.remove(data.getUuid());
            transitionRows.add(data.getTrackingRowId());
        }

        private void applyKillRollback(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_kill_transition", () -> {
                insertTrackingVersion(data.getTrackingRowId(), data.getUuid(), data.getLocation(), null, 0, true, data.getKillRowId(), "tracked entity kill rollback");
                updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_KILL);
            });
            missingRows.remove(data.getUuid());
            transitionRows.add(data.getTrackingRowId());
        }

        private void applyKillRestore(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_kill_transition", () -> {
                insertTrackingVersion(data.getTrackingRowId(), null, data.getLocation(), null, 1, true, data.getKillRowId(), "tracked entity kill restore");
                updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_KILL);
            });
            transitionRows.add(data.getTrackingRowId());
        }

        private void applyCompositeRestore(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_composite_transition", () -> {
                insertTrackingVersion(data.getTrackingRowId(), null, null, null, 1, true, data.getKillRowId(), "tracked entity composite restore");
                updateBlockState(data.getBlockRowId(), 0, LookupActions.ENTITY_SPAWN);
                updateBlockState(data.getPairedBlockRowId(), 0, LookupActions.ENTITY_KILL);
            });
            transitionRows.add(data.getTrackingRowId());
        }

        private void applyCompositeRollback(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_composite_transition", () -> {
                insertTrackingVersion(data.getTrackingRowId(), null, data.getLocation(), data.getState(), 1, true, data.getKillRowId(), "tracked entity composite rollback");
                updateBlockState(data.getBlockRowId(), 1, LookupActions.ENTITY_SPAWN);
                updateBlockState(data.getPairedBlockRowId(), 1, LookupActions.ENTITY_KILL);
            });
            transitionRows.add(data.getTrackingRowId());
        }

        private void updateBlockState(long blockRowId, int rolledBack, int action) throws Exception {
            if (blockRowId <= 0) {
                return;
            }

            String tableName = ConfigHandler.prefix + "block";
            String query = "INSERT INTO " + tableName + " (rowid,time,user,wid,x,y,z,type,data,action,rolled_back,version,meta,blockdata) "
                    + "SELECT rowid,time,user,wid,x,y,z,type,data,action," + rolledBack + ",version + 1,meta,blockdata "
                    + "FROM " + tableName + " FINAL WHERE rowid=" + blockRowId + " AND action=" + action;
            int updated = transitionStatement.executeUpdate(query);
            if (updated <= 0 && !blockStateMatches(blockRowId, rolledBack, action)) {
                throw new PermanentTransitionException("Expected one row for entity spawn block rollback state, updated " + updated);
            }
        }

        private boolean insertLocationVersion(UUID uuid, Location location, Integer removedState, UUID replacementUuid) throws Exception {
            EntitySpawnIdentity identity = loadIdentity(entitySpawn.getConnection(), uuid);
            if (identity == null) {
                return false;
            }
            EntitySpawnRecord record = loadRecord(entitySpawn.getConnection(), identity.getRowId());
            if (record == null || (removedState == null && record.isRemoved())) {
                return false;
            }
            insertRecordVersion(record, replacementUuid == null ? record.getUuid() : replacementUuid, location, null, removedState == null ? (record.isRemoved() ? 1 : 0) : removedState);
            return true;
        }

        private boolean insertRevivedVersion(UUID previousUuid, UUID uuid, Location location) throws Exception {
            if (previousUuid == null || uuid == null) {
                return false;
            }
            return insertLocationVersion(previousUuid, location, 0, uuid);
        }

        private void insertTrackingVersion(int trackingRowId, UUID uuid, Location location, byte[] state, int removedState, boolean requireKillMatch, int killRowId, String operation) throws Exception {
            EntitySpawnRecord record = loadRecord(entitySpawn.getConnection(), trackingRowId);
            if (record == null) {
                throw new PermanentTransitionException("Missing entity spawn tracking row for " + operation);
            }
            if (requireKillMatch && record.getKillRowId() != killRowId) {
                throw new PermanentTransitionException("Expected kill row " + killRowId + " for " + operation + ", found " + record.getKillRowId());
            }
            UUID nextUuid = uuid == null ? record.getUuid() : uuid;
            insertRecordVersion(record, nextUuid, location, stateString(state), removedState);
            if (requireKillMatch && !trackingKillStateMatches(trackingRowId, killRowId, removedState == 1, uuid)) {
                throw new PermanentTransitionException("Expected one row for " + operation);
            }
            if (!requireKillMatch && !trackingRowExists(trackingRowId)) {
                throw new PermanentTransitionException("Expected one row for " + operation);
            }
        }

        private void insertRecordVersion(EntitySpawnRecord record, UUID uuid, Location location, String state, int removedState) throws Exception {
            Location nextLocation = location == null ? record.getLocation() : location;
            if (nextLocation == null || nextLocation.getWorld() == null) {
                throw new PermanentTransitionException("Missing world for entity spawn tracking row " + record.getRowId());
            }
            setInsertValues(entitySpawn, record.getRowId(), (int) (System.currentTimeMillis() / 1000L), record.getBlockRowId(), record.getKillRowId(), uuid.toString(), record.getOriginalWorldId(), WorldUtils.getWorldId(nextLocation.getWorld().getName()), record.getOriginalX(), record.getOriginalY(), record.getOriginalZ(), nextLocation.getX(), nextLocation.getY(), nextLocation.getZ(), nextLocation.getYaw(), nextLocation.getPitch(), state, removedState);
            entitySpawn.executeUpdate();
        }

        private String stateString(byte[] state) {
            if (state == null) {
                return null;
            }
            return new String(state, StandardCharsets.UTF_8);
        }

        private boolean exists(UUID uuid) throws Exception {
            exists.setString(1, uuid.toString());
            try (ResultSet resultSet = exists.executeQuery()) {
                return resultSet.next();
            }
        }

        private boolean trackingRowExists(int trackingRowId) throws Exception {
            trackingRowExists.setInt(1, trackingRowId);
            try (ResultSet resultSet = trackingRowExists.executeQuery()) {
                return resultSet.next();
            }
        }

        private boolean trackingKillStateMatches(int trackingRowId, int killRowId, boolean removedState, UUID uuid) throws Exception {
            trackingKillStateMatches.setInt(1, trackingRowId);
            trackingKillStateMatches.setInt(2, killRowId);
            try (ResultSet resultSet = trackingKillStateMatches.executeQuery()) {
                if (!resultSet.next() || (resultSet.getInt("removed") == 1) != removedState) {
                    return false;
                }
                return uuid == null || uuid.toString().equals(resultSet.getString("uuid"));
            }
        }

        private boolean blockStateMatches(long blockRowId, int rolledBack, int action) throws Exception {
            blockStateMatches.setLong(1, blockRowId);
            blockStateMatches.setInt(2, action);
            blockStateMatches.setInt(3, rolledBack);
            try (ResultSet resultSet = blockStateMatches.executeQuery()) {
                return resultSet.next();
            }
        }

        public void afterCommit(boolean committed) {
            List<EntitySpawnData> lifecycleRetries = new ArrayList<>();
            List<EntityContainerRollbackUpdate> combinedRetries = new ArrayList<>();
            Set<UUID> deferredUuids = new HashSet<>();
            for (EntitySpawnData data : lifecycleData) {
                if (!committed || !appliedLifecycleData.contains(data)) {
                    lifecycleRetries.add(data);
                    deferLifecycle(data);
                    if (data.getUuid() != null) {
                        deferredUuids.add(data.getUuid());
                    }
                    if (data.getOperation() == EntitySpawnData.Operation.REVIVED && data.getPreviousUuid() != null) {
                        deferredUuids.add(data.getPreviousUuid());
                    }
                }
            }
            for (Map.Entry<Integer, EntitySpawnData> transition : transitionData.entrySet()) {
                EntitySpawnData data = transition.getValue();
                if (!permanentTransitionRows.contains(transition.getKey()) && (data.getOperation() == EntitySpawnData.Operation.RESTORE || data.getOperation() == EntitySpawnData.Operation.KILL_ROLLBACK) && (!committed || !transitionRows.contains(transition.getKey()))) {
                    deferLifecycle(data);
                    deferredUuids.add(data.getUuid());
                }
            }
            for (Map.Entry<Integer, EntityContainerRollbackUpdate> transition : combinedTransitionData.entrySet()) {
                EntitySpawnData data = transition.getValue().getTransition();
                if (!permanentCombinedTransitionRows.contains(transition.getKey()) && introducesTrackedUuid(data) && (!committed || !combinedTransitionRows.contains(transition.getKey()))) {
                    deferLifecycle(data);
                    deferredUuids.add(data.getUuid());
                }
            }

            try {
                if (committed) {
                    for (LocationConfirmation confirmation : locationConfirmations.values()) {
                        if (deferredUuids.contains(confirmation.uuid)) {
                            continue;
                        }
                        try {
                            EntitySpawnTracking.confirmDatabaseLocation(confirmation.uuid, confirmation.location, confirmation.epoch);
                        }
                        catch (Exception e) {
                            ErrorReporter.report(e);
                        }
                    }
                    for (Map.Entry<UUID, Long> confirmation : verificationConfirmations.entrySet()) {
                        if (deferredUuids.contains(confirmation.getKey())) {
                            continue;
                        }
                        try {
                            EntitySpawnTracking.confirmDatabaseVerification(confirmation.getKey(), confirmation.getValue());
                        }
                        catch (Exception e) {
                            ErrorReporter.report(e);
                        }
                    }
                    for (UUID uuid : missingRows) {
                        if (deferredUuids.contains(uuid)) {
                            continue;
                        }
                        try {
                            EntitySpawnTracking.confirmDatabaseIdentityMissing(uuid);
                        }
                        catch (Exception e) {
                            ErrorReporter.report(e);
                        }
                    }
                }
            }
            finally {
                List<EntitySpawnData> retries = new ArrayList<>();
                List<EntitySpawnData> transitionRetries = new ArrayList<>();
                for (Map.Entry<Integer, EntitySpawnData> transition : transitionData.entrySet()) {
                    int trackingRowId = transition.getKey();
                    if (permanentTransitionRows.contains(trackingRowId)) {
                        EntitySpawnData data = transition.getValue();
                        if (data.getUuid() != null) {
                            try {
                                EntitySpawnTracking.clearTracking(data.getUuid());
                            }
                            catch (Exception e) {
                                ErrorReporter.report(e);
                            }
                        }
                        EntitySpawnRollbackHandler.releaseTrackingRow(trackingRowId);
                    }
                    else if (committed && transitionRows.contains(trackingRowId)) {
                        EntitySpawnRollbackHandler.releaseTrackingRow(trackingRowId);
                    }
                    else {
                        EntitySpawnData data = transition.getValue();
                        retries.add(data);
                        transitionRetries.add(data);
                    }
                }
                for (Map.Entry<Integer, EntityContainerRollbackUpdate> transition : combinedTransitionData.entrySet()) {
                    int trackingRowId = transition.getKey();
                    EntityContainerRollbackUpdate update = transition.getValue();
                    EntitySpawnData data = update.getTransition();
                    if (permanentCombinedTransitionRows.contains(trackingRowId)) {
                        if (data.getUuid() != null) {
                            try {
                                EntitySpawnTracking.clearTracking(data.getUuid());
                            }
                            catch (Exception e) {
                                ErrorReporter.report(e);
                            }
                        }
                        EntitySpawnRollbackHandler.releaseTrackingRow(trackingRowId);
                    }
                    else if (committed && combinedTransitionRows.contains(trackingRowId)) {
                        EntitySpawnRollbackHandler.releaseTrackingRow(trackingRowId);
                    }
                    else {
                        combinedRetries.add(update);
                    }
                }
                retries.addAll(lifecycleRetries);
                try {
                    Queue.queueEntityRetriesFirst(combinedRetries, retries);
                }
                catch (Exception e) {
                    for (EntitySpawnData data : transitionRetries) {
                        try {
                            EntitySpawnRollbackHandler.releaseTrackingRow(data.getTrackingRowId());
                            if (data.getUuid() != null) {
                                EntitySpawnTracking.clearTracking(data.getUuid());
                            }
                        }
                        catch (Exception cleanupException) {
                            e.addSuppressed(cleanupException);
                        }
                    }
                    for (EntityContainerRollbackUpdate update : combinedRetries) {
                        EntitySpawnData data = update.getTransition();
                        try {
                            EntitySpawnRollbackHandler.releaseTrackingRow(data.getTrackingRowId());
                            if (data.getUuid() != null) {
                                EntitySpawnTracking.clearTracking(data.getUuid());
                            }
                        }
                        catch (Exception cleanupException) {
                            e.addSuppressed(cleanupException);
                        }
                    }
                    ErrorReporter.report(e);
                }
                locationConfirmations.clear();
                verificationConfirmations.clear();
                missingRows.clear();
                lifecycleData.clear();
                appliedLifecycleData.clear();
                transitionData.clear();
                transitionRows.clear();
                permanentTransitionRows.clear();
                combinedTransitionData.clear();
                combinedTransitionRows.clear();
                permanentCombinedTransitionRows.clear();
            }
        }

        @Override
        public void close() throws Exception {
            afterCommit(false);
            deferredLifecycleUuids.clear();
            entitySpawn.close();
            exists.close();
            trackingRowExists.close();
            trackingKillStateMatches.close();
            blockStateMatches.close();
            transitionStatement.close();
        }

        private static final class LocationConfirmation {

            private final UUID uuid;
            private final Location location;
            private final long epoch;

            private LocationConfirmation(EntitySpawnData data) {
                uuid = data.getUuid();
                location = data.getLocation();
                epoch = data.getVerificationEpoch();
            }
        }

        private static final class PermanentTransitionException extends SQLException {

            private static final long serialVersionUID = 1L;

            private PermanentTransitionException(String message) {
                super(message);
            }
        }
    }
}
