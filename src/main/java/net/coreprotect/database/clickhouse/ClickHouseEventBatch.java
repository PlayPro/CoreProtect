package net.coreprotect.database.clickhouse;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import net.coreprotect.database.DatabaseType;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.utility.serialize.BlockMetaCodec;
import net.coreprotect.utility.serialize.EntityDataCodec;

public final class ClickHouseEventBatch implements AutoCloseable {

    private static final long SINGLETON_ROW_ID = 1L;

    private final ClickHouseBatchIdentity identity;
    private final ClickHouseRowIdAllocator rowIdAllocator;
    private final ClickHouseRowBinaryBuffer rows = new ClickHouseRowBinaryBuffer(ClickHouseSchema.EVENT_COLUMNS, ClickHouseSchema.EVENT_COLUMN_TYPES);
    private final EnumMap<ClickHouseFamily, HashSet<Long>> versionRowIds = new EnumMap<>(ClickHouseFamily.class);
    private final TreeMap<Integer, Integer> partitionRowCounts = new TreeMap<>();
    private int eventCount;
    private int duplicateVersionCount;
    private int currentTime;
    private int currentWorldId;
    private int currentX;
    private int currentZ;
    private ClickHouseEventPointer lastPointer;
    private boolean sealed;
    private boolean closed;

    ClickHouseEventBatch(ClickHouseBatchIdentity identity, ClickHouseRowIdAllocator rowIdAllocator) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.rowIdAllocator = Objects.requireNonNull(rowIdAllocator, "rowIdAllocator");
    }

    public long addArtMap(int id, String art) throws SQLException {
        return addNamedMap(ClickHouseFamily.ART_MAP, id, art);
    }

    public long addBlock(int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws SQLException {
        long rowId = beginRow(ClickHouseFamily.BLOCK, time);
        setLocation(userId, worldId, x, y, z);
        set("type", type);
        set("data", (long) data);
        setBinary("meta", meta);
        setBinary("blockdata", blockData);
        set("action", action);
        set("rolled_back", rolledBack);
        commitRow(ClickHouseFamily.BLOCK, rowId);
        return rowId;
    }

    public long addChat(int time, int userId, int worldId, int x, int y, int z, String message) throws SQLException {
        return addMessage(ClickHouseFamily.CHAT, time, userId, worldId, x, y, z, message);
    }

    public long addCommand(int time, int userId, int worldId, int x, int y, int z, String message) throws SQLException {
        return addMessage(ClickHouseFamily.COMMAND, time, userId, worldId, x, y, z, message);
    }

    public long addContainer(int time, int userId, int worldId, int x, int y, int z, int type, int data, int amount, byte[] metadata, int action, int rolledBack) throws SQLException {
        return addContainer(ClickHouseFamily.CONTAINER, time, userId, 0, worldId, x, y, z, type, data, amount, metadata, action, rolledBack);
    }

    public long addEntityContainer(int time, int userId, int entitySpawnRowId, int worldId, int x, int y, int z, int type, int data, int amount, byte[] metadata, int action, int rolledBack) throws SQLException {
        return addContainer(ClickHouseFamily.ENTITY_CONTAINER, time, userId, entitySpawnRowId, worldId, x, y, z, type, data, amount, metadata, action, rolledBack);
    }

    public long addEntityInteraction(int time, int userId, int entitySpawnRowId, int worldId, int x, int y, int z, int type, int action, byte[] metadata, int rolledBack) throws SQLException {
        long rowId = beginRow(ClickHouseFamily.ENTITY_INTERACTION, time);
        setLocation(userId, worldId, x, y, z);
        set("entity_spawn_rowid", (long) entitySpawnRowId);
        set("type", type);
        set("action", action);
        setBinary("metadata", metadata);
        set("rolled_back", rolledBack);
        commitRow(ClickHouseFamily.ENTITY_INTERACTION, rowId);
        return rowId;
    }

    public long addItem(int time, int userId, int worldId, int x, int y, int z, int type, byte[] data, int amount, int action, int rolledBack) throws SQLException {
        long rowId = beginRow(ClickHouseFamily.ITEM, time);
        setLocation(userId, worldId, x, y, z);
        set("type", type);
        setBinary("payload", data);
        set("amount", amount);
        set("action", action);
        set("rolled_back", rolledBack);
        commitRow(ClickHouseFamily.ITEM, rowId);
        return rowId;
    }

    public long addDatabaseLock(int time, int status) throws SQLException {
        return addDatabaseLockVersion(SINGLETON_ROW_ID, time, status);
    }

    public long addDatabaseLockVersion(long rowId, int time, int status) throws SQLException {
        beginExplicitRow(ClickHouseFamily.DATABASE_LOCK, rowId, time);
        set("status", status);
        set("database_lock_time", time);
        commitRow(ClickHouseFamily.DATABASE_LOCK, rowId);
        return rowId;
    }

    public long addEntity(int time, byte[] data) throws SQLException {
        long rowId = beginRow(ClickHouseFamily.ENTITY, time);
        setEntityData("payload", data);
        commitRow(ClickHouseFamily.ENTITY, rowId);
        return rowId;
    }

    public long addEntitySpawn(int time, Long blockRowId, Integer killRowId, UUID uuid, int worldId, int currentWorldId, double originX, double originY, double originZ, double currentX, double currentY, double currentZ, float yaw, float pitch, byte[] data, int removed) throws SQLException {
        long rowId = beginRow(ClickHouseFamily.ENTITY_SPAWN, time);
        set("block_rowid", blockRowId);
        set("kill_rowid", killRowId == null ? null : killRowId.longValue());
        set("block_rowid_present", blockRowId == null ? 0 : 1);
        set("kill_rowid_present", killRowId == null ? 0 : 1);
        set("uuid", Objects.requireNonNull(uuid, "uuid").toString());
        set("wid", worldId);
        set("x", (int) Math.floor(originX));
        set("z", (int) Math.floor(originZ));
        set("current_wid", currentWorldId);
        set("origin_x", originX);
        set("origin_y", originY);
        set("origin_z", originZ);
        set("current_x", currentX);
        set("current_y", currentY);
        set("current_z", currentZ);
        set("yaw", yaw);
        set("pitch", pitch);
        setEntityData("entity_data", data);
        set("entity_data_present", data == null ? 0 : 1);
        set("removed", removed);
        commitRow(ClickHouseFamily.ENTITY_SPAWN, rowId);
        return rowId;
    }

    public long addEntityMap(int id, String entity) throws SQLException {
        return addNamedMap(ClickHouseFamily.ENTITY_MAP, id, entity);
    }

    public long addMaterialMap(int id, String material) throws SQLException {
        return addNamedMap(ClickHouseFamily.MATERIAL_MAP, id, material);
    }

    public long addBlockDataMap(int id, String blockData) throws SQLException {
        long rowId = beginExplicitRow(ClickHouseFamily.BLOCKDATA_MAP, id, 0);
        set("id", id);
        set("text", Objects.requireNonNull(blockData, "blockData"));
        commitRow(ClickHouseFamily.BLOCKDATA_MAP, rowId);
        return rowId;
    }

    public long addSession(int time, int userId, int worldId, int x, int y, int z, int action) throws SQLException {
        long rowId = beginRow(ClickHouseFamily.SESSION, time);
        setLocation(userId, worldId, x, y, z);
        set("action", action);
        commitRow(ClickHouseFamily.SESSION, rowId);
        return rowId;
    }

    public long addSign(int time, int userId, int worldId, int x, int y, int z, int action, int color, int colorSecondary, int data, int waxed, int face, String... lines) throws SQLException {
        if (lines == null || lines.length != 8) {
            throw new IllegalArgumentException("ClickHouse sign events require exactly eight lines");
        }
        long rowId = beginRow(ClickHouseFamily.SIGN, time);
        setLocation(userId, worldId, x, y, z);
        set("action", action);
        set("color", color);
        set("color_secondary", colorSecondary);
        set("sign_data", data);
        set("waxed", waxed);
        set("face", face);
        for (int index = 0; index < lines.length; index++) {
            set("line_" + (index + 1), lines[index]);
        }
        commitRow(ClickHouseFamily.SIGN, rowId);
        return rowId;
    }

    public long addSkull(int time, String owner, String skin) throws SQLException {
        long rowId = beginRow(ClickHouseFamily.SKULL, time);
        set("name", owner == null ? "" : owner);
        set("text", skin == null ? "" : skin);
        commitRow(ClickHouseFamily.SKULL, rowId);
        return rowId;
    }

    public long addUserVersion(long rowId, int time, String userName, String uuid) throws SQLException {
        beginExplicitRow(ClickHouseFamily.USER, rowId, time);
        setUser(userName, uuid);
        commitRow(ClickHouseFamily.USER, rowId);
        return rowId;
    }

    public long addUsernameLog(int time, String uuid, String userName) throws SQLException {
        long rowId = beginRow(ClickHouseFamily.USERNAME_LOG, time);
        setUser(userName, uuid);
        commitRow(ClickHouseFamily.USERNAME_LOG, rowId);
        return rowId;
    }

    public long addVersion(int time, String version) throws SQLException {
        return addVersionRevision(SINGLETON_ROW_ID, time, version);
    }

    public long addVersionRevision(long rowId, int time, String version) throws SQLException {
        beginExplicitRow(ClickHouseFamily.VERSION, rowId, time);
        set("version", Objects.requireNonNull(version, "version"));
        commitRow(ClickHouseFamily.VERSION, rowId);
        return rowId;
    }

    public long addWorld(int id, String world) throws SQLException {
        return addNamedMap(ClickHouseFamily.WORLD, id, world);
    }

    public void addCompatibilityRow(ClickHouseFamily family, long rowId, Map<String, ?> values) throws SQLException {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(values, "values");
        if (family == ClickHouseFamily.DATABASE_LOCK || family == ClickHouseFamily.VERSION) {
            throw new IllegalArgumentException("ClickHouse core data requires a dedicated writer: " + family.getTableName());
        }
        int time = numberOrZero(values.get("time")).intValue();
        beginExplicitRow(family, rowId, time);
        if (family == ClickHouseFamily.ENTITY_SPAWN) {
            set("x", originKey(values.get("origin_x")));
            set("z", originKey(values.get("origin_z")));
        }
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String canonicalColumn = Objects.requireNonNull(entry.getKey(), "compatibility column");
            if (canonicalColumn.equals("time")) {
                continue;
            }
            if (isReservedCompatibilityColumn(canonicalColumn)) {
                throw new IllegalArgumentException("Reserved ClickHouse compatibility column: " + canonicalColumn);
            }
            Object value = entry.getValue();
            if (canonicalColumn.equals("data") && (family == ClickHouseFamily.ENTITY || family == ClickHouseFamily.ENTITY_SPAWN)) {
                value = entityData(value);
            }
            else if (canonicalColumn.equals("meta") && family == ClickHouseFamily.BLOCK) {
                value = blockMetadata(value);
            }
            String physicalColumn = compatibilityColumn(family, canonicalColumn);
            set(physicalColumn, value);
            if (family == ClickHouseFamily.ENTITY_SPAWN) {
                if (canonicalColumn.equals("block_rowid")) {
                    set("block_rowid_present", value == null ? 0 : 1);
                }
                else if (canonicalColumn.equals("kill_rowid")) {
                    set("kill_rowid_present", value == null ? 0 : 1);
                }
                else if (canonicalColumn.equals("data")) {
                    set("entity_data_present", value == null ? 0 : 1);
                }
            }
        }
        commitRow(family, rowId);
    }

    public int size() {
        return eventCount;
    }

    int logicalSize() {
        return eventCount - duplicateVersionCount;
    }

    Map<Integer, Integer> seal(ClickHouseStateBatch state) throws SQLException {
        if (sealed) {
            return partitionRowCounts;
        }
        ensureWritable();
        ClickHouseStateBatch requiredState = Objects.requireNonNull(state, "state");
        requiredState.appendTo(rows, eventCount, partitionRowCounts);
        appendBatchReceipts(eventCount + requiredState.getRollbackCount() + requiredState.getEntityStateCount());
        rows.seal();
        sealed = true;
        return partitionRowCounts;
    }

    InputStream openRows() {
        if (!sealed) {
            throw new IllegalStateException("ClickHouse event batch is not sealed");
        }
        return rows.openStream();
    }

    InputStream openRows(int partitionId) {
        if (!sealed) {
            throw new IllegalStateException("ClickHouse event batch is not sealed");
        }
        return rows.openStream(partitionId);
    }

    ClickHouseEventPointer getLastPointer() {
        if (lastPointer == null) {
            throw new IllegalStateException("ClickHouse event batch has no rows");
        }
        return lastPointer;
    }

    Checkpoint checkpoint() {
        ensureWritable();
        return new Checkpoint(rows.checkpoint(), eventCount, duplicateVersionCount, versionRowIds, partitionRowCounts, lastPointer);
    }

    void restore(Checkpoint checkpoint) {
        ensureWritable();
        Objects.requireNonNull(checkpoint, "checkpoint");
        rows.restore(checkpoint.bufferSize);
        eventCount = checkpoint.eventCount;
        duplicateVersionCount = checkpoint.duplicateVersionCount;
        versionRowIds.clear();
        checkpoint.versionRowIds.forEach((family, rowIds) -> versionRowIds.put(family, new HashSet<>(rowIds)));
        partitionRowCounts.clear();
        partitionRowCounts.putAll(checkpoint.partitionRowCounts);
        lastPointer = checkpoint.lastPointer;
    }

    @Override
    public void close() {
        closed = true;
        sealed = true;
        rows.close();
    }

    private long addNamedMap(ClickHouseFamily family, int id, String name) throws SQLException {
        long rowId = beginExplicitRow(family, id, 0);
        set("id", id);
        set("name", Objects.requireNonNull(name, family.getTableName()));
        commitRow(family, rowId);
        return rowId;
    }

    private long addMessage(ClickHouseFamily family, int time, int userId, int worldId, int x, int y, int z, String message) throws SQLException {
        long rowId = beginRow(family, time);
        setLocation(userId, worldId, x, y, z);
        set("message", Objects.requireNonNull(message, "message"));
        commitRow(family, rowId);
        return rowId;
    }

    private long addContainer(ClickHouseFamily family, int time, int userId, int entitySpawnRowId, int worldId, int x, int y, int z, int type, int data, int amount, byte[] metadata, int action, int rolledBack) throws SQLException {
        long rowId = beginRow(family, time);
        setLocation(userId, worldId, x, y, z);
        set("entity_spawn_rowid", (long) entitySpawnRowId);
        set("type", type);
        set("data", (long) data);
        set("amount", amount);
        setBinary("metadata", metadata);
        set("action", action);
        set("rolled_back", rolledBack);
        commitRow(family, rowId);
        return rowId;
    }

    private long beginRow(ClickHouseFamily family, int time) throws SQLException {
        return beginRow(family, rowIdAllocator.nextRowId(family), time);
    }

    private long beginExplicitRow(ClickHouseFamily family, long rowId, int time) {
        ensureWritable();
        rowIdAllocator.observeRowId(family, rowId);
        return beginRow(family, rowId, time);
    }

    private long beginRow(ClickHouseFamily family, long rowId, int time) {
        ensureWritable();
        Objects.requireNonNull(family, "family");
        if (eventCount == Integer.MAX_VALUE) {
            throw new IllegalStateException("ClickHouse event batch exceeds the supported row count");
        }
        if (rowId < 1) {
            throw new IllegalArgumentException("ClickHouse compatibility row IDs must be positive");
        }
        rows.beginRow();
        currentTime = time;
        currentWorldId = 0;
        currentX = 0;
        currentZ = 0;
        set("batch_sequence", identity.getBatchSequence());
        set("batch_id", identity.getBatchId());
        set("batch_ordinal", eventCount);
        set("family", family.getTableName());
        set("rowid", rowId);
        set("time", time);
        return rowId;
    }

    private void setLocation(int userId, int worldId, int x, int y, int z) {
        set("user_id", userId);
        set("wid", worldId);
        set("x", x);
        set("y", y);
        set("z", z);
    }

    private void setUser(String userName, String uuid) {
        set("user_name", Objects.requireNonNull(userName, "userName"));
        set("uuid", uuid == null || uuid.isEmpty() ? null : uuid);
    }

    private void setBinary(String column, byte[] value) {
        set(column, value);
    }

    private void setEntityData(String column, byte[] value) {
        if (value != null && !EntityDataCodec.isEncoded(value)) {
            throw new IllegalArgumentException("Entity data does not use the CoreProtect binary format");
        }
        set(column, value);
    }

    private static Object entityData(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            if (!EntityDataCodec.isEncoded((byte[]) value)) {
                throw new IllegalArgumentException("Entity data does not use the CoreProtect binary format");
            }
            return value;
        }
        throw new IllegalArgumentException("ClickHouse entity data must be binary");
    }

    private static Object blockMetadata(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof byte[])) {
            throw new IllegalArgumentException("ClickHouse block metadata must be binary");
        }
        byte[] data = (byte[]) value;
        if (!isJavaSerialization(data) && !BlockMetaCodec.isEncoded(data)) {
            return data;
        }
        try {
            return BlockStatement.transcodeMetadata(data, DatabaseType.CLICKHOUSE);
        }
        catch (Exception exception) {
            throw new IllegalArgumentException("Unable to transcode ClickHouse block metadata", exception);
        }
    }

    private static boolean isJavaSerialization(byte[] data) {
        return data.length >= 2 && data[0] == (byte) 0xac && data[1] == (byte) 0xed;
    }

    private void set(String column, Object value) {
        Object storedValue = value;
        if (column.equals("wid") || column.equals("x") || column.equals("z")) {
            storedValue = numberOrZero(value);
            rows.set(column + "_present", value == null ? 0 : 1);
        }
        rows.set(column, storedValue);
        if (storedValue == null) {
            return;
        }
        switch (column) {
            case "time":
                currentTime = ((Number) storedValue).intValue();
                break;
            case "wid":
                currentWorldId = ((Number) storedValue).intValue();
                break;
            case "x":
                currentX = ((Number) storedValue).intValue();
                break;
            case "z":
                currentZ = ((Number) storedValue).intValue();
                break;
            default:
                break;
        }
    }

    private void commitRow(ClickHouseFamily family, long rowId) throws SQLException {
        int partitionId = ClickHouseSchema.eventPartitionId(family, currentTime);
        rows.commitRow(family.getTableName() + " event", partitionId);
        partitionRowCounts.merge(partitionId, 1, Math::addExact);
        if (isVersionedFamily(family) && !versionRowIds.computeIfAbsent(family, ignored -> new HashSet<>()).add(rowId)) {
            duplicateVersionCount++;
        }
        lastPointer = new ClickHouseEventPointer(identity.getDatasetId(), family, identity.getBatchSequence(), eventCount, rowId, currentTime, currentWorldId, currentX, currentZ);
        eventCount++;
    }

    private void appendBatchReceipts(int firstOrdinal) throws SQLException {
        int ordinal = firstOrdinal;
        for (Map.Entry<Integer, Integer> partition : partitionRowCounts.entrySet()) {
            rows.beginRow();
            rows.set("batch_sequence", identity.getBatchSequence());
            rows.set("batch_id", identity.getBatchId());
            rows.set("batch_ordinal", ordinal++);
            rows.set("family", ClickHouseSchema.BATCH_RECEIPT_FAMILY);
            rows.set("rowid", identity.getBatchSequence());
            rows.set("wid", partition.getKey());
            rows.set("amount", partition.getValue());
            rows.commitRow("batch receipt", partition.getKey());
        }
    }

    private static boolean isVersionedFamily(ClickHouseFamily family) {
        return family == ClickHouseFamily.DATABASE_LOCK || family == ClickHouseFamily.USER || family == ClickHouseFamily.VERSION;
    }

    private static Number numberOrZero(Object value) {
        return value == null ? Integer.valueOf(0) : (Number) value;
    }

    private static Integer originKey(Object value) {
        return value == null ? null : (int) Math.floor(((Number) value).doubleValue());
    }

    private static boolean isReservedCompatibilityColumn(String column) {
        switch (column) {
            case "batch_sequence":
            case "batch_id":
            case "batch_ordinal":
            case "family":
            case "rowid":
                return true;
            default:
                return false;
        }
    }

    private static String compatibilityColumn(ClickHouseFamily family, String column) {
        if (column.equals("user")) {
            return family == ClickHouseFamily.USER || family == ClickHouseFamily.USERNAME_LOG ? "user_name" : "user_id";
        }
        if (column.equals("data")) {
            if (family == ClickHouseFamily.ITEM || family == ClickHouseFamily.ENTITY) {
                return "payload";
            }
            if (family == ClickHouseFamily.ENTITY_SPAWN) {
                return "entity_data";
            }
            if (family == ClickHouseFamily.BLOCKDATA_MAP) {
                return "text";
            }
            if (family == ClickHouseFamily.SIGN) {
                return "sign_data";
            }
        }
        if (family == ClickHouseFamily.ENTITY_SPAWN && (column.equals("x") || column.equals("y") || column.equals("z"))) {
            return "current_" + column;
        }
        if ((family == ClickHouseFamily.ART_MAP && column.equals("art"))
                || (family == ClickHouseFamily.ENTITY_MAP && column.equals("entity"))
                || (family == ClickHouseFamily.MATERIAL_MAP && column.equals("material"))
                || (family == ClickHouseFamily.WORLD && column.equals("world"))
                || (family == ClickHouseFamily.SKULL && column.equals("owner"))) {
            return "name";
        }
        if (family == ClickHouseFamily.SKULL && column.equals("skin")) {
            return "text";
        }
        return column;
    }

    private void ensureWritable() {
        if (closed) {
            throw new IllegalStateException("ClickHouse event batch is closed");
        }
        if (sealed) {
            throw new IllegalStateException("ClickHouse event batch is already sealed");
        }
    }

    static final class Checkpoint {

        private final int bufferSize;
        private final int eventCount;
        private final int duplicateVersionCount;
        private final EnumMap<ClickHouseFamily, HashSet<Long>> versionRowIds = new EnumMap<>(ClickHouseFamily.class);
        private final TreeMap<Integer, Integer> partitionRowCounts;
        private final ClickHouseEventPointer lastPointer;

        private Checkpoint(int bufferSize, int eventCount, int duplicateVersionCount, EnumMap<ClickHouseFamily, HashSet<Long>> versionRowIds,
                Map<Integer, Integer> partitionRowCounts, ClickHouseEventPointer lastPointer) {
            this.bufferSize = bufferSize;
            this.eventCount = eventCount;
            this.duplicateVersionCount = duplicateVersionCount;
            versionRowIds.forEach((family, rowIds) -> this.versionRowIds.put(family, new HashSet<>(rowIds)));
            this.partitionRowCounts = new TreeMap<>(partitionRowCounts);
            this.lastPointer = lastPointer;
        }
    }

}
