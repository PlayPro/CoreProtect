package net.coreprotect.database;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.EntitySpawnStatement;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.sql.*;
import java.util.*;

public final class RelationalConsumerWriteBatch implements ConsumerWriteBatch {

    private static final int SIGN_BATCH = 0;
    private static final int BLOCK_BATCH = 1;
    private static final int ENTITY_BATCH = 2;
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
    private static final int INITIAL_DUCKDB_ROW_ID_RESERVATION = 256;
    private static final int MAXIMUM_DUCKDB_ROW_ID_RESERVATION = 65536;
    private static final int COMMIT_ROW_LIMIT = 10_000;
    private static final long[] EMPTY_ROW_IDS = new long[0];

    private final Connection connection;
    private final DatabaseType databaseType;
    private final Statement transactionStatement;
    private final PreparedStatement[] batchStatements = new PreparedStatement[16];
    private final int[] pendingBatchRows = new int[16];
    private final List<PreparedStatement> statements = new ArrayList<>();

    private PreparedStatement blockReturningStatement;
    private PreparedStatement skullStatement;
    private PreparedStatement entityStatement;
    private PreparedStatement entitySpawnStatement;
    private PreparedStatement entitySpawnBlockLinkStatement;
    private PreparedStatement entitySpawnCheckpointStatement;
    private PreparedStatement entitySpawnCheckpointStateStatement;
    private PreparedStatement entityInteractionStatement;
    private PreparedStatement userByNameStatement;
    private PreparedStatement userByNameOrUuidStatement;
    private PreparedStatement userInsertStatement;
    private PreparedStatement usernameByUuidStatement;
    private PreparedStatement usernameUpdateStatement;
    private PreparedStatement usernameHistoryStatement;
    private PreparedStatement usernameHistoryInsertStatement;
    private PreparedStatement databaseLockStatement;
    private final DuckDBTableAppender duckDBBlock = new DuckDBTableAppender("block");
    private final DuckDBTableAppender duckDBContainer = new DuckDBTableAppender("container");
    private final DuckDBTableAppender duckDBEntityContainer = new DuckDBTableAppender("entity_container");
    private final DuckDBTableAppender duckDBItem = new DuckDBTableAppender("item");
    private final DuckDBTableAppender duckDBEntity = new DuckDBTableAppender("entity");
    private final DuckDBTableAppender[] duckDBTables = {duckDBBlock, duckDBContainer, duckDBEntityContainer, duckDBItem, duckDBEntity};
    private DuckDBSpatialIndex.Transaction duckDBSpatialIndex;
    private EntitySpawnStatement.Updates entitySpawnUpdates;
    private boolean commitAttempted;
    private int transactionRows;
    private int nextSQLiteEntityId;

    public RelationalConsumerWriteBatch(Connection connection, DatabaseType databaseType) throws SQLException {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.databaseType = Objects.requireNonNull(databaseType, "databaseType");
        transactionStatement = connection.createStatement();
    }

    @Override
    public void begin() throws Exception {
        commitAttempted = false;
        transactionRows = 0;
        nextSQLiteEntityId = 0;
        if (databaseType.isDuckDB()) {
            duckDBSpatialIndex = DuckDBSpatialIndex.begin(connection, ConfigHandler.prefix);
        }
        Database.beginTransaction(transactionStatement, databaseType);
    }

    @Override
    public boolean commit() throws Exception {
        commitAttempted = false;
        try {
            finishDuckDBAppenders();
            boolean acknowledgedRollback = Database.isRollbackOnlyTransactionAcknowledged();
            if (!Database.isTransactionRollbackOnly()) {
                for (int index = 0; index < batchStatements.length; index++) {
                    PreparedStatement statement = batchStatements[index];
                    if (statement != null) {
                        pendingBatchRows[index] = 0;
                        statement.executeBatch();
                    }
                }
                if (duckDBSpatialIndex != null) {
                    duckDBSpatialIndex.flush(connection);
                }
            }
            boolean committed = Database.commitTransactionChecked(transactionStatement, databaseType, () -> commitAttempted = true);
            if (!committed) {
                boolean rolledBack = Database.rollbackTransaction(transactionStatement, databaseType);
                duckDBSpatialIndex = null;
                discardUncommittedDuckDBRowIds();
                return acknowledgedRollback && rolledBack;
            }
            if (acknowledgedRollback) {
                discardUncommittedDuckDBRowIds();
            } else {
                for (DuckDBTableAppender table : duckDBTables) {
                    table.markReservationCommitted();
                }
            }
            if (duckDBSpatialIndex != null) {
                duckDBSpatialIndex.publish();
                duckDBSpatialIndex = null;
            }
            return true;
        }
        catch (Exception exception) {
            Database.reportDatabaseFailure(exception);
            Database.rollbackTransaction(transactionStatement, databaseType);
            duckDBSpatialIndex = null;
            discardUncommittedDuckDBRowIds();
            return false;
        }
    }

    @Override
    public boolean wasCommitAttempted() {
        return commitAttempted;
    }

    /**
     * Bounds a relational transaction so a backlog after an outage commits in chunks instead of one transaction.
     */
    @Override
    public boolean shouldCommit() {
        return transactionRows >= COMMIT_ROW_LIMIT;
    }

    @Override
    public void rollback() {
        try {
            finishDuckDBAppenders();
        }
        catch (Exception exception) {
            Database.reportDatabaseFailure(exception);
        }
        Database.rollbackTransaction(transactionStatement, databaseType);
        duckDBSpatialIndex = null;
        discardUncommittedDuckDBRowIds();
        Arrays.fill(pendingBatchRows, 0);
    }

    @Override
    public void executeAtomically(String name, Database.SavepointOperation operation) throws Exception {
        Database.executeSavepoint(transactionStatement, name, operation);
    }

    @Override
    public int resolveUserId(String user, String uuid) throws Exception {
        String cacheKey = user.toLowerCase(Locale.ROOT);
        Integer cachedId = ConfigHandler.playerIdCache.get(cacheKey);
        if (cachedId != null) {
            return cachedId;
        }

        PreparedStatement statement = uuid == null ? userByNameStatement() : userByNameOrUuidStatement();
        statement.setString(1, user);
        if (uuid != null) {
            statement.setString(2, uuid);
        }

        int userId = -1;
        String storedUuid = uuid;
        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                userId = resultSet.getInt(1);
                storedUuid = resultSet.getString(2);
            }
        }
        if (userId == -1) {
            userId = insertUser(user);
        }

        ConfigHandler.playerIdCache.put(cacheKey, userId);
        ConfigHandler.playerIdCacheReversed.put(userId, user);
        if (storedUuid != null) {
            ConfigHandler.uuidCache.put(cacheKey, storedUuid);
            ConfigHandler.uuidCacheReversed.put(storedUuid, user);
        }
        return userId;
    }

    @Override
    public void recordUsername(String user, String uuid, int retainHistory, int time) throws Exception {
        if (ConfigHandler.isBlacklisted(user)) {
            return;
        }

        PreparedStatement lookup = usernameByUuidStatement();
        lookup.setString(1, uuid);
        int userId = -1;
        String storedUser = null;
        try (ResultSet resultSet = lookup.executeQuery()) {
            if (resultSet.next()) {
                userId = resultSet.getInt(1);
                storedUser = resultSet.getString(2);
            }
        }

        boolean update = storedUser == null || !user.equalsIgnoreCase(storedUser);
        if (storedUser == null) {
            Integer cachedId = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
            userId = cachedId == null ? resolveUserId(user, uuid) : cachedId;
        }

        if (update) {
            PreparedStatement statement = usernameUpdateStatement();
            statement.setString(1, user);
            statement.setString(2, uuid);
            statement.setInt(3, userId);
            statement.executeUpdate();
        }
        else {
            PreparedStatement statement = usernameHistoryStatement();
            statement.setString(1, uuid);
            statement.setString(2, user);
            try (ResultSet resultSet = statement.executeQuery()) {
                update = !resultSet.next();
            }
        }

        if (update && retainHistory == 1) {
            PreparedStatement statement = usernameHistoryInsertStatement();
            statement.setInt(1, time);
            statement.setString(2, uuid);
            statement.setString(3, user);
            statement.executeUpdate();
        }

        String cacheKey = user.toLowerCase(Locale.ROOT);
        ConfigHandler.playerIdCache.put(cacheKey, userId);
        ConfigHandler.playerIdCacheReversed.put(userId, user);
        ConfigHandler.uuidCache.put(cacheKey, uuid);
        ConfigHandler.uuidCacheReversed.put(uuid, user);
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
        PreparedStatement statement;
        int batchIndex;
        switch (kind) {
            case ART:
                batchIndex = ART_BATCH;
                statement = batchStatement(Database.ART, batchIndex);
                break;
            case BLOCK_DATA:
                batchIndex = BLOCK_DATA_BATCH;
                statement = batchStatement(Database.BLOCKDATA, batchIndex);
                break;
            case ENTITY:
                batchIndex = ENTITY_MAP_BATCH;
                statement = batchStatement(Database.ENTITY_MAP, batchIndex);
                break;
            case MATERIAL:
                batchIndex = MATERIAL_BATCH;
                statement = batchStatement(Database.MATERIAL, batchIndex);
                break;
            case WORLD:
                batchIndex = WORLD_BATCH;
                statement = batchStatement(Database.WORLD, batchIndex);
                break;
            default:
                throw new IllegalArgumentException("Unsupported reference kind " + kind);
        }
        statement.setInt(1, id);
        statement.setString(2, value);
        addBatch(statement, batchIndex);
    }

    @Override
    public void addBlock(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws Exception {
        if (databaseType.isDuckDB()) {
            appendDuckDBBlock(time, userId, worldId, x, y, z, type, data, meta, blockData, action, rolledBack);
            return;
        }
        PreparedStatement statement = batchStatement(Database.BLOCK, BLOCK_BATCH);
        setBlock(statement, time, userId, worldId, x, y, z, type, data, meta, blockData, action, rolledBack);
        addBatch(statement, BLOCK_BATCH);
    }

    @Override
    public long addBlockReturningId(int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws Exception {
        if (databaseType.isDuckDB()) {
            long rowId = appendDuckDBBlock(time, userId, worldId, x, y, z, type, data, meta, blockData, action, rolledBack);
            duckDBBlock.flush();
            return rowId;
        }
        if (blockReturningStatement == null) {
            blockReturningStatement = own(required(Database.prepareStatement(connection, Database.BLOCK, true), "block insert"));
        }
        setBlock(blockReturningStatement, time, userId, worldId, x, y, z, type, data, meta, blockData, action, rolledBack);
        return executeReturningId(blockReturningStatement, "block insert");
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
        if (databaseType.isDuckDB()) {
            int rowId = Math.toIntExact(duckDBContainer.nextRowId());
            DuckDBAppender appender = duckDBContainer.beginRow()
                    .append(rowId)
                    .append(time)
                    .append(userId)
                    .append(worldId)
                    .append(x)
                    .append(y)
                    .append(z)
                    .append(type)
                    .append(data)
                    .append(amount);
            appendNullable(appender, metadata);
            appender.append((byte) action)
                    .append((byte) rolledBack)
                    .endRow();
            trackDuckDBRow("container", rowId, worldId, x, z, null);
            return;
        }
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
        addBatch(statement, CONTAINER_BATCH);
        trackDuckDBGenerated("container", worldId, x, z);
    }

    @Override
    public void addEntityContainer(int batchCount, int time, int userId, int entitySpawnRowId, int worldId, int x, int y, int z, int type, int data, int amount, byte[] metadata, int action, int rolledBack) throws Exception {
        if (databaseType.isDuckDB()) {
            int rowId = Math.toIntExact(duckDBEntityContainer.nextRowId());
            DuckDBAppender appender = duckDBEntityContainer.beginRow()
                    .append(rowId)
                    .append(time)
                    .append(userId)
                    .append(entitySpawnRowId)
                    .append(worldId)
                    .append(x)
                    .append(y)
                    .append(z)
                    .append(type)
                    .append(data)
                    .append(amount);
            appendNullable(appender, metadata);
            appender.append((byte) action)
                    .append((byte) rolledBack)
                    .endRow();
            trackDuckDBRow("entity_container", rowId, worldId, x, z, entitySpawnRowId);
            return;
        }
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
        addBatch(statement, ENTITY_CONTAINER_BATCH);
        trackDuckDBGenerated("entity_container", worldId, x, z, entitySpawnRowId);
    }

    @Override
    public void addItem(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, byte[] data, int amount, int action, int rolledBack) throws Exception {
        if (databaseType.isDuckDB()) {
            int rowId = Math.toIntExact(duckDBItem.nextRowId());
            DuckDBAppender appender = duckDBItem.beginRow()
                    .append(rowId)
                    .append(time)
                    .append(userId)
                    .append(worldId)
                    .append(x)
                    .append(y)
                    .append(z)
                    .append(type);
            appendNullable(appender, data);
            appender.append(amount)
                    .append((byte) action)
                    .append((byte) rolledBack)
                    .endRow();
            trackDuckDBRow("item", rowId, worldId, x, z, null);
            return;
        }
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
        addBatch(statement, ITEM_BATCH);
        trackDuckDBGenerated("item", worldId, x, z);
    }

    @Override
    public void addChat(int batchCount, long time, int userId, int worldId, int x, int y, int z, String message) throws Exception {
        PreparedStatement statement = batchStatement(Database.CHAT, CHAT_BATCH);
        setMessage(statement, time, userId, worldId, x, y, z, message);
        addBatch(statement, CHAT_BATCH);
        trackDuckDBGenerated("chat", worldId, x, z);
    }

    @Override
    public void addCommand(int batchCount, long time, int userId, int worldId, int x, int y, int z, String message) throws Exception {
        PreparedStatement statement = batchStatement(Database.COMMAND, COMMAND_BATCH);
        setMessage(statement, time, userId, worldId, x, y, z, message);
        addBatch(statement, COMMAND_BATCH);
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
        addBatch(statement, SESSION_BATCH);
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
        addBatch(statement, SIGN_BATCH);
        trackDuckDBGenerated("sign", worldId, x, z);
    }

    @Override
    public int addEntity(int time, byte[] data) throws Exception {
        if (databaseType.isDuckDB()) {
            int rowId = Math.toIntExact(duckDBEntity.nextRowId());
            DuckDBAppender appender = duckDBEntity.beginRow()
                    .append(rowId)
                    .append(time);
            appendNullable(appender, data);
            appender.endRow();
            return rowId;
        }
        if (nextSQLiteEntityId > 0) {
            PreparedStatement statement = batchStatements[ENTITY_BATCH];
            if (statement == null) {
                statement = own(connection.prepareStatement("INSERT INTO " + ConfigHandler.prefix + "entity (rowid,time,data) VALUES (?,?,?)"));
                batchStatements[ENTITY_BATCH] = statement;
            }
            int rowId = nextSQLiteEntityId++;
            statement.setInt(1, rowId);
            statement.setInt(2, time);
            statement.setObject(3, data);
            addBatch(statement, ENTITY_BATCH);
            return rowId;
        }
        if (entityStatement == null) {
            entityStatement = own(required(Database.prepareStatement(connection, Database.ENTITY, true), "entity insert"));
        }
        entityStatement.setInt(1, time);
        entityStatement.setObject(2, data);
        int rowId = Math.toIntExact(executeReturningId(entityStatement, "entity insert"));
        if (databaseType.isSQLite()) {
            nextSQLiteEntityId = rowId + 1;
        }
        return rowId;
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
        statement.addBatch();
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
        transactionRows += rowIds.size();
        Database.performRolledBackUpdateChecked(transactionStatement, rolledBack, rowIds, target);
    }

    @Override
    public ConsumerEntitySpawnUpdates entitySpawnUpdates() throws Exception {
        if (entitySpawnUpdates == null) {
            entitySpawnUpdates = new EntitySpawnStatement.Updates(connection, this, databaseType);
        }
        return entitySpawnUpdates;
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        try {
            finishDuckDBAppenders();
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
        if (entitySpawnStatement == null) {
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

    private PreparedStatement userByNameStatement() throws SQLException {
        if (userByNameStatement == null) {
            String collate = databaseType.isMySQL() ? "" : " COLLATE NOCASE";
            userByNameStatement = own(connection.prepareStatement("SELECT rowid,uuid FROM " + ConfigHandler.prefix + "user WHERE " + databaseType.getUserColumn() + "=?" + collate + " ORDER BY rowid ASC LIMIT 1 OFFSET 0"));
        }
        return userByNameStatement;
    }

    private PreparedStatement userByNameOrUuidStatement() throws SQLException {
        if (userByNameOrUuidStatement == null) {
            String collate = databaseType.isMySQL() ? "" : " COLLATE NOCASE";
            userByNameOrUuidStatement = own(connection.prepareStatement("SELECT rowid,uuid FROM " + ConfigHandler.prefix + "user WHERE " + databaseType.getUserColumn() + "=?" + collate + " OR uuid=? ORDER BY rowid ASC LIMIT 1 OFFSET 0"));
        }
        return userByNameOrUuidStatement;
    }

    private PreparedStatement usernameByUuidStatement() throws SQLException {
        if (usernameByUuidStatement == null) {
            usernameByUuidStatement = own(connection.prepareStatement("SELECT rowid," + databaseType.getUserColumn() + " FROM " + ConfigHandler.prefix + "user WHERE uuid=? LIMIT 1 OFFSET 0"));
        }
        return usernameByUuidStatement;
    }

    private PreparedStatement usernameUpdateStatement() throws SQLException {
        if (usernameUpdateStatement == null) {
            usernameUpdateStatement = own(connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "user SET " + databaseType.getUserColumn() + "=?,uuid=? WHERE rowid=?"));
        }
        return usernameUpdateStatement;
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

    private long appendDuckDBBlock(int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws SQLException {
        long rowId = duckDBBlock.nextRowId();
        DuckDBAppender appender = duckDBBlock.beginRow()
                .append(rowId)
                .append(time)
                .append(userId)
                .append(worldId)
                .append(x)
                .append(y)
                .append(z)
                .append(type)
                .append(data);
        appendNullable(appender, meta);
        appendNullable(appender, blockData);
        appender.append((byte) action)
                .append((byte) rolledBack)
                .endRow();
        trackDuckDBRow("block", rowId, worldId, x, z, null);
        return rowId;
    }

    private void trackDuckDBRow(String table, long rowId, int worldId, int x, int z, Integer entitySpawnRowId) throws SQLException {
        if (duckDBSpatialIndex != null) {
            duckDBSpatialIndex.addRow(table, rowId, worldId, x, z, entitySpawnRowId);
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

    private void finishDuckDBAppenders() throws SQLException {
        SQLException failure = null;
        for (DuckDBTableAppender table : duckDBTables) {
            try {
                table.finish();
            } catch (SQLException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void discardUncommittedDuckDBRowIds() {
        for (DuckDBTableAppender table : duckDBTables) {
            table.discardUncommittedRowIds();
        }
    }

    private static void appendNullable(DuckDBAppender appender, byte[] value) throws SQLException {
        if (value == null) {
            appender.appendNull();
        }
        else {
            appender.append(value);
        }
    }

    private int insertUser(String user) throws Exception {
        if (userInsertStatement == null) {
            String sql = "INSERT INTO " + ConfigHandler.prefix + "user (time," + databaseType.getUserColumn() + ") VALUES (?,?)";
            userInsertStatement = own(prepare(sql, true));
        }
        userInsertStatement.setInt(1, (int) (System.currentTimeMillis() / 1000L));
        userInsertStatement.setString(2, user);
        return Math.toIntExact(executeReturningId(userInsertStatement, "user insert"));
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

    /**
     * Flushes on this statement's own row count. The previous form used the consumer loop index,
     * shared across all 16 statements, so a statement only flushed if it happened to be used on
     * an index divisible by 1000 and could otherwise hold the whole batch in JDBC parameter sets.
     */
    private void addBatch(PreparedStatement statement, int index) throws SQLException {
        statement.addBatch();
        transactionRows++;
        if (++pendingBatchRows[index] >= 1000) {
            pendingBatchRows[index] = 0;
            statement.executeBatch();
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

    /**
     * Appends one DuckDB table with row ids reserved from its sequence. The appender is flushed and closed on every
     * commit or rollback. Reserved ids outlive a commit, but a crash replays a sequence only up to its last committed
     * advance, so ids reserved inside a transaction that did not commit are dropped.
     */
    private final class DuckDBTableAppender {

        private final String table;
        private PreparedStatement rowIdStatement;
        private DuckDBAppender appender;
        private long[] rowIds = EMPTY_ROW_IDS;
        private int rowIdIndex;
        private int reservationSize = INITIAL_DUCKDB_ROW_ID_RESERVATION;
        private boolean reservationCommitted;

        private DuckDBTableAppender(String table) {
            this.table = table;
        }

        private DuckDBAppender beginRow() throws SQLException {
            if (appender == null) {
                DuckDBConnection duckDBConnection = connection.unwrap(DuckDBConnection.class);
                appender = duckDBConnection.createAppender(DuckDBConnection.DEFAULT_SCHEMA, ConfigHandler.prefix + table);
            }
            transactionRows++;
            return appender.beginRow();
        }

        private long nextRowId() throws SQLException {
            if (rowIdIndex >= rowIds.length) {
                reserveRowIds();
            }
            return rowIds[rowIdIndex++];
        }

        private void reserveRowIds() throws SQLException {
            flush();
            if (rowIdStatement == null) {
                String sequence = ConfigHandler.prefix + table + "_rowid_seq";
                rowIdStatement = own(connection.prepareStatement("SELECT nextval('" + sequence + "') FROM range(?)"));
            }

            int size = reservationSize;
            long[] reserved = new long[size];
            rowIdStatement.setInt(1, size);
            try (ResultSet resultSet = rowIdStatement.executeQuery()) {
                for (int index = 0; index < size; index++) {
                    if (!resultSet.next()) {
                        throw new SQLException("DuckDB " + table + " row id reservation returned too few values");
                    }
                    reserved[index] = resultSet.getLong(1);
                }
                if (resultSet.next()) {
                    throw new SQLException("DuckDB " + table + " row id reservation returned too many values");
                }
            }
            rowIds = reserved;
            rowIdIndex = 0;
            reservationSize = Math.min(MAXIMUM_DUCKDB_ROW_ID_RESERVATION, size * 2);
            reservationCommitted = false;
        }

        private void flush() throws SQLException {
            if (appender != null) {
                appender.flush();
            }
        }

        private void finish() throws SQLException {
            DuckDBAppender current = appender;
            appender = null;
            if (current == null) {
                return;
            }

            SQLException failure = null;
            try {
                current.flush();
            } catch (SQLException exception) {
                failure = exception;
            }
            try {
                current.close();
            } catch (SQLException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void markReservationCommitted() {
            reservationCommitted = true;
        }

        private void discardUncommittedRowIds() {
            if (!reservationCommitted) {
                rowIds = EMPTY_ROW_IDS;
                rowIdIndex = 0;
                reservationSize = INITIAL_DUCKDB_ROW_ID_RESERVATION;
            }
        }
    }

}
