package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.ConsumerEntitySpawnUpdates;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.model.rollback.RollbackUpdateTargets;
import net.coreprotect.utility.ErrorReporter;

public final class ClickHouseConsumerWriteBatch implements ConsumerWriteBatch {

    private static final int TARGET_BATCH_ROWS = 100_000;

    private final ClickHouseDatabase database;
    private final String userTable;
    private final String usernameLogTable;
    private final ClickHouseEntitySpawnUpdates entitySpawnUpdates;
    private final EnumMap<ClickHouseFamily, Map<Long, ClickHouseEventPointer>> localPointers = new EnumMap<>(ClickHouseFamily.class);
    private final Map<Long, Integer> localBlockActions = new HashMap<>();
    private final Map<String, UserRow> usersByName = new HashMap<>();
    private final Map<String, UserRow> usersByUuid = new HashMap<>();
    private final Map<String, Integer> userAliases = new HashMap<>();
    private final Set<String> usernameHistory = new HashSet<>();
    private final EnumMap<ReferenceKind, Map<Integer, String>> localReferences = new EnumMap<>(ReferenceKind.class);
    private final EnumMap<ReferenceKind, Map<Integer, String>> unpublishedReferences = new EnumMap<>(ReferenceKind.class);
    private ClickHouseWriteBatch batch;
    private boolean closed;

    public ClickHouseConsumerWriteBatch(ClickHouseDatabase database, String prefix) {
        this.database = Objects.requireNonNull(database, "database");
        String validatedPrefix = prefix == null || prefix.isEmpty() ? "" : ClickHouseIdentifiers.requireIdentifier(prefix, "ClickHouse table prefix");
        userTable = validatedPrefix + ClickHouseFamily.USER.getTableName();
        usernameLogTable = validatedPrefix + ClickHouseFamily.USERNAME_LOG.getTableName();
        entitySpawnUpdates = new ClickHouseEntitySpawnUpdates(this, validatedPrefix + "event_data");
    }

    @Override
    public void begin() throws SQLException {
        ensureOpen();
        if (batch != null) {
            throw new IllegalStateException("ClickHouse consumer batch is already active");
        }
        batch = database.newWriteBatch();
        clearBatchState();
        entitySpawnUpdates.beginBatch();
        Consumer.transacting = true;
    }

    @Override
    public boolean commit() {
        ClickHouseWriteBatch current = requireBatch();
        boolean published = false;
        try {
            database.publish(current);
            published = true;
            try {
                publishUserCaches();
            }
            finally {
                entitySpawnUpdates.batchPublished();
            }
            return true;
        }
        catch (Exception exception) {
            ErrorReporter.report(exception);
            return published;
        }
        finally {
            current.close();
            batch = null;
            if (!published) {
                discardReferenceCache(Collections.emptyMap());
                entitySpawnUpdates.batchDiscarded();
            }
            clearBatchState();
            Consumer.transacting = false;
            Consumer.interrupt = false;
        }
    }

    @Override
    public boolean shouldCommit() {
        return batch != null && batch.size() >= TARGET_BATCH_ROWS;
    }

    @Override
    public void rollback() {
        if (batch == null) {
            return;
        }
        batch.close();
        batch = null;
        discardReferenceCache(Collections.emptyMap());
        clearBatchState();
        entitySpawnUpdates.batchDiscarded();
        Consumer.transacting = false;
        Consumer.interrupt = false;
    }

    @Override
    public void executeAtomically(String name, Database.SavepointOperation operation) throws Exception {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(operation, "operation");
        ClickHouseWriteBatch current = requireBatch();
        ClickHouseWriteBatch.Checkpoint batchCheckpoint = current.checkpoint();
        AdapterCheckpoint adapterCheckpoint = checkpoint();
        ClickHouseEntitySpawnUpdates.Checkpoint entityCheckpoint = entitySpawnUpdates.checkpoint();
        try {
            operation.execute();
        }
        catch (Exception exception) {
            try {
                current.restore(batchCheckpoint);
                restore(adapterCheckpoint);
                entitySpawnUpdates.restore(entityCheckpoint);
            }
            catch (RuntimeException restoreException) {
                exception.addSuppressed(restoreException);
                rollback();
            }
            throw exception;
        }
    }

    @Override
    public int resolveUserId(String user, String uuid) throws Exception {
        requireBatch();
        String requiredUser = requireText(user, "user");
        String normalizedUuid = normalizeUuid(uuid);
        String cacheKey = normalizeName(requiredUser);
        Integer cachedId = cachedUserId(cacheKey, normalizedUuid);
        if (cachedId != null) {
            return cachedId;
        }
        return resolveUser(requiredUser, normalizedUuid, currentTime(), false).row.id;
    }

    private UserResolution resolveUser(String user, String uuid, int time, boolean updateIdentity) throws Exception {
        String cacheKey = normalizeName(user);
        UserRow row = null;
        if (!uuid.isEmpty()) {
            row = usersByUuid.get(uuid);
            if (row == null) {
                row = findUserByUuid(uuid);
            }
        }
        if (row == null) {
            row = usersByName.get(cacheKey);
            if (row == null) {
                row = findUserByName(user);
            }
        }

        int rowId = database.canonicalUserId(user, uuid, row == null ? null : row.id);
        if (row == null || row.id != rowId) {
            row = findStagedUserById(rowId);
            if (row == null) {
                row = findUserById(rowId);
            }
        }
        boolean changed = row == null;
        if (changed) {
            events().addUserVersion(rowId, time, user, uuid);
            row = new UserRow(rowId, user, uuid);
        }
        else if (!uuid.isEmpty() && (row.uuid.isEmpty()
                || (updateIdentity && (!user.equalsIgnoreCase(row.name) || !uuid.equals(row.uuid))))) {
            events().addUserVersion(rowId, time, user, uuid);
            unstageUser(row);
            row = new UserRow(rowId, user, uuid);
            changed = true;
        }
        stageUser(row);
        userAliases.put(cacheKey, row.id);
        return new UserResolution(row, changed);
    }

    @Override
    public void recordUsername(String user, String uuid, int retainHistory, int time) throws Exception {
        requireBatch();
        String requiredUser = requireText(user, "user");
        String requiredUuid = requireText(uuid, "uuid");
        if (ConfigHandler.isBlacklisted(requiredUser)) {
            return;
        }
        UserResolution resolution = resolveUser(requiredUser, requiredUuid, time, true);
        if (retainHistory == 1 && (resolution.changed || !hasUsernameHistory(requiredUuid, requiredUser))) {
            events().addUsernameLog(time, requiredUuid, requiredUser);
            usernameHistory.add(historyKey(requiredUuid, requiredUser));
        }
    }

    @Override
    public void updateDatabaseLock(int status, int time) throws Exception {
        requireBatch();
        events().addDatabaseLock(time, status);
    }

    @Override
    public void addReference(ReferenceKind kind, int batchCount, int id, String value) throws Exception {
        requireBatch();
        Objects.requireNonNull(kind, "kind");
        String requiredValue = Objects.requireNonNull(value, "value");
        Map<Integer, String> references = localReferences.computeIfAbsent(kind, ignored -> new HashMap<>());
        for (Map.Entry<Integer, String> reference : references.entrySet()) {
            if (reference.getKey() != id && reference.getValue().equals(requiredValue)) {
                throw referenceConflict(kind, id, requiredValue, reference.getKey(), reference.getValue());
            }
        }
        String stagedValue = references.get(id);
        if (stagedValue != null) {
            if (!stagedValue.equals(requiredValue)) {
                throw referenceConflict(kind, id, requiredValue, id, stagedValue);
            }
            return;
        }

        Map<Integer, String> unpublished = unpublishedReferences.computeIfAbsent(kind, ignored -> new HashMap<>());
        unpublished.put(id, requiredValue);
        references.put(id, requiredValue);
        try {
            switch (kind) {
                case ART:
                    events().addArtMap(id, requiredValue);
                    break;
                case BLOCK_DATA:
                    events().addBlockDataMap(id, requiredValue);
                    break;
                case ENTITY:
                    events().addEntityMap(id, requiredValue);
                    break;
                case MATERIAL:
                    events().addMaterialMap(id, requiredValue);
                    break;
                case WORLD:
                    events().addWorld(id, requiredValue);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported ClickHouse reference kind: " + kind);
            }
        }
        catch (Exception exception) {
            references.remove(id, requiredValue);
            throw exception;
        }
    }

    @Override
    public void addBlock(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws Exception {
        events().addBlock(time, userId, worldId, x, y, z, type, data, meta, blockData, action, rolledBack);
    }

    @Override
    public long addBlockReturningId(int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws Exception {
        long rowId = events().addBlock(time, userId, worldId, x, y, z, type, data, meta, blockData, action, rolledBack);
        registerPointer(ClickHouseFamily.BLOCK, rowId);
        localBlockActions.put(rowId, action);
        return rowId;
    }

    @Override
    public int addSkull(int time, String owner, String skin) throws Exception {
        return toIntId(events().addSkull(time, owner, skin), "skull");
    }

    @Override
    public void addContainer(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, int data, int amount, byte[] metadata, int action, int rolledBack) throws Exception {
        events().addContainer(time, userId, worldId, x, y, z, type, data, amount, metadata, action, rolledBack);
    }

    @Override
    public void addEntityContainer(int batchCount, int time, int userId, int entitySpawnRowId, int worldId, int x, int y, int z, int type, int data, int amount, byte[] metadata, int action, int rolledBack) throws Exception {
        events().addEntityContainer(time, userId, entitySpawnRowId, worldId, x, y, z, type, data, amount, metadata, action, rolledBack);
    }

    @Override
    public void addItem(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, byte[] data, int amount, int action, int rolledBack) throws Exception {
        events().addItem(time, userId, worldId, x, y, z, type, data, amount, action, rolledBack);
    }

    @Override
    public void addChat(int batchCount, long time, int userId, int worldId, int x, int y, int z, String message) throws Exception {
        events().addChat(unsignedTime(time), userId, worldId, x, y, z, message);
    }

    @Override
    public void addCommand(int batchCount, long time, int userId, int worldId, int x, int y, int z, String message) throws Exception {
        events().addCommand(unsignedTime(time), userId, worldId, x, y, z, message);
    }

    @Override
    public void addSession(int batchCount, int time, int userId, int worldId, int x, int y, int z, int action) throws Exception {
        events().addSession(time, userId, worldId, x, y, z, action);
    }

    @Override
    public void addSign(int batchCount, int time, int userId, int worldId, int x, int y, int z, int action, int color, int colorSecondary, int data, int waxed, int face, String[] lines) throws Exception {
        events().addSign(time, userId, worldId, x, y, z, action, color, colorSecondary, data, waxed, face, lines);
    }

    @Override
    public int addEntity(int time, byte[] data) throws Exception {
        return toIntId(events().addEntity(time, data), "entity");
    }

    @Override
    public int addEntitySpawn(int time, Long blockRowId, Integer killRowId, UUID uuid, int originWorldId, int currentWorldId, double originX, double originY, double originZ, double currentX, double currentY, double currentZ, float yaw, float pitch, byte[] data, int removed) throws Exception {
        if (removed != 0 && removed != 1) {
            throw new IllegalArgumentException("ClickHouse entity spawn removed state must be zero or one");
        }
        entitySpawnUpdates.requireAvailable(uuid, killRowId, null);
        long rowId = events().addEntitySpawn(time, blockRowId, killRowId, uuid, originWorldId, currentWorldId, originX, originY, originZ, currentX, currentY, currentZ, yaw, pitch, data, removed);
        ClickHouseEventPointer pointer = registerPointer(ClickHouseFamily.ENTITY_SPAWN, rowId);
        entitySpawnUpdates.register(new ClickHouseEntityState(pointer, blockRowId, killRowId, uuid, currentWorldId, currentX, currentY, currentZ, yaw, pitch, data, removed == 1));
        return toIntId(rowId, "entity spawn");
    }

    @Override
    public void linkEntitySpawnBlock(int trackingRowId, long blockRowId) throws Exception {
        entitySpawnUpdates.linkBlock(trackingRowId, blockRowId);
    }

    @Override
    public void linkEntitySpawnKill(UUID uuid, int killRowId) throws Exception {
        entitySpawnUpdates.linkKill(uuid, killRowId);
    }

    @Override
    public boolean checkpointEntitySpawn(int trackingRowId, int worldId, double x, double y, double z, float yaw, float pitch) throws Exception {
        return entitySpawnUpdates.checkpointLocation(trackingRowId, worldId, x, y, z, yaw, pitch);
    }

    @Override
    public void addEntityInteraction(int time, int userId, int entitySpawnRowId, int worldId, int x, int y, int z, int type, int action, byte[] metadata, int rolledBack) throws Exception {
        events().addEntityInteraction(time, userId, entitySpawnRowId, worldId, x, y, z, type, action, metadata, rolledBack);
    }

    @Override
    public void updateRolledBack(int target, int rolledBack, List<Long> rowIds) throws Exception {
        ClickHouseFamily family = rollbackFamily(target);
        Set<Long> unique = validateRowIds(rowIds);
        Map<Long, ClickHouseEventPointer> resolved = new LinkedHashMap<>();
        Map<Long, ClickHouseEventPointer> local = localPointers.get(family);
        List<Long> remote = new ArrayList<>();
        for (Long rowId : unique) {
            ClickHouseEventPointer pointer = local == null ? null : local.get(rowId);
            if (pointer == null) {
                remote.add(rowId);
            }
            else {
                resolved.put(rowId, pointer);
            }
        }
        if (!remote.isEmpty()) {
            resolved.putAll(database.resolveEventPointers(family, remote));
        }
        for (Long rowId : unique) {
            ClickHouseEventPointer pointer = resolved.get(rowId);
            if (pointer == null) {
                throw new SQLException("Unable to resolve ClickHouse " + family.getTableName() + " row ID " + rowId);
            }
            requireBatch().addRollback(pointer, rolledBack);
        }
    }

    @Override
    public void updateRolledBackRows(int target, int rolledBack, List<Object[]> rows) throws Exception {
        Objects.requireNonNull(rows, "rows");
        ClickHouseFamily family = rollbackFamily(target);
        if (family == ClickHouseFamily.ENTITY_CONTAINER) {
            ConsumerWriteBatch.super.updateRolledBackRows(target, rolledBack, rows);
            return;
        }
        ClickHouseWriteBatch current = requireBatch();
        Set<Long> unique = new LinkedHashSet<>();
        for (Object[] row : rows) {
            if (row == null || row.length <= 10
                    || !(row[0] instanceof Long)
                    || !(row[1] instanceof Integer)
                    || !(row[3] instanceof Integer)
                    || !(row[5] instanceof Integer)
                    || !(row[10] instanceof Integer)) {
                throw new IllegalArgumentException("ClickHouse rollback rows are missing required event coordinates");
            }
            long rowId = (Long) row[0];
            if (unique.add(rowId)) {
                current.addRollback(family, rowId, (Integer) row[1], (Integer) row[10], (Integer) row[3], (Integer) row[5], rolledBack);
            }
        }
    }

    @Override
    public ConsumerEntitySpawnUpdates entitySpawnUpdates() {
        requireBatch();
        return entitySpawnUpdates;
    }

    @Override
    public void close() {
        if (!closed) {
            rollback();
            entitySpawnUpdates.close();
            closed = true;
        }
    }

    Connection openConnection() throws SQLException {
        return database.openAuxiliaryConnection();
    }

    ClickHouseDatabase database() {
        return database;
    }

    ClickHouseWriteBatch requireBatch() {
        ensureOpen();
        if (batch == null) {
            throw new IllegalStateException("ClickHouse consumer batch is not active");
        }
        return batch;
    }

    boolean updateBlockState(long rowId, int rolledBack, int expectedAction) throws Exception {
        Integer action = localBlockActions.get(rowId);
        if (action == null) {
            prefetchBlockTargets(java.util.Collections.singleton(rowId));
            action = localBlockActions.get(rowId);
        }
        if (action == null || action != expectedAction) {
            return false;
        }
        updateRolledBack(RollbackUpdateTargets.BLOCK, rolledBack, java.util.Collections.singletonList(rowId));
        return true;
    }

    void prefetchBlockTargets(Set<Long> rowIds) throws SQLException {
        if (rowIds.isEmpty()) {
            return;
        }
        Map<Long, ClickHouseEventPointer> blockPointers = localPointers.get(ClickHouseFamily.BLOCK);
        List<Long> remote = new ArrayList<>();
        for (Long rowId : rowIds) {
            if (rowId != null && rowId > 0 && (blockPointers == null || !blockPointers.containsKey(rowId) || !localBlockActions.containsKey(rowId))) {
                remote.add(rowId);
            }
        }
        if (remote.isEmpty()) {
            return;
        }
        Map<Long, Integer> actions = new HashMap<>();
        Map<Long, ClickHouseEventPointer> resolved = database.resolveAvailableBlockTargets(remote, actions);
        if (!resolved.isEmpty()) {
            Map<Long, ClickHouseEventPointer> pointers = localPointers.computeIfAbsent(ClickHouseFamily.BLOCK, ignored -> new LinkedHashMap<>());
            resolved.forEach(pointers::putIfAbsent);
            actions.forEach(localBlockActions::putIfAbsent);
        }
    }

    private ClickHouseEventBatch events() {
        return requireBatch().events();
    }

    private ClickHouseEventPointer registerPointer(ClickHouseFamily family, long rowId) {
        ClickHouseEventPointer pointer = events().getLastPointer();
        if (pointer.getFamily() != family || pointer.getRowId() != rowId) {
            throw new IllegalStateException("ClickHouse event pointer does not match the staged fact");
        }
        localPointers.computeIfAbsent(family, ignored -> new LinkedHashMap<>()).put(rowId, pointer);
        return pointer;
    }

    private UserRow findStagedUserById(int id) {
        for (UserRow row : usersByName.values()) {
            if (row.id == id) {
                return row;
            }
        }
        for (UserRow row : usersByUuid.values()) {
            if (row.id == id) {
                return row;
            }
        }
        return null;
    }

    private Integer cachedUserId(String user, String uuid) {
        Integer id = userAliases.get(user);
        if (id != null) {
            UserRow row = findStagedUserById(id);
            return uuid.isEmpty() || (row != null && uuid.equals(row.uuid)) ? id : null;
        }
        id = ConfigHandler.playerIdCache.get(user);
        return id != null && (uuid.isEmpty() || uuid.equals(ConfigHandler.uuidCache.get(user))) ? id : null;
    }

    private UserRow findUserByName(String user) throws SQLException {
        String sql = "SELECT rowid,`user`,uuid FROM " + userTable
                + " WHERE lowerUTF8(`user`)=lowerUTF8(?) ORDER BY (uuid!='') DESC,rowid LIMIT 1";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user);
            return readUser(statement);
        }
    }

    private UserRow findUserByUuid(String uuid) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT rowid,`user`,uuid FROM " + userTable
                        + " WHERE uuid=? ORDER BY rowid LIMIT 1")) {
            statement.setString(1, uuid);
            return readUser(statement);
        }
    }

    private UserRow findUserById(int id) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("SELECT rowid,`user`,uuid FROM " + userTable + " WHERE rowid=? LIMIT 1")) {
            statement.setInt(1, id);
            return readUser(statement);
        }
    }

    private UserRow readUser(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return null;
            }
            UserRow row = new UserRow(toIntId(resultSet.getLong(1), "user"), resultSet.getString(2), normalizeUuid(resultSet.getString(3)));
            stageUser(row);
            return row;
        }
    }

    private boolean hasUsernameHistory(String uuid, String user) throws SQLException {
        String key = historyKey(uuid, user);
        if (usernameHistory.contains(key)) {
            return true;
        }
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + usernameLogTable + " WHERE uuid=? AND `user`=? LIMIT 1")) {
            statement.setString(1, uuid);
            statement.setString(2, user);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    usernameHistory.add(key);
                    return true;
                }
            }
        }
        return false;
    }

    static ClickHouseFamily referenceFamily(ReferenceKind kind) {
        switch (kind) {
            case ART:
                return ClickHouseFamily.ART_MAP;
            case BLOCK_DATA:
                return ClickHouseFamily.BLOCKDATA_MAP;
            case ENTITY:
                return ClickHouseFamily.ENTITY_MAP;
            case MATERIAL:
                return ClickHouseFamily.MATERIAL_MAP;
            case WORLD:
                return ClickHouseFamily.WORLD;
            default:
                throw new IllegalArgumentException("Unsupported ClickHouse reference kind: " + kind);
        }
    }

    static String referenceValueColumn(ReferenceKind kind) {
        switch (kind) {
            case ART:
                return "art";
            case BLOCK_DATA:
                return "data";
            case ENTITY:
                return "entity";
            case MATERIAL:
                return "material";
            case WORLD:
                return "world";
            default:
                throw new IllegalArgumentException("Unsupported ClickHouse reference kind: " + kind);
        }
    }

    private static SQLException referenceConflict(ReferenceKind kind, int id, String value, int storedId, String storedValue) {
        return new SQLException("ClickHouse " + kind.name().toLowerCase(Locale.ROOT) + " reference " + id + "='" + value + "' conflicts with " + storedId + "='" + storedValue + "'");
    }

    private void stageUser(UserRow row) {
        usersByName.put(normalizeName(row.name), row);
        if (!row.uuid.isEmpty()) {
            usersByUuid.put(row.uuid, row);
        }
    }

    private static void cacheUser(UserRow row) {
        String key = normalizeName(row.name);
        ConfigHandler.playerIdCache.put(key, row.id);
        ConfigHandler.playerIdCacheReversed.put(row.id, row.name);
        if (!row.uuid.isEmpty()) {
            ConfigHandler.uuidCache.put(key, row.uuid);
            ConfigHandler.uuidCacheReversed.put(row.uuid, row.name);
        }
    }

    private void publishUserCaches() {
        for (UserRow row : usersByName.values()) {
            cacheUser(row);
        }
        ConfigHandler.playerIdCache.putAll(userAliases);
    }

    private void unstageUser(UserRow row) {
        usersByName.remove(normalizeName(row.name), row);
        if (!row.uuid.isEmpty()) {
            usersByUuid.remove(row.uuid, row);
        }
    }

    private AdapterCheckpoint checkpoint() {
        EnumMap<ClickHouseFamily, Map<Long, ClickHouseEventPointer>> pointers = new EnumMap<>(ClickHouseFamily.class);
        for (Map.Entry<ClickHouseFamily, Map<Long, ClickHouseEventPointer>> entry : localPointers.entrySet()) {
            pointers.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return new AdapterCheckpoint(pointers, localBlockActions, usersByName, usersByUuid, userAliases,
                usernameHistory, localReferences, unpublishedReferences);
    }

    private void restore(AdapterCheckpoint checkpoint) {
        discardReferenceCache(checkpoint.unpublishedReferences);
        localPointers.clear();
        for (Map.Entry<ClickHouseFamily, Map<Long, ClickHouseEventPointer>> entry : checkpoint.localPointers.entrySet()) {
            localPointers.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        localBlockActions.clear();
        localBlockActions.putAll(checkpoint.localBlockActions);
        usersByName.clear();
        usersByName.putAll(checkpoint.usersByName);
        usersByUuid.clear();
        usersByUuid.putAll(checkpoint.usersByUuid);
        userAliases.clear();
        userAliases.putAll(checkpoint.userAliases);
        usernameHistory.clear();
        usernameHistory.addAll(checkpoint.usernameHistory);
        localReferences.clear();
        checkpoint.localReferences.forEach((kind, references) -> localReferences.put(kind, new HashMap<>(references)));
        unpublishedReferences.clear();
        checkpoint.unpublishedReferences.forEach((kind, references) -> unpublishedReferences.put(kind, new HashMap<>(references)));
    }

    private void discardReferenceCache(Map<ReferenceKind, Map<Integer, String>> retainedReferences) {
        unpublishedReferences.forEach((kind, references) -> {
            Map<Integer, String> retained = retainedReferences.get(kind);
            references.forEach((id, value) -> {
                if (retained == null || !value.equals(retained.get(id))) {
                    ConfigHandler.uncacheIdentifier(kind, id, value);
                }
            });
        });
    }

    private void clearBatchState() {
        localPointers.clear();
        localBlockActions.clear();
        usersByName.clear();
        usersByUuid.clear();
        userAliases.clear();
        usernameHistory.clear();
        localReferences.clear();
        unpublishedReferences.clear();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("ClickHouse consumer batch is closed");
        }
    }

    private static Set<Long> validateRowIds(List<Long> rowIds) {
        Objects.requireNonNull(rowIds, "rowIds");
        Set<Long> unique = new LinkedHashSet<>();
        for (Long rowId : rowIds) {
            if (rowId == null || rowId < 1) {
                throw new IllegalArgumentException("ClickHouse rollback row IDs must be positive");
            }
            unique.add(rowId);
        }
        return unique;
    }

    private static ClickHouseFamily rollbackFamily(int target) {
        if (target == RollbackUpdateTargets.ENTITY_CONTAINER) {
            return ClickHouseFamily.ENTITY_CONTAINER;
        }
        if (RollbackUpdateTargets.updatesContainerTable(target)) {
            return ClickHouseFamily.CONTAINER;
        }
        if (RollbackUpdateTargets.updatesItemTable(target)) {
            return ClickHouseFamily.ITEM;
        }
        return ClickHouseFamily.BLOCK;
    }

    private static int toIntId(long rowId, String family) throws SQLException {
        if (rowId > Integer.MAX_VALUE) {
            throw new SQLException("ClickHouse " + family + " row IDs exceed CoreProtect's signed integer compatibility range");
        }
        return (int) rowId;
    }

    private static int unsignedTime(long time) {
        if (time < 0 || time > 0xffff_ffffL) {
            throw new IllegalArgumentException("ClickHouse timestamps must fit UInt32");
        }
        return (int) time;
    }

    private static int currentTime() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("ClickHouse " + name + " cannot be empty");
        }
        return value;
    }

    private static String normalizeUuid(String uuid) {
        return uuid == null ? "" : uuid;
    }

    private static String normalizeName(String user) {
        return user.toLowerCase(Locale.ROOT);
    }

    private static String historyKey(String uuid, String user) {
        return uuid + '\0' + user;
    }

    private static final class UserRow {

        private final int id;
        private final String name;
        private final String uuid;

        private UserRow(int id, String name, String uuid) {
            this.id = id;
            this.name = Objects.requireNonNull(name, "name");
            this.uuid = Objects.requireNonNull(uuid, "uuid");
        }
    }

    private static final class UserResolution {

        private final UserRow row;
        private final boolean changed;

        private UserResolution(UserRow row, boolean changed) {
            this.row = row;
            this.changed = changed;
        }
    }

    private static final class AdapterCheckpoint {

        private final EnumMap<ClickHouseFamily, Map<Long, ClickHouseEventPointer>> localPointers;
        private final Map<Long, Integer> localBlockActions;
        private final Map<String, UserRow> usersByName;
        private final Map<String, UserRow> usersByUuid;
        private final Map<String, Integer> userAliases;
        private final Set<String> usernameHistory;
        private final EnumMap<ReferenceKind, Map<Integer, String>> localReferences = new EnumMap<>(ReferenceKind.class);
        private final EnumMap<ReferenceKind, Map<Integer, String>> unpublishedReferences = new EnumMap<>(ReferenceKind.class);

        private AdapterCheckpoint(EnumMap<ClickHouseFamily, Map<Long, ClickHouseEventPointer>> localPointers, Map<Long, Integer> localBlockActions,
                Map<String, UserRow> usersByName, Map<String, UserRow> usersByUuid,
                Map<String, Integer> userAliases, Set<String> usernameHistory,
                EnumMap<ReferenceKind, Map<Integer, String>> localReferences,
                EnumMap<ReferenceKind, Map<Integer, String>> unpublishedReferences) {
            this.localPointers = localPointers;
            this.localBlockActions = new HashMap<>(localBlockActions);
            this.usersByName = new HashMap<>(usersByName);
            this.usersByUuid = new HashMap<>(usersByUuid);
            this.userAliases = new HashMap<>(userAliases);
            this.usernameHistory = new HashSet<>(usernameHistory);
            localReferences.forEach((kind, references) -> this.localReferences.put(kind, new HashMap<>(references)));
            unpublishedReferences.forEach((kind, references) -> this.unpublishedReferences.put(kind, new HashMap<>(references)));
        }
    }

}
