package net.coreprotect.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.utility.ErrorReporter;

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
    private static final int INITIAL_DUCKDB_BLOCK_ID_RESERVATION = 256;
    private static final int MAXIMUM_DUCKDB_BLOCK_ID_RESERVATION = 65536;
    private static final long[] EMPTY_ROW_IDS = new long[0];

    private final Connection connection;
    private final DatabaseType databaseType;
    private final Statement transactionStatement;
    private final PreparedStatement[] batchStatements = new PreparedStatement[16];
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
    private PreparedStatement duckDBBlockRowIdStatement;
    private DuckDBAppender duckDBBlockAppender;
    private long[] duckDBBlockRowIds = EMPTY_ROW_IDS;
    private int duckDBBlockRowIdIndex;
    private int duckDBBlockIdReservationSize = INITIAL_DUCKDB_BLOCK_ID_RESERVATION;
    private EntitySpawnStatement.Updates entitySpawnUpdates;

    public RelationalConsumerWriteBatch(Connection connection, DatabaseType databaseType) throws SQLException {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.databaseType = Objects.requireNonNull(databaseType, "databaseType");
        transactionStatement = connection.createStatement();
    }

    @Override
    public void begin() throws Exception {
        Database.beginTransaction(transactionStatement, databaseType);
    }

    @Override
    public boolean commit() throws Exception {
        try {
            finishDuckDBBlockAppender();
            boolean acknowledgedRollback = Database.isRollbackOnlyTransactionAcknowledged();
            if (!Database.isTransactionRollbackOnly()) {
                for (PreparedStatement statement : batchStatements) {
                    if (statement != null) {
                        statement.executeBatch();
                    }
                }
            }
            boolean committed = Database.commitTransactionChecked(transactionStatement, databaseType);
            if (!committed) {
                boolean rolledBack = Database.rollbackTransaction(transactionStatement, databaseType);
                return acknowledgedRollback && rolledBack;
            }
            return true;
        }
        catch (Exception exception) {
            Database.rollbackTransaction(transactionStatement, databaseType);
            ErrorReporter.report(exception);
            return false;
        }
    }

    @Override
    public void rollback() {
        try {
            finishDuckDBBlockAppender();
        }
        catch (Exception exception) {
            ErrorReporter.report(exception);
        }
        Database.rollbackTransaction(transactionStatement, databaseType);
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
        switch (kind) {
            case ART:
                statement = batchStatement(Database.ART, ART_BATCH);
                break;
            case BLOCK_DATA:
                statement = batchStatement(Database.BLOCKDATA, BLOCK_DATA_BATCH);
                break;
            case ENTITY:
                statement = batchStatement(Database.ENTITY_MAP, ENTITY_MAP_BATCH);
                break;
            case MATERIAL:
                statement = batchStatement(Database.MATERIAL, MATERIAL_BATCH);
                break;
            case WORLD:
                statement = batchStatement(Database.WORLD, WORLD_BATCH);
                break;
            default:
                throw new IllegalArgumentException("Unsupported reference kind " + kind);
        }
        statement.setInt(1, id);
        statement.setString(2, value);
        addBatch(statement, batchCount);
    }

    @Override
    public void addBlock(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws Exception {
        if (databaseType.isDuckDB()) {
            appendDuckDBBlock(time, userId, worldId, x, y, z, type, data, meta, blockData, action, rolledBack);
            return;
        }
        PreparedStatement statement = batchStatement(Database.BLOCK, BLOCK_BATCH);
        setBlock(statement, time, userId, worldId, x, y, z, type, data, meta, blockData, action, rolledBack);
        addBatch(statement, batchCount);
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
        addBatch(statement, batchCount);
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
        addBatch(statement, batchCount);
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
        addBatch(statement, batchCount);
    }

    @Override
    public void addChat(int batchCount, long time, int userId, int worldId, int x, int y, int z, String message) throws Exception {
        PreparedStatement statement = batchStatement(Database.CHAT, CHAT_BATCH);
        setMessage(statement, time, userId, worldId, x, y, z, message);
        addBatch(statement, batchCount);
    }

    @Override
    public void addCommand(int batchCount, long time, int userId, int worldId, int x, int y, int z, String message) throws Exception {
        PreparedStatement statement = batchStatement(Database.COMMAND, COMMAND_BATCH);
        setMessage(statement, time, userId, worldId, x, y, z, message);
        addBatch(statement, batchCount);
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
        addBatch(statement, batchCount);
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
        addBatch(statement, batchCount);
    }

    @Override
    public int addEntity(int time, byte[] data) throws Exception {
        if (entityStatement == null) {
            entityStatement = own(required(Database.prepareStatement(connection, Database.ENTITY, true), "entity insert"));
        }
        entityStatement.setInt(1, time);
        entityStatement.setObject(2, data);
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
        if (data == null) {
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
    }

    @Override
    public void updateRolledBack(int target, int rolledBack, List<Long> rowIds) throws Exception {
        Database.performRolledBackUpdateChecked(transactionStatement, rolledBack, rowIds, target);
    }

    @Override
    public ConsumerEntitySpawnUpdates entitySpawnUpdates() throws Exception {
        if (entitySpawnUpdates == null) {
            entitySpawnUpdates = new EntitySpawnStatement.Updates(connection, this);
        }
        return entitySpawnUpdates;
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
        if (entitySpawnStatement == null) {
            String sql = "INSERT INTO " + ConfigHandler.prefix + "entity_spawn (time,block_rowid,kill_rowid,uuid,wid,current_wid,origin_x,origin_y,origin_z,x,y,z,yaw,pitch,data,removed) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            entitySpawnStatement = own(prepare(sql, true));
        }
        return entitySpawnStatement;
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

    private static void addBatch(PreparedStatement statement, int batchCount) throws SQLException {
        statement.addBatch();
        if (batchCount > 0 && batchCount % 1000 == 0) {
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

}
