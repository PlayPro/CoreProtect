package net.coreprotect.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.database.statement.UserStatement.Identity;
import net.coreprotect.utility.StringUtils;

public final class RelationalConsumerWriteBatch implements ConsumerWriteBatch {

    private static final int SIGN_BATCH = 0;
    private static final int BLOCK_BATCH = 1;
    private static final int CONTAINER_BATCH = 3;
    private static final int ENTITY_CONTAINER_BATCH = 4;
    private static final int ITEM_BATCH = 5;
    private static final int WORLD_BATCH = 6;
    private static final int CHAT_BATCH = 7;
    private static final int COMMAND_BATCH = 8;
    private static final int SESSION_BATCH = 9;
    private static final int MATERIAL_BATCH = 11;
    private static final int ART_BATCH = 12;
    private static final int ENTITY_MAP_BATCH = 13;
    private static final int BLOCK_DATA_BATCH = 14;
    private static final int ENTITY_KILL_LINK_BATCH = 15;
    private static final int BATCH_SIZE = 1000;
    private static final int INITIAL_DUCKDB_BLOCK_ID_RESERVATION = 256;
    private static final int MAXIMUM_DUCKDB_BLOCK_ID_RESERVATION = 65536;
    private static final long[] EMPTY_ROW_IDS = new long[0];

    private final Connection connection;
    private final DatabaseType databaseType;
    private final Statement transactionStatement;
    private final PreparedStatement[] batchStatements = new PreparedStatement[16];
    private final int[] pendingBatchCounts = new int[batchStatements.length];
    private final List<PreparedStatement> statements = new ArrayList<>();
    private final Map<String, Identity> pendingUsersByName = new HashMap<>();
    private final List<UserCacheUpdate> userCacheUpdates = new ArrayList<>();

    private PreparedStatement blockReturningStatement;
    private PreparedStatement skullStatement;
    private PreparedStatement entityStatement;
    private PreparedStatement entitySpawnStatement;
    private PreparedStatement entitySpawnBlockLinkStatement;
    private PreparedStatement entitySpawnCheckpointStatement;
    private PreparedStatement entitySpawnCheckpointStateStatement;
    private PreparedStatement entityInteractionStatement;
    private PreparedStatement usernameHistoryStatement;
    private PreparedStatement usernameHistoryInsertStatement;
    private PreparedStatement databaseLockStatement;
    private PreparedStatement duckDBBlockRowIdStatement;
    private DuckDBAppender duckDBBlockAppender;
    private long[] duckDBBlockRowIds = EMPTY_ROW_IDS;
    private int duckDBBlockRowIdIndex;
    private int duckDBBlockIdReservationSize = INITIAL_DUCKDB_BLOCK_ID_RESERVATION;
    private DuckDBSpatialIndex.Transaction duckDBSpatialIndex;
    private EntitySpawnStatement.Updates entitySpawnUpdates;
    private SQLException batchFailure;
    private boolean commitAttempted;
    private Throwable commitFailure;

    public RelationalConsumerWriteBatch(Connection connection, DatabaseType databaseType) throws SQLException {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.databaseType = Objects.requireNonNull(databaseType, "databaseType");
        transactionStatement = connection.createStatement();
    }

    @Override
    public void begin() throws Exception {
        clearProcessedEntityRemovals();
        commitAttempted = false;
        if (databaseType.isDuckDB()) {
            duckDBSpatialIndex = DuckDBSpatialIndex.begin(connection, ConfigHandler.prefix);
        }
        Database.beginTransaction(transactionStatement, databaseType);
    }

    @Override
    public boolean commit() throws Exception {
        clearProcessedEntityRemovals();
        commitAttempted = false;
        commitFailure = null;
        boolean writesCommitted = false;
        try {
            finishDuckDBBlockAppender();
            if (batchFailure != null) {
                throw batchFailure;
            }
            boolean acknowledgedRollback = Database.isRollbackOnlyTransactionAcknowledged();
            if (!Database.isTransactionRollbackOnly()) {
                for (int index = 0; index < batchStatements.length; index++) {
                    flushPendingBatch(index);
                }
                if (duckDBSpatialIndex != null) {
                    duckDBSpatialIndex.flush(connection);
                }
            }
            boolean rollbackOnly = Database.isTransactionRollbackOnly();
            boolean committed = Database.commitTransactionChecked(transactionStatement, databaseType, () -> commitAttempted = true);
            if (!committed) {
                boolean rolledBack = !rollbackOnly && Database.rollbackTransaction(transactionStatement, databaseType);
                if (commitAttempted || !rolledBack) {
                    abandonDuckDBSpatialIndex();
                }
                else {
                    duckDBSpatialIndex = null;
                }
                return acknowledgedRollback && rolledBack;
            }
            if (acknowledgedRollback) {
                duckDBSpatialIndex = null;
                return true;
            }
            writesCommitted = true;
            publishUserCacheUpdates();
            if (duckDBSpatialIndex != null) {
                duckDBSpatialIndex.publish();
                duckDBSpatialIndex = null;
            }
            return true;
        }
        catch (Exception exception) {
            commitFailure = exception;
            Database.reportDatabaseFailure(exception);
            boolean rolledBack = Database.rollbackTransaction(transactionStatement, databaseType);
            if (commitAttempted || !rolledBack) {
                abandonDuckDBSpatialIndex();
            }
            else {
                duckDBSpatialIndex = null;
            }
            return false;
        }
        finally {
            clearUserCacheUpdates();
            if (!writesCommitted) {
                clearPendingBatches();
            }
        }
    }

    @Override
    public boolean wasCommitAttempted() {
        return commitAttempted;
    }

    @Override
    public Throwable getCommitFailure() {
        return commitFailure;
    }

    @Override
    public void rollback() {
        clearProcessedEntityRemovals();
        try {
            try {
                finishDuckDBBlockAppender();
            }
            catch (Exception exception) {
                Database.reportDatabaseFailure(exception);
            }
            if (Database.rollbackTransaction(transactionStatement, databaseType)) {
                duckDBSpatialIndex = null;
            }
            else {
                abandonDuckDBSpatialIndex();
            }
        }
        finally {
            clearUserCacheUpdates();
            clearPendingBatches();
        }
    }

    private void abandonDuckDBSpatialIndex() {
        if (duckDBSpatialIndex != null) {
            duckDBSpatialIndex.abandon();
            duckDBSpatialIndex = null;
        }
    }

    @Override
    public void executeAtomically(String name, Database.SavepointOperation operation) throws Exception {
        int previousUpdateCount = userCacheUpdates.size();
        try {
            Database.executeSavepoint(transactionStatement, name, operation);
        }
        catch (Exception exception) {
            if (previousUpdateCount != userCacheUpdates.size()) {
                userCacheUpdates.subList(previousUpdateCount, userCacheUpdates.size()).clear();
                pendingUsersByName.clear();
                userCacheUpdates.forEach(this::applyUserCacheUpdate);
            }
            throw exception;
        }
    }

    @Override
    public int resolveUserId(String user, String uuid) throws Exception {
        user = StringUtils.normalizeUsername(user);
        uuid = UserStatement.normalizeUuid(uuid);
        if (uuid == null) {
            String cacheKey = user.toLowerCase(Locale.ROOT);
            Identity pending = pendingUsersByName.get(cacheKey);
            if (pending != null) {
                return pending.getId();
            }
            if (!pendingUsersByName.containsKey(cacheKey)) {
                Integer cachedId = ConfigHandler.playerIdCache.get(cacheKey);
                if (cachedId != null && cachedId > 0) {
                    return cachedId;
                }
            }
        }

        Identity identity = UserStatement.findIdentity(connection, user, uuid);
        if (identity == null || (uuid != null && identity.getUuid() == null)) {
            identity = UserStatement.storeIdentity(connection, identity, user, uuid, (int) (System.currentTimeMillis() / 1000L));
            stageUserCacheUpdate(null, identity);
        }
        else if (uuid == null) {
            stageUserCacheUpdate(null, identity);
        }
        return identity.getId();
    }

    @Override
    public void recordUsername(String suppliedUser, String suppliedUuid, int retainHistory, int time) throws Exception {
        String user = StringUtils.normalizeUsername(suppliedUser);
        String uuid = UserStatement.normalizeUuid(suppliedUuid);
        if (user == null || user.isEmpty() || uuid == null || ConfigHandler.isBlacklisted(user)) {
            return;
        }

        executeAtomically("username_history", () -> {
            Identity previous = UserStatement.findIdentity(connection, user, uuid);
            boolean changed = previous == null || previous.getUuid() == null || !user.equalsIgnoreCase(previous.getName());
            Identity identity = changed ? UserStatement.storeIdentity(connection, previous, user, uuid, time) : previous;
            if (retainHistory == 1 && (changed || !hasUsernameHistory(user, uuid))) {
                PreparedStatement statement = usernameHistoryInsertStatement();
                statement.setInt(1, time);
                statement.setString(2, uuid);
                statement.setString(3, user);
                statement.executeUpdate();
            }
            stageUserCacheUpdate(previous, identity);
        });
    }

    private boolean hasUsernameHistory(String user, String uuid) throws SQLException {
        PreparedStatement statement = usernameHistoryStatement();
        statement.setString(1, uuid);
        statement.setString(2, user);
        try (ResultSet rows = statement.executeQuery()) {
            return rows.next();
        }
    }

    private void stageUserCacheUpdate(Identity previous, Identity current) {
        UserCacheUpdate update = new UserCacheUpdate(previous, current);
        userCacheUpdates.add(update);
        applyUserCacheUpdate(update);
    }

    private void applyUserCacheUpdate(UserCacheUpdate update) {
        String key = update.current.getName().toLowerCase(Locale.ROOT);
        if (update.previous != null) {
            String previousKey = update.previous.getName().toLowerCase(Locale.ROOT);
            Identity pending = pendingUsersByName.get(previousKey);
            if (!key.equals(previousKey) && (pending == null || pending.getId() == update.previous.getId())) {
                pendingUsersByName.put(previousKey, null);
            }
        }
        pendingUsersByName.put(key, update.current);
    }

    private void publishUserCacheUpdates() {
        for (UserCacheUpdate update : userCacheUpdates) {
            UserStatement.evictPreviousName(update.previous, update.current);
        }
        for (Identity identity : pendingUsersByName.values()) {
            if (identity != null) {
                UserStatement.publishIdentity(identity);
            }
        }
    }

    private void clearUserCacheUpdates() {
        userCacheUpdates.clear();
        pendingUsersByName.clear();
    }

    private static final class UserCacheUpdate {

        private final Identity previous;
        private final Identity current;

        private UserCacheUpdate(Identity previous, Identity current) {
            this.previous = previous;
            this.current = current;
        }
    }

    @Override
    public void updateDatabaseLock(int status, int time) throws Exception {
        PreparedStatement statement = databaseLockStatement();
        statement.setInt(1, status);
        statement.setInt(2, time);
        statement.executeUpdate();
    }

    @Override
    public void addReference(ReferenceKind kind, int batchCount, int id, String value) throws Exception {
        switch (kind) {
            case ART:
                addReference(Database.ART, ART_BATCH, id, value);
                break;
            case BLOCK_DATA:
                addReference(Database.BLOCKDATA, BLOCK_DATA_BATCH, id, value);
                break;
            case ENTITY:
                addReference(Database.ENTITY_MAP, ENTITY_MAP_BATCH, id, value);
                break;
            case MATERIAL:
                addReference(Database.MATERIAL, MATERIAL_BATCH, id, value);
                break;
            case WORLD:
                addReference(Database.WORLD, WORLD_BATCH, id, value);
                break;
            default:
                throw new IllegalArgumentException("Unsupported reference kind " + kind);
        }
    }

    private void addReference(int type, int index, int id, String value) throws SQLException {
        PreparedStatement statement = batchStatement(type, index);
        statement.setInt(1, id);
        statement.setString(2, value);
        addBatch(index);
    }

    @Override
    public void addBlock(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws Exception {
        if (databaseType.isDuckDB()) {
            appendDuckDBBlock(time, userId, worldId, x, y, z, type, data, meta, blockData, action, rolledBack);
            return;
        }
        PreparedStatement statement = batchStatement(Database.BLOCK, BLOCK_BATCH);
        setBlock(statement, time, userId, worldId, x, y, z, type, data, meta, blockData, action, rolledBack);
        addBatch(BLOCK_BATCH);
    }

    @Override
    public long addBlockReturningId(int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws Exception {
        if (databaseType.isDuckDB()) {
            flushDuckDBBlockAppender();
            resetDuckDBBlockRowIds();
        }
        if (blockReturningStatement == null) {
            blockReturningStatement = own(required(Database.prepareStatement(connection, Database.BLOCK, true), "block insert"));
        }
        setBlock(blockReturningStatement, time, userId, worldId, x, y, z, type, data, meta, blockData, action, rolledBack);
        long rowId = executeReturningId(blockReturningStatement, "block insert");
        trackDuckDBBlock(rowId, worldId, x, z);
        return rowId;
    }

    @Override
    public int addSkull(int time, String owner, String skin) throws Exception {
        if (skullStatement == null) {
            skullStatement = own(required(Database.prepareStatement(connection, Database.SKULL, true), "skull insert"));
        }
        skullStatement.setInt(1, time);
        skullStatement.setString(2, owner);
        skullStatement.setString(3, skin);
        return Math.toIntExact(executeReturningId(skullStatement, "skull insert"));
    }

    @Override
    public void addContainer(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, int data, int amount, byte[] metadata, int action, int rolledBack) throws Exception {
        PreparedStatement statement = batchStatement(Database.CONTAINER, CONTAINER_BATCH);
        statement.setInt(1, time);
        statement.setInt(2, userId);
        statement.setInt(3, worldId);
        statement.setInt(4, x);
        statement.setInt(5, y);
        statement.setInt(6, z);
        statement.setInt(7, type);
        statement.setInt(8, data);
        statement.setInt(9, amount);
        statement.setObject(10, metadata);
        statement.setInt(11, action);
        statement.setInt(12, rolledBack);
        addBatch(CONTAINER_BATCH);
        trackDuckDBGenerated("container", worldId, x, z);
    }

    @Override
    public void addEntityContainer(int batchCount, int time, int userId, int entitySpawnRowId, int worldId, int x, int y, int z, int type, int data, int amount, byte[] metadata, int action, int rolledBack) throws Exception {
        PreparedStatement statement = batchStatement(Database.ENTITY_CONTAINER, ENTITY_CONTAINER_BATCH);
        statement.setInt(1, time);
        statement.setInt(2, userId);
        statement.setInt(3, entitySpawnRowId);
        statement.setInt(4, worldId);
        statement.setInt(5, x);
        statement.setInt(6, y);
        statement.setInt(7, z);
        statement.setInt(8, type);
        statement.setInt(9, data);
        statement.setInt(10, amount);
        statement.setObject(11, metadata);
        statement.setInt(12, action);
        statement.setInt(13, rolledBack);
        addBatch(ENTITY_CONTAINER_BATCH);
        trackDuckDBGenerated("entity_container", worldId, x, z, entitySpawnRowId);
    }

    @Override
    public void addItem(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, byte[] data, int amount, int action, int rolledBack) throws Exception {
        PreparedStatement statement = batchStatement(Database.ITEM, ITEM_BATCH);
        statement.setInt(1, time);
        statement.setInt(2, userId);
        statement.setInt(3, worldId);
        statement.setInt(4, x);
        statement.setInt(5, y);
        statement.setInt(6, z);
        statement.setInt(7, type);
        statement.setObject(8, data);
        statement.setInt(9, amount);
        statement.setInt(10, action);
        statement.setInt(11, rolledBack);
        addBatch(ITEM_BATCH);
        trackDuckDBGenerated("item", worldId, x, z);
    }

    @Override
    public void addChat(int batchCount, long time, int userId, int worldId, int x, int y, int z, String message) throws Exception {
        PreparedStatement statement = batchStatement(Database.CHAT, CHAT_BATCH);
        setMessage(statement, time, userId, worldId, x, y, z, message);
        addBatch(CHAT_BATCH);
        trackDuckDBGenerated("chat", worldId, x, z);
    }

    @Override
    public void addCommand(int batchCount, long time, int userId, int worldId, int x, int y, int z, String message) throws Exception {
        PreparedStatement statement = batchStatement(Database.COMMAND, COMMAND_BATCH);
        setMessage(statement, time, userId, worldId, x, y, z, message);
        addBatch(COMMAND_BATCH);
        trackDuckDBGenerated("command", worldId, x, z);
    }

    @Override
    public void addSession(int batchCount, int time, int userId, int worldId, int x, int y, int z, int action) throws Exception {
        PreparedStatement statement = batchStatement(Database.SESSION, SESSION_BATCH);
        statement.setInt(1, time);
        statement.setInt(2, userId);
        statement.setInt(3, worldId);
        statement.setInt(4, x);
        statement.setInt(5, y);
        statement.setInt(6, z);
        statement.setInt(7, action);
        addBatch(SESSION_BATCH);
        trackDuckDBGenerated("session", worldId, x, z);
    }

    @Override
    public void addSign(int batchCount, int time, int userId, int worldId, int x, int y, int z, int action, int color, int colorSecondary, int data, int waxed, int face, String[] lines) throws Exception {
        if (lines.length != 8) {
            throw new IllegalArgumentException("Sign data must contain eight lines");
        }
        PreparedStatement statement = batchStatement(Database.SIGN, SIGN_BATCH);
        statement.setInt(1, time);
        statement.setInt(2, userId);
        statement.setInt(3, worldId);
        statement.setInt(4, x);
        statement.setInt(5, y);
        statement.setInt(6, z);
        statement.setInt(7, action);
        statement.setInt(8, color);
        statement.setInt(9, colorSecondary);
        statement.setInt(10, data);
        statement.setInt(11, waxed);
        statement.setInt(12, face);
        for (int index = 0; index < lines.length; index++) {
            statement.setString(13 + index, lines[index]);
        }
        addBatch(SIGN_BATCH);
        trackDuckDBGenerated("sign", worldId, x, z);
    }

    @Override
    public int addEntity(int time, byte[] data) throws Exception {
        if (entityStatement == null) {
            entityStatement = own(required(Database.prepareStatement(connection, Database.ENTITY, true), "entity insert"));
        }
        entityStatement.setInt(1, time);
        if (databaseType.isDuckDB()) {
            setDuckDBEntityData(entityStatement, 2, data);
        }
        else {
            entityStatement.setObject(2, data);
        }
        return Math.toIntExact(executeReturningId(entityStatement, "entity insert"));
    }

    @Override
    public int addEntitySpawn(int time, Long blockRowId, Integer killRowId, UUID uuid, int originWorldId, int currentWorldId, double originX, double originY, double originZ, double currentX, double currentY, double currentZ, float yaw, float pitch, byte[] data, int removed) throws Exception {
        PreparedStatement statement = entitySpawnStatement();
        statement.setInt(1, time);
        setNullableLong(statement, 2, blockRowId);
        setNullableInt(statement, 3, killRowId);
        statement.setString(4, uuid.toString());
        statement.setInt(5, originWorldId);
        statement.setInt(6, currentWorldId);
        statement.setDouble(7, originX);
        statement.setDouble(8, originY);
        statement.setDouble(9, originZ);
        statement.setDouble(10, currentX);
        statement.setDouble(11, currentY);
        statement.setDouble(12, currentZ);
        statement.setFloat(13, yaw);
        statement.setFloat(14, pitch);
        if (databaseType.isDuckDB()) {
            setDuckDBEntityData(statement, 15, data);
        }
        else if (data == null) {
            statement.setNull(15, Types.BLOB);
        }
        else {
            statement.setBytes(15, data);
        }
        statement.setInt(16, removed);
        return Math.toIntExact(executeReturningId(statement, "entity spawn insert"));
    }

    @Override
    public void linkEntitySpawnBlock(int trackingRowId, long blockRowId) throws Exception {
        if (entitySpawnBlockLinkStatement == null) {
            entitySpawnBlockLinkStatement = own(connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET block_rowid=? WHERE rowid=? AND block_rowid IS NULL"));
        }
        entitySpawnBlockLinkStatement.setLong(1, blockRowId);
        entitySpawnBlockLinkStatement.setInt(2, trackingRowId);
        if (entitySpawnBlockLinkStatement.executeUpdate() != 1) {
            throw new SQLException("Entity spawn tracking row did not link to its block row");
        }
    }

    @Override
    public void linkEntitySpawnKill(UUID uuid, int killRowId) throws Exception {
        PreparedStatement statement = batchStatements[ENTITY_KILL_LINK_BATCH];
        if (statement == null) {
            statement = own(connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET kill_rowid=? WHERE uuid=?"));
            batchStatements[ENTITY_KILL_LINK_BATCH] = statement;
        }
        statement.setInt(1, killRowId);
        statement.setString(2, uuid.toString());
        addBatch(ENTITY_KILL_LINK_BATCH);
    }

    @Override
    public boolean checkpointEntitySpawn(int trackingRowId, int worldId, double x, double y, double z, float yaw, float pitch) throws Exception {
        if (entitySpawnCheckpointStatement == null) {
            entitySpawnCheckpointStatement = own(connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET current_wid=?,x=?,y=?,z=?,yaw=?,pitch=? WHERE rowid=? AND removed=0"));
        }
        entitySpawnCheckpointStatement.setInt(1, worldId);
        entitySpawnCheckpointStatement.setDouble(2, x);
        entitySpawnCheckpointStatement.setDouble(3, y);
        entitySpawnCheckpointStatement.setDouble(4, z);
        entitySpawnCheckpointStatement.setFloat(5, yaw);
        entitySpawnCheckpointStatement.setFloat(6, pitch);
        entitySpawnCheckpointStatement.setInt(7, trackingRowId);
        int updated = entitySpawnCheckpointStatement.executeUpdate();
        if (updated == 1) {
            return true;
        }
        if (updated > 1) {
            throw new SQLException("Entity interaction tracking row is ambiguous: " + trackingRowId);
        }

        if (entitySpawnCheckpointStateStatement == null) {
            entitySpawnCheckpointStateStatement = own(connection.prepareStatement("SELECT removed FROM " + ConfigHandler.prefix + "entity_spawn WHERE rowid=?"));
        }
        entitySpawnCheckpointStateStatement.setInt(1, trackingRowId);
        try (ResultSet resultSet = entitySpawnCheckpointStateStatement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Entity interaction tracking row is missing: " + trackingRowId);
            }
            int removed = resultSet.getInt("removed");
            if (resultSet.wasNull() || (removed != 0 && removed != 1)) {
                throw new SQLException("Entity interaction tracking row has invalid lifecycle state: " + trackingRowId);
            }
            if (resultSet.next()) {
                throw new SQLException("Entity interaction tracking row is ambiguous: " + trackingRowId);
            }
            return removed == 0;
        }
    }

    @Override
    public void addEntityInteraction(int time, int userId, int entitySpawnRowId, int worldId, int x, int y, int z, int type, int action, byte[] metadata, int rolledBack) throws Exception {
        if (entityInteractionStatement == null) {
            entityInteractionStatement = own(required(Database.prepareStatement(connection, Database.ENTITY_INTERACTION, false), "entity interaction insert"));
        }
        entityInteractionStatement.setInt(1, time);
        entityInteractionStatement.setInt(2, userId);
        entityInteractionStatement.setInt(3, entitySpawnRowId);
        entityInteractionStatement.setInt(4, worldId);
        entityInteractionStatement.setInt(5, x);
        entityInteractionStatement.setInt(6, y);
        entityInteractionStatement.setInt(7, z);
        entityInteractionStatement.setInt(8, type);
        entityInteractionStatement.setInt(9, action);
        if (metadata == null) {
            entityInteractionStatement.setNull(10, Types.BLOB);
        }
        else {
            entityInteractionStatement.setBytes(10, metadata);
        }
        entityInteractionStatement.setInt(11, rolledBack);
        if (entityInteractionStatement.executeUpdate() != 1) {
            throw new SQLException("Entity interaction insert did not insert one row");
        }
        trackDuckDBGenerated("entity_interaction", worldId, x, z, entitySpawnRowId);
    }

    @Override
    public void updateRolledBack(int target, int rolledBack, List<Long> rowIds) throws Exception {
        Database.performRolledBackUpdateChecked(transactionStatement, rolledBack, rowIds, target);
    }

    @Override
    public ConsumerEntitySpawnUpdates entitySpawnUpdates() throws Exception {
        if (entitySpawnUpdates == null) {
            entitySpawnUpdates = new EntitySpawnStatement.Updates(connection, this, databaseType);
        }
        return entitySpawnUpdates;
    }

    private void clearProcessedEntityRemovals() {
        if (entitySpawnUpdates != null) {
            entitySpawnUpdates.clearProcessedRemovals();
        }
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        try {
            finishDuckDBBlockAppender();
        }
        catch (Exception exception) {
            failure = exception;
        }
        if (entitySpawnUpdates != null) {
            try {
                entitySpawnUpdates.close();
            }
            catch (Exception exception) {
                failure = addFailure(failure, exception);
            }
        }
        for (PreparedStatement statement : statements) {
            try {
                statement.close();
            }
            catch (Exception exception) {
                failure = addFailure(failure, exception);
            }
        }
        try {
            transactionStatement.close();
        }
        catch (Exception exception) {
            failure = addFailure(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private PreparedStatement batchStatement(int type, int index) throws SQLException {
        PreparedStatement statement = batchStatements[index];
        if (statement == null) {
            statement = own(required(Database.prepareStatement(connection, type, false), "batched insert"));
            batchStatements[index] = statement;
        }
        return statement;
    }

    private PreparedStatement entitySpawnStatement() throws SQLException {
        // DuckDB closes the prepared statement after a constraint violation.
        if (entitySpawnStatement == null || (databaseType.isDuckDB() && entitySpawnStatement.isClosed())) {
            String sql = "INSERT INTO " + ConfigHandler.prefix + "entity_spawn (time,block_rowid,kill_rowid,uuid,wid,current_wid,origin_x,origin_y,origin_z,x,y,z,yaw,pitch,data,removed) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            entitySpawnStatement = own(prepare(sql, true));
        }
        return entitySpawnStatement;
    }

    private static void setDuckDBEntityData(PreparedStatement statement, int index, byte[] data) throws SQLException {
        if (data == null) {
            statement.setNull(index, Types.BLOB);
        }
        else {
            statement.setBytes(index, data);
        }
    }

    private PreparedStatement usernameHistoryStatement() throws SQLException {
        if (usernameHistoryStatement == null) {
            usernameHistoryStatement = own(connection.prepareStatement("SELECT rowid FROM " + ConfigHandler.prefix + "username_log WHERE uuid=? AND " + databaseType.getUserColumn() + "=? LIMIT 1 OFFSET 0"));
        }
        return usernameHistoryStatement;
    }

    private PreparedStatement usernameHistoryInsertStatement() throws SQLException {
        if (usernameHistoryInsertStatement == null) {
            usernameHistoryInsertStatement = own(connection.prepareStatement("INSERT INTO " + ConfigHandler.prefix + "username_log (time,uuid," + databaseType.getUserColumn() + ") VALUES (?,?,?)"));
        }
        return usernameHistoryInsertStatement;
    }

    private PreparedStatement databaseLockStatement() throws SQLException {
        if (databaseLockStatement == null) {
            databaseLockStatement = own(connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "database_lock SET status=?,time=? WHERE rowid=1"));
        }
        return databaseLockStatement;
    }

    private void appendDuckDBBlock(int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws SQLException {
        long rowId = nextDuckDBBlockRowId();
        if (duckDBBlockAppender == null) {
            DuckDBConnection duckDBConnection = connection.unwrap(DuckDBConnection.class);
            duckDBBlockAppender = duckDBConnection.createAppender(DuckDBConnection.DEFAULT_SCHEMA, ConfigHandler.prefix + "block");
        }

        duckDBBlockAppender.beginRow()
                .append(rowId)
                .append(time)
                .append(userId)
                .append(worldId)
                .append(x)
                .append(y)
                .append(z)
                .append(type)
                .append(data);
        appendNullable(duckDBBlockAppender, meta);
        appendNullable(duckDBBlockAppender, blockData);
        duckDBBlockAppender.append((byte) action)
                .append((byte) rolledBack)
                .endRow();
        trackDuckDBBlock(rowId, worldId, x, z);
    }

    private void trackDuckDBBlock(long rowId, int worldId, int x, int z) throws SQLException {
        if (duckDBSpatialIndex != null) {
            duckDBSpatialIndex.addBlock(rowId, worldId, x, z);
        }
    }

    private void trackDuckDBGenerated(String table, int worldId, int x, int z) {
        trackDuckDBGenerated(table, worldId, x, z, null);
    }

    private void trackDuckDBGenerated(String table, int worldId, int x, int z, Integer entitySpawnRowId) {
        if (duckDBSpatialIndex != null) {
            duckDBSpatialIndex.addGenerated(table, worldId, x, z, entitySpawnRowId);
        }
    }

    private long nextDuckDBBlockRowId() throws SQLException {
        if (duckDBBlockRowIdIndex >= duckDBBlockRowIds.length) {
            reserveDuckDBBlockRowIds();
        }
        return duckDBBlockRowIds[duckDBBlockRowIdIndex++];
    }

    private void reserveDuckDBBlockRowIds() throws SQLException {
        flushDuckDBBlockAppender();
        if (duckDBBlockRowIdStatement == null) {
            String sequence = ConfigHandler.prefix + "block_rowid_seq";
            duckDBBlockRowIdStatement = own(connection.prepareStatement("SELECT nextval('" + sequence + "') FROM range(?)"));
        }

        int reservationSize = duckDBBlockIdReservationSize;
        long[] rowIds = new long[reservationSize];
        duckDBBlockRowIdStatement.setInt(1, reservationSize);
        try (ResultSet resultSet = duckDBBlockRowIdStatement.executeQuery()) {
            for (int index = 0; index < reservationSize; index++) {
                if (!resultSet.next()) {
                    throw new SQLException("DuckDB block row id reservation returned too few values");
                }
                rowIds[index] = resultSet.getLong(1);
            }
            if (resultSet.next()) {
                throw new SQLException("DuckDB block row id reservation returned too many values");
            }
        }
        duckDBBlockRowIds = rowIds;
        duckDBBlockRowIdIndex = 0;
        duckDBBlockIdReservationSize = Math.min(MAXIMUM_DUCKDB_BLOCK_ID_RESERVATION, reservationSize * 2);
    }

    private void flushDuckDBBlockAppender() throws SQLException {
        if (duckDBBlockAppender != null) {
            duckDBBlockAppender.flush();
        }
    }

    private void finishDuckDBBlockAppender() throws SQLException {
        DuckDBAppender appender = duckDBBlockAppender;
        duckDBBlockAppender = null;
        resetDuckDBBlockRowIds();
        if (appender == null) {
            return;
        }

        SQLException failure = null;
        try {
            appender.flush();
        }
        catch (SQLException exception) {
            failure = exception;
        }
        try {
            appender.close();
        }
        catch (SQLException exception) {
            if (failure == null) {
                failure = exception;
            }
            else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void resetDuckDBBlockRowIds() {
        duckDBBlockRowIds = EMPTY_ROW_IDS;
        duckDBBlockRowIdIndex = 0;
        duckDBBlockIdReservationSize = INITIAL_DUCKDB_BLOCK_ID_RESERVATION;
    }

    private static void appendNullable(DuckDBAppender appender, byte[] value) throws SQLException {
        if (value == null) {
            appender.appendNull();
        }
        else {
            appender.append(value);
        }
    }

    private PreparedStatement prepare(String sql, boolean keys) throws SQLException {
        if (!keys) {
            return connection.prepareStatement(sql);
        }
        if (Database.hasReturningKeys()) {
            return connection.prepareStatement(sql + " RETURNING rowid");
        }
        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }

    private PreparedStatement own(PreparedStatement statement) {
        statements.add(statement);
        return statement;
    }

    private static PreparedStatement required(PreparedStatement statement, String operation) throws SQLException {
        if (statement == null) {
            throw new SQLException("Unable to prepare " + operation);
        }
        return statement;
    }

    private void addBatch(int index) throws SQLException {
        if (batchFailure != null) {
            throw batchFailure;
        }
        try {
            batchStatements[index].addBatch();
            if (++pendingBatchCounts[index] == BATCH_SIZE) {
                flushPendingBatch(index);
            }
        }
        catch (SQLException exception) {
            batchFailure = exception;
            throw exception;
        }
    }

    private void flushPendingBatch(int index) throws SQLException {
        if (pendingBatchCounts[index] > 0) {
            batchStatements[index].executeBatch();
            pendingBatchCounts[index] = 0;
        }
    }

    private void clearPendingBatches() {
        batchFailure = null;
        for (int index = 0; index < batchStatements.length; index++) {
            pendingBatchCounts[index] = 0;
            PreparedStatement statement = batchStatements[index];
            if (statement == null) {
                continue;
            }
            try {
                statement.clearBatch();
            }
            catch (SQLException exception) {
                batchStatements[index] = null;
                try {
                    statement.close();
                    statements.remove(statement);
                }
                catch (SQLException closeException) {
                    exception.addSuppressed(closeException);
                }
                Database.reportDatabaseFailure(exception);
            }
        }
    }

    private static long executeReturningId(PreparedStatement statement, String operation) throws Exception {
        if (Database.hasReturningKeys()) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(operation + " did not return a row id");
                }
                return resultSet.getLong(1);
            }
        }
        int updated = statement.executeUpdate();
        if (updated != 1) {
            throw new SQLException("Expected one row for " + operation + ", updated " + updated);
        }
        try (ResultSet resultSet = statement.getGeneratedKeys()) {
            if (!resultSet.next()) {
                throw new SQLException(operation + " did not generate a row id");
            }
            return resultSet.getLong(1);
        }
    }

    private static void setBlock(PreparedStatement statement, int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws SQLException {
        statement.setInt(1, time);
        statement.setInt(2, userId);
        statement.setInt(3, worldId);
        statement.setInt(4, x);
        statement.setInt(5, y);
        statement.setInt(6, z);
        statement.setInt(7, type);
        statement.setInt(8, data);
        statement.setObject(9, meta);
        statement.setObject(10, blockData);
        statement.setInt(11, action);
        statement.setInt(12, rolledBack);
    }

    private static void setMessage(PreparedStatement statement, long time, int userId, int worldId, int x, int y, int z, String message) throws SQLException {
        statement.setLong(1, time);
        statement.setInt(2, userId);
        statement.setInt(3, worldId);
        statement.setInt(4, x);
        statement.setInt(5, y);
        statement.setInt(6, z);
        statement.setString(7, message);
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        }
        else {
            statement.setLong(index, value);
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        }
        else {
            statement.setInt(index, value);
        }
    }

    private static Exception addFailure(Exception failure, Exception exception) {
        if (failure == null) {
            return exception;
        }
        failure.addSuppressed(exception);
        return failure;
    }

}
