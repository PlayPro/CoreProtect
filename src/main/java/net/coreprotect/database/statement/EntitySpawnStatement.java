package net.coreprotect.database.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerEntitySpawnUpdates;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.database.EntitySpawnUpdateCoordinator;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.entity.EntityContainerRollbackUpdate;
import net.coreprotect.model.entity.EntityInteractionOrigin;
import net.coreprotect.model.entity.EntitySpawnData;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.model.entity.EntitySpawnRecord;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.WorldUtils;

public final class EntitySpawnStatement {

    private static final int SELECT_BATCH_SIZE = 500;

    private EntitySpawnStatement() {
        throw new IllegalStateException("Database class");
    }

    public static int insert(ConsumerWriteBatch batch, int time, EntitySpawnData data, Location loggedLocation) throws Exception {
        Location currentLocation = data.getLocation();
        return batch.addEntitySpawn(time, null, null, data.getUuid(), WorldUtils.getWorldId(loggedLocation.getWorld().getName()), WorldUtils.getWorldId(currentLocation.getWorld().getName()), loggedLocation.getX(), loggedLocation.getY(), loggedLocation.getZ(), currentLocation.getX(), currentLocation.getY(), currentLocation.getZ(), currentLocation.getYaw(), currentLocation.getPitch(), null, 0);
    }

    public static EntitySpawnIdentity insertIdentity(ConsumerWriteBatch batch, int time, UUID uuid, EntityInteractionOrigin origin, Location currentLocation) throws Exception {
        int rowId = batch.addEntitySpawn(time, null, null, uuid, origin.getWorldId(), WorldUtils.getWorldId(currentLocation.getWorld().getName()), origin.getX(), origin.getY(), origin.getZ(), currentLocation.getX(), currentLocation.getY(), currentLocation.getZ(), currentLocation.getYaw(), currentLocation.getPitch(), null, 0);
        return new EntitySpawnIdentity(rowId, uuid, origin.getWorldId(), origin.getX(), origin.getY(), origin.getZ());
    }

    public static void linkBlock(ConsumerWriteBatch batch, int trackingRowId, long blockRowId) throws Exception {
        batch.linkEntitySpawnBlock(trackingRowId, blockRowId);
    }

    public static void addKillLink(ConsumerWriteBatch batch, String uuid, int killRowId) throws Exception {
        batch.linkEntitySpawnKill(UUID.fromString(uuid), killRowId);
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
        StringBuilder query = new StringBuilder("SELECT uuid FROM ").append(ConfigHandler.prefix).append("entity_spawn WHERE removed=0 AND current_wid=? AND x>=? AND x<? AND z>=? AND z<?");
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
        String columns = "rowid AS id,kill_rowid,uuid,wid,origin_x,origin_y,origin_z,current_wid,x,y,z,yaw,pitch,removed" + (includeState ? ",data" : "");
        String query = "SELECT " + columns + " FROM " + ConfigHandler.prefix + "entity_spawn WHERE " + keyColumn + " IN(" + placeholders + ")";
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
                        byte[] serializedState = DatabaseUtils.getBytes(resultSet, "data");
                        state = serializedState == null || serializedState.length == 0 ? null : EntityStatement.deserializeData(serializedState);
                        if (state != null && state.size() < 4) {
                            state = null;
                        }
                    }
                    EntitySpawnRecord record = new EntitySpawnRecord(rowId, killRowId, UUID.fromString(resultSet.getString("uuid")), resultSet.getInt("wid"), resultSet.getDouble("origin_x"), resultSet.getDouble("origin_y"), resultSet.getDouble("origin_z"), resultSet.getInt("current_wid"), resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z"), resultSet.getFloat("yaw"), resultSet.getFloat("pitch"), resultSet.getInt("removed") == 1, state);
                    records.put(byKillRowId ? killRowId : rowId, record);
                }
            }
        }
    }

    private static List<EntitySpawnIdentity> loadIdentityBatch(Connection connection, List<?> ids, boolean byRowId) throws SQLException {
        StringJoiner placeholders = new StringJoiner(",");
        for (int ignored = 0; ignored < ids.size(); ignored++) {
            placeholders.add("?");
        }

        String keyColumn = byRowId ? "rowid" : "uuid";
        String query = "SELECT rowid AS id,uuid,wid,origin_x,origin_y,origin_z FROM " + ConfigHandler.prefix + "entity_spawn WHERE " + keyColumn + " IN(" + placeholders + ")";
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

    public static final class Updates implements ConsumerEntitySpawnUpdates {

        private final PreparedStatement location;
        private final PreparedStatement removed;
        private final PreparedStatement revived;
        private final PreparedStatement rollback;
        private final PreparedStatement restore;
        private final PreparedStatement killRollback;
        private final PreparedStatement killRestore;
        private final PreparedStatement compositeRollback;
        private final PreparedStatement compositeRestore;
        private final PreparedStatement blockState;
        private final PreparedStatement exists;
        private final PreparedStatement trackingRowExists;
        private final PreparedStatement trackingKillStateMatches;
        private final PreparedStatement blockStateMatches;
        private final Statement transitionStatement;
        private final EntitySpawnUpdateCoordinator coordinator = new EntitySpawnUpdateCoordinator();

        public Updates(Connection connection) throws Exception {
            location = connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET current_wid=?,x=?,y=?,z=?,yaw=?,pitch=? WHERE uuid=? AND removed=0");
            removed = connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET current_wid=?,x=?,y=?,z=?,yaw=?,pitch=?,data=NULL,removed=1 WHERE uuid=? AND removed=0");
            revived = connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET uuid=?,current_wid=?,x=?,y=?,z=?,yaw=?,pitch=?,data=NULL,removed=0 WHERE uuid=? AND removed=1");
            rollback = connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET current_wid=?,x=?,y=?,z=?,yaw=?,pitch=?,data=?,kill_rowid=NULL,removed=1 WHERE rowid=?");
            restore = connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET uuid=?,current_wid=?,x=?,y=?,z=?,yaw=?,pitch=?,data=NULL,kill_rowid=NULL,removed=0 WHERE rowid=?");
            killRollback = connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET uuid=?,current_wid=?,x=?,y=?,z=?,yaw=?,pitch=?,data=NULL,removed=0 WHERE rowid=? AND kill_rowid=?");
            killRestore = connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET current_wid=?,x=?,y=?,z=?,yaw=?,pitch=?,data=NULL,removed=1 WHERE rowid=? AND kill_rowid=?");
            compositeRollback = connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET current_wid=?,x=?,y=?,z=?,yaw=?,pitch=?,data=?,removed=1 WHERE rowid=? AND kill_rowid=?");
            compositeRestore = connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET data=NULL,removed=1 WHERE rowid=? AND kill_rowid=?");
            blockState = connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "block SET rolled_back=? WHERE rowid=? AND action=?");
            exists = connection.prepareStatement("SELECT 1 FROM " + ConfigHandler.prefix + "entity_spawn WHERE uuid=? AND removed=0 LIMIT 1");
            trackingRowExists = connection.prepareStatement("SELECT 1 FROM " + ConfigHandler.prefix + "entity_spawn WHERE rowid=? LIMIT 1");
            trackingKillStateMatches = connection.prepareStatement("SELECT uuid,removed FROM " + ConfigHandler.prefix + "entity_spawn WHERE rowid=? AND kill_rowid=? LIMIT 1");
            blockStateMatches = connection.prepareStatement("SELECT 1 FROM " + ConfigHandler.prefix + "block WHERE rowid=? AND action=? AND rolled_back=? LIMIT 1");
            transitionStatement = connection.createStatement();
        }

        public void apply(EntitySpawnData data) {
            if (!coordinator.begin(data)) {
                return;
            }
            try {
                switch (data.getOperation()) {
                    case VERIFY:
                        if (exists(data.getUuid())) {
                            coordinator.verificationFound(data);
                        }
                        else {
                            coordinator.verificationMissing(data);
                        }
                        break;
                    case LOCATION:
                        setLocation(location, data.getLocation(), 1);
                        location.setString(7, data.getUuid().toString());
                        if (location.executeUpdate() > 0 || exists(data.getUuid())) {
                            coordinator.locationFound(data);
                        }
                        else {
                            coordinator.locationMissing(data);
                        }
                        break;
                    case REMOVED:
                        setLocation(removed, data.getLocation(), 1);
                        removed.setString(7, data.getUuid().toString());
                        removed.executeUpdate();
                        break;
                    case REVIVED:
                        revived.setString(1, data.getUuid().toString());
                        setLocation(revived, data.getLocation(), 2);
                        revived.setString(8, data.getPreviousUuid().toString());
                        if (revived.executeUpdate() > 0 || exists(data.getUuid())) {
                            coordinator.entityFound(data.getUuid());
                        }
                        else {
                            coordinator.entityMissing(data.getUuid());
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
                        coordinator.transitionApplied(data.getTrackingRowId());
                        break;
                    default:
                        break;
                }
                coordinator.applied(data);
            }
            catch (PermanentTransitionException e) {
                if (coordinator.permanentTransitionFailed(data, e)) {
                    Database.acknowledgeRollbackOnlyTransaction();
                }
                ErrorReporter.report(e);
            }
            catch (Exception e) {
                coordinator.failed(data);
                Database.handleWriteFailure(e);
            }
        }

        public void applyCombined(EntityContainerRollbackUpdate update, Database.SavepointOperation rowUpdate) {
            EntitySpawnData data = update.getTransition();
            int trackingRowId = data.getTrackingRowId();
            coordinator.beginCombined(update);
            try {
                Database.executeSavepoint(transitionStatement, "entity_container_transition", () -> {
                    applyCombinedTransition(data);
                    rowUpdate.execute();
                });
                coordinator.combinedApplied(trackingRowId);
            }
            catch (PermanentTransitionException e) {
                if (coordinator.permanentCombinedFailed(data, e)) {
                    Database.acknowledgeRollbackOnlyTransaction();
                }
                ErrorReporter.report(e);
            }
            catch (Exception e) {
                coordinator.combinedFailed(data);
                Database.handleWriteFailure(e);
            }
        }

        public void identityFound(UUID uuid) {
            coordinator.entityFound(uuid);
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
                    coordinator.transitionApplied(data.getTrackingRowId());
                    break;
                default:
                    throw new PermanentTransitionException("Unsupported combined entity spawn transition " + data.getOperation());
            }
        }

        private void applyRollback(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_transition", () -> {
                byte[] serializedState = data.getState();
                Location rollbackLocation = data.getLocation();
                setLocation(rollback, rollbackLocation, 1);
                setNullableBytes(rollback, 7, serializedState);
                rollback.setInt(8, data.getTrackingRowId());
                requireTrackingUpdate(rollback.executeUpdate(), data.getTrackingRowId(), "entity spawn tracking rollback");
                updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_SPAWN);
            });
            coordinator.transitionApplied(data.getTrackingRowId());
        }

        private void applyRestore(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_transition", () -> {
                restore.setString(1, data.getUuid().toString());
                setLocation(restore, data.getLocation(), 2);
                restore.setInt(8, data.getTrackingRowId());
                requireTrackingUpdate(restore.executeUpdate(), data.getTrackingRowId(), "entity spawn tracking restore");
                updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_SPAWN);
            });
            coordinator.entityFound(data.getUuid());
            coordinator.transitionApplied(data.getTrackingRowId());
        }

        private void applyKillRollback(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_kill_transition", () -> {
                killRollback.setString(1, data.getUuid().toString());
                setLocation(killRollback, data.getLocation(), 2);
                killRollback.setInt(8, data.getTrackingRowId());
                killRollback.setInt(9, data.getKillRowId());
                requireTrackingKillUpdate(killRollback.executeUpdate(), data.getTrackingRowId(), data.getKillRowId(), false, data.getUuid(), "tracked entity kill rollback");
                updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_KILL);
            });
            coordinator.entityFound(data.getUuid());
            coordinator.transitionApplied(data.getTrackingRowId());
        }

        private void applyKillRestore(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_kill_transition", () -> {
                setLocation(killRestore, data.getLocation(), 1);
                killRestore.setInt(7, data.getTrackingRowId());
                killRestore.setInt(8, data.getKillRowId());
                requireTrackingKillUpdate(killRestore.executeUpdate(), data.getTrackingRowId(), data.getKillRowId(), true, null, "tracked entity kill restore");
                updateBlockState(data.getBlockRowId(), data.getRolledBack(), LookupActions.ENTITY_KILL);
            });
            coordinator.transitionApplied(data.getTrackingRowId());
        }

        private void applyCompositeRestore(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_composite_transition", () -> {
                compositeRestore.setInt(1, data.getTrackingRowId());
                compositeRestore.setInt(2, data.getKillRowId());
                requireTrackingKillUpdate(compositeRestore.executeUpdate(), data.getTrackingRowId(), data.getKillRowId(), true, null, "tracked entity composite restore");
                updateBlockState(data.getBlockRowId(), 0, LookupActions.ENTITY_SPAWN);
                updateBlockState(data.getPairedBlockRowId(), 0, LookupActions.ENTITY_KILL);
            });
            coordinator.transitionApplied(data.getTrackingRowId());
        }

        private void applyCompositeRollback(EntitySpawnData data) throws Exception {
            Database.executeSavepoint(transitionStatement, "entity_spawn_composite_transition", () -> {
                setLocation(compositeRollback, data.getLocation(), 1);
                setNullableBytes(compositeRollback, 7, data.getState());
                compositeRollback.setInt(8, data.getTrackingRowId());
                compositeRollback.setInt(9, data.getKillRowId());
                requireTrackingKillUpdate(compositeRollback.executeUpdate(), data.getTrackingRowId(), data.getKillRowId(), true, null, "tracked entity composite rollback");
                updateBlockState(data.getBlockRowId(), 1, LookupActions.ENTITY_SPAWN);
                updateBlockState(data.getPairedBlockRowId(), 1, LookupActions.ENTITY_KILL);
            });
            coordinator.transitionApplied(data.getTrackingRowId());
        }

        private void updateBlockState(long blockRowId, int rolledBack, int action) throws Exception {
            blockState.setInt(1, rolledBack);
            blockState.setLong(2, blockRowId);
            blockState.setInt(3, action);
            int updated = blockState.executeUpdate();
            if (updated != 1 && (updated != 0 || !blockStateMatches(blockRowId, rolledBack, action))) {
                throw new PermanentTransitionException("Expected one row for entity spawn block rollback state, updated " + updated);
            }
        }

        private void requireTrackingUpdate(int updated, int trackingRowId, String operation) throws Exception {
            if (updated != 1 && (updated != 0 || !trackingRowExists(trackingRowId))) {
                throw new PermanentTransitionException("Expected one row for " + operation + ", updated " + updated);
            }
        }

        private void requireTrackingKillUpdate(int updated, int trackingRowId, int killRowId, boolean removedState, UUID uuid, String operation) throws Exception {
            if (updated == 1 || (updated == 0 && trackingKillStateMatches(trackingRowId, killRowId, removedState, uuid))) {
                return;
            }
            throw new PermanentTransitionException("Expected one row for " + operation + ", updated " + updated);
        }

        private void setLocation(PreparedStatement statement, Location value, int offset) throws Exception {
            statement.setInt(offset, WorldUtils.getWorldId(value.getWorld().getName()));
            statement.setDouble(offset + 1, value.getX());
            statement.setDouble(offset + 2, value.getY());
            statement.setDouble(offset + 3, value.getZ());
            statement.setFloat(offset + 4, value.getYaw());
            statement.setFloat(offset + 5, value.getPitch());
        }

        private void setNullableBytes(PreparedStatement statement, int index, byte[] value) throws Exception {
            if (value == null) {
                statement.setNull(index, Types.BLOB);
            }
            else {
                statement.setBytes(index, value);
            }
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
            coordinator.afterCommit(committed);
        }

        public void afterDiscard() {
            coordinator.afterDiscard();
        }

        @Override
        public void close() throws Exception {
            afterCommit(false);
            coordinator.clearDeferred();
            location.close();
            removed.close();
            revived.close();
            rollback.close();
            restore.close();
            killRollback.close();
            killRestore.close();
            compositeRollback.close();
            compositeRestore.close();
            blockState.close();
            exists.close();
            trackingRowExists.close();
            trackingKillStateMatches.close();
            blockStateMatches.close();
            transitionStatement.close();
        }

        private static final class PermanentTransitionException extends SQLException {

            private static final long serialVersionUID = 1L;

            private PermanentTransitionException(String message) {
                super(message);
            }
        }
    }
}
