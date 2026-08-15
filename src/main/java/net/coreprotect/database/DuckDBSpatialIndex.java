package net.coreprotect.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.coreprotect.utility.DatabaseUtils;

public final class DuckDBSpatialIndex {

    static final int SEGMENT_ROWS = 122_880;

    private static final int FILTER_BYTES = 4 * 1024;
    private static final int FILTER_BITS = FILTER_BYTES * 8;
    private static final int FILTER_HASHES = 4;
    private static final int MAXIMUM_QUERY_CHUNKS = 4_096;
    private static final int MAXIMUM_ENTITY_KEYS = 4_096;
    private static final int MAXIMUM_REQUIRED_ROW_IDS = 4_096;
    private static final int MAXIMUM_PREDICATE_RANGES = 256;
    private static final long MAXIMUM_FILTER_PROBES = 2_000_000L;
    private static final String DEFAULT_ALIAS = "duckdb_spatial_rows";
    private static final Object STATE_LOCK = new Object();

    private static String runtimeKey;
    private static WriterState runtimeState;
    private static IndexCache indexCache;
    private static long runtimeGeneration;

    private DuckDBSpatialIndex() {
        throw new IllegalStateException("Database class");
    }

    static void createTable(String prefix, Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "duckdb_spatial_index ("
                + "table_id TINYINT NOT NULL,"
                + "start_rowid BIGINT NOT NULL,"
                + "end_rowid BIGINT NOT NULL,"
                + "row_count INTEGER NOT NULL,"
                + "chunks BLOB NOT NULL,"
                + "entities BLOB,"
                + "PRIMARY KEY(table_id,start_rowid))");
        reset();
    }

    static Transaction begin(Connection connection, String prefix) throws SQLException {
        String key = databaseKey(connection, prefix);
        synchronized (STATE_LOCK) {
            if (runtimeState == null || !key.equals(runtimeKey)) {
                runtimeState = loadWriterState(connection, prefix);
                runtimeKey = key;
                runtimeGeneration++;
            }
            return new Transaction(key, prefix, runtimeGeneration, runtimeState.copy());
        }
    }

    public static Builder builder(String prefix) {
        return new Builder(prefix);
    }

    public static String tableExpression(Connection connection, String prefix, String table, int worldId, int minimumX, int maximumX, int minimumZ, int maximumZ) {
        return tableExpression(connection, prefix, table, worldId, minimumX, maximumX, minimumZ, maximumZ, Collections.emptySet(), Collections.emptySet(), DEFAULT_ALIAS);
    }

    public static String tableExpression(Connection connection, String prefix, String table, int worldId, int minimumX, int maximumX, int minimumZ, int maximumZ, Collection<Integer> entitySpawnRowIds, Collection<Long> requiredRowIds) {
        return tableExpression(connection, prefix, table, worldId, minimumX, maximumX, minimumZ, maximumZ, entitySpawnRowIds, requiredRowIds, DEFAULT_ALIAS);
    }

    public static String tableExpression(Connection connection, String prefix, String table, int worldId, int minimumX, int maximumX, int minimumZ, int maximumZ, Collection<Integer> entitySpawnRowIds, Collection<Long> requiredRowIds, String alias) {
        List<RowRange> ranges = spatialRanges(connection, prefix, table, worldId, minimumX, maximumX, minimumZ, maximumZ, entitySpawnRowIds, requiredRowIds);
        return tableExpression(prefix, table, ranges, alias);
    }

    public static String entityTableExpression(Connection connection, String prefix, String table, Collection<Integer> entitySpawnRowIds, String alias) {
        List<RowRange> ranges = entityRanges(connection, prefix, table, entitySpawnRowIds);
        return tableExpression(prefix, table, ranges, alias);
    }

    private static String tableExpression(String prefix, String table, List<RowRange> ranges, String alias) {
        String tableName = prefix + table;
        if (ranges == null) {
            return tableName + alias(alias);
        }

        StringBuilder query = new StringBuilder("(");
        for (RowRange range : ranges) {
            if (query.length() > 1) {
                query.append(" UNION ALL ");
            }
            query.append("SELECT * FROM ").append(tableName).append(" WHERE ");
            if (range.end == Long.MAX_VALUE) {
                query.append("rowid > ").append(range.start - 1L);
            }
            else {
                query.append("rowid BETWEEN ").append(range.start).append(" AND ").append(range.end);
            }
        }
        return query.append(')').append(alias(alias == null || alias.isEmpty() ? DEFAULT_ALIAS : alias)).toString();
    }

    static List<RowRange> spatialRanges(Connection connection, String prefix, String table, int worldId, int minimumX, int maximumX, int minimumZ, int maximumZ, Collection<Integer> entitySpawnRowIds, Collection<Long> requiredRowIds) {
        Source source = Source.fromTable(table);
        if (source == null) {
            return null;
        }
        entitySpawnRowIds = entitySpawnRowIds == null ? Collections.emptySet() : entitySpawnRowIds;
        requiredRowIds = requiredRowIds == null ? Collections.emptySet() : requiredRowIds;

        long minimumChunkX = Math.floorDiv(minimumX, 16);
        long maximumChunkX = Math.floorDiv(maximumX, 16);
        long minimumChunkZ = Math.floorDiv(minimumZ, 16);
        long maximumChunkZ = Math.floorDiv(maximumZ, 16);
        long width = maximumChunkX - minimumChunkX + 1L;
        long depth = maximumChunkZ - minimumChunkZ + 1L;
        if (width <= 0L || depth <= 0L || width > MAXIMUM_QUERY_CHUNKS || depth > MAXIMUM_QUERY_CHUNKS || width * depth > MAXIMUM_QUERY_CHUNKS) {
            return null;
        }
        if (entitySpawnRowIds.size() > MAXIMUM_ENTITY_KEYS || requiredRowIds.size() > MAXIMUM_REQUIRED_ROW_IDS) {
            return null;
        }

        long[] chunks = new long[(int) (width * depth)];
        int chunkIndex = 0;
        for (long chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (long chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                chunks[chunkIndex++] = spatialKey(worldId, (int) chunkX, (int) chunkZ);
            }
        }
        long[] entities = new long[entitySpawnRowIds.size()];
        int entityIndex = 0;
        for (Integer entitySpawnRowId : entitySpawnRowIds) {
            if (entitySpawnRowId != null && entitySpawnRowId > 0) {
                entities[entityIndex++] = entitySpawnRowId;
            }
        }
        if (entityIndex != entities.length) {
            entities = Arrays.copyOf(entities, entityIndex);
        }
        long[] requiredRows = new long[requiredRowIds.size()];
        int requiredIndex = 0;
        for (Long rowId : requiredRowIds) {
            if (rowId != null && rowId > 0L) {
                requiredRows[requiredIndex++] = rowId;
            }
        }
        if (requiredIndex != requiredRows.length) {
            requiredRows = Arrays.copyOf(requiredRows, requiredIndex);
        }
        Arrays.sort(requiredRows);

        try {
            IndexSnapshot snapshot = snapshots(connection, prefix).get(source);
            return ranges(snapshot, chunks, entities, requiredRows);
        }
        catch (SQLException exception) {
            return null;
        }
    }

    static List<RowRange> entityRanges(Connection connection, String prefix, String table, Collection<Integer> entitySpawnRowIds) {
        Source source = Source.fromTable(table);
        if (source == null || !source.entityRows || entitySpawnRowIds == null || entitySpawnRowIds.isEmpty() || entitySpawnRowIds.size() > MAXIMUM_ENTITY_KEYS) {
            return null;
        }

        long[] entities = new long[entitySpawnRowIds.size()];
        int entityIndex = 0;
        for (Integer entitySpawnRowId : entitySpawnRowIds) {
            if (entitySpawnRowId != null && entitySpawnRowId > 0) {
                entities[entityIndex++] = entitySpawnRowId;
            }
        }
        if (entityIndex == 0) {
            return null;
        }
        if (entityIndex != entities.length) {
            entities = Arrays.copyOf(entities, entityIndex);
        }

        try {
            IndexSnapshot snapshot = snapshots(connection, prefix).get(source);
            return ranges(snapshot, new long[0], entities, new long[0]);
        }
        catch (SQLException exception) {
            return null;
        }
    }

    private static List<RowRange> ranges(IndexSnapshot snapshot, long[] chunks, long[] entities, long[] requiredRowIds) {
        if (snapshot == null || !snapshot.usable || snapshot.segments.isEmpty()) {
            return null;
        }
        long probesPerSegment = (long) chunks.length + entities.length;
        if (probesPerSegment * snapshot.segments.size() > MAXIMUM_FILTER_PROBES) {
            return null;
        }

        List<RowRange> ranges = new ArrayList<>();
        int matchedSegments = 0;
        for (Segment segment : snapshot.segments) {
            boolean spatialMatch = chunks.length > 0 && segment.chunks.mightContainAny(chunks);
            boolean entityMatch = entities.length > 0 && segment.entities != null && segment.entities.mightContainAny(entities);
            boolean requiredRowMatch = containsAny(segment, requiredRowIds);
            if (spatialMatch || entityMatch || requiredRowMatch) {
                matchedSegments++;
                addRange(ranges, segment.startRowId, segment.endRowId);
                if (ranges.size() > MAXIMUM_PREDICATE_RANGES) {
                    return null;
                }
            }
        }
        if (matchedSegments == snapshot.segments.size()) {
            return null;
        }

        addRange(ranges, snapshot.lastIndexedRowId + 1L, Long.MAX_VALUE);
        return ranges;
    }

    private static String alias(String alias) {
        return alias == null || alias.isEmpty() ? "" : " AS " + alias;
    }

    private static boolean containsAny(Segment segment, long[] rowIds) {
        int index = Arrays.binarySearch(rowIds, segment.startRowId);
        if (index < 0) {
            index = -index - 1;
        }
        return index < rowIds.length && rowIds[index] <= segment.endRowId;
    }

    public static void reset() {
        synchronized (STATE_LOCK) {
            runtimeKey = null;
            runtimeState = null;
            indexCache = null;
            runtimeGeneration++;
        }
    }

    private static WriterState loadWriterState(Connection connection, String prefix) throws SQLException {
        WriterState state = new WriterState();
        for (Source source : Source.values()) {
            long indexedThrough = 0L;
            String highWaterSql = "SELECT COALESCE(MAX(end_rowid),0) FROM " + prefix + "duckdb_spatial_index WHERE table_id=" + source.id;
            try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(highWaterSql)) {
                if (resultSet.next()) {
                    indexedThrough = resultSet.getLong(1);
                }
            }

            String tailSql = "SELECT rowid,wid,x,z" + (source.entityRows ? ",entity_spawn_rowid" : "") + " FROM " + prefix + source.table + " WHERE rowid>? ORDER BY rowid";
            try (PreparedStatement statement = connection.prepareStatement(tailSql)) {
                statement.setLong(1, indexedThrough);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        Integer entitySpawnRowId = source.entityRows ? resultSet.getInt("entity_spawn_rowid") : null;
                        state.accumulator(source).add(resultSet.getLong("rowid"), resultSet.getInt("wid"), resultSet.getInt("x"), resultSet.getInt("z"), entitySpawnRowId);
                    }
                }
            }
        }
        return state;
    }

    private static Map<Source, IndexSnapshot> snapshots(Connection connection, String prefix) throws SQLException {
        String key = databaseKey(connection, prefix);
        synchronized (STATE_LOCK) {
            if (indexCache != null && key.equals(indexCache.key)) {
                return indexCache.snapshots;
            }
        }

        Map<Source, IndexSnapshot> loaded = new EnumMap<>(Source.class);
        for (Source source : Source.values()) {
            loaded.put(source, new IndexSnapshot());
        }
        String sql = "SELECT table_id,start_rowid,end_rowid,row_count,chunks,entities FROM " + prefix + "duckdb_spatial_index ORDER BY table_id,start_rowid";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                Source source = Source.fromId(resultSet.getInt("table_id"));
                if (source == null) {
                    continue;
                }
                IndexSnapshot snapshot = loaded.get(source);
                long startRowId = resultSet.getLong("start_rowid");
                long endRowId = resultSet.getLong("end_rowid");
                int rowCount = resultSet.getInt("row_count");
                byte[] chunks = DatabaseUtils.getBytes(resultSet, "chunks");
                byte[] entities = DatabaseUtils.getBytes(resultSet, "entities");
                if (startRowId <= 0L || endRowId < startRowId || rowCount <= 0 || chunks == null || chunks.length != FILTER_BYTES
                        || (source.entityRows && (entities == null || entities.length != FILTER_BYTES))
                        || (!source.entityRows && entities != null && entities.length != FILTER_BYTES)) {
                    snapshot.usable = false;
                    continue;
                }
                snapshot.segments.add(new Segment(startRowId, endRowId, rowCount, new BloomFilter(chunks), entities == null ? null : new BloomFilter(entities)));
                snapshot.lastIndexedRowId = Math.max(snapshot.lastIndexedRowId, endRowId);
            }
        }

        synchronized (STATE_LOCK) {
            indexCache = new IndexCache(key, loaded);
            return indexCache.snapshots;
        }
    }

    private static void addRange(List<RowRange> ranges, long start, long end) {
        if (!ranges.isEmpty()) {
            RowRange previous = ranges.get(ranges.size() - 1);
            if (previous.end == Long.MAX_VALUE || start <= previous.end + 1L) {
                previous.end = Math.max(previous.end, end);
                return;
            }
        }
        ranges.add(new RowRange(start, end));
    }

    private static String databaseKey(Connection connection, String prefix) throws SQLException {
        return connection.getMetaData().getURL() + '\n' + prefix;
    }

    private static long spatialKey(int worldId, int chunkX, int chunkZ) {
        long value = Integer.toUnsignedLong(worldId);
        value = mix64(value ^ (Integer.toUnsignedLong(chunkX) * 0x9E3779B97F4A7C15L));
        return mix64(value ^ (Integer.toUnsignedLong(chunkZ) * 0xC2B2AE3D27D4EB4FL));
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    static final class Transaction {

        private final String key;
        private final String prefix;
        private final long generation;
        private final WriterState state;
        private final Map<Source, List<PendingLocation>> generatedRows = new EnumMap<>(Source.class);
        private boolean flushed;
        private boolean metadataChanged;

        private Transaction(String key, String prefix, long generation, WriterState state) {
            this.key = key;
            this.prefix = prefix;
            this.generation = generation;
            this.state = state;
        }

        void addBlock(long rowId, int worldId, int x, int z) throws SQLException {
            state.accumulator(Source.BLOCK).add(rowId, worldId, x, z);
        }

        void addGenerated(String table, int worldId, int x, int z, Integer entitySpawnRowId) {
            Source source = Source.fromTable(table);
            if (source != null && source != Source.BLOCK) {
                generatedRows.computeIfAbsent(source, ignored -> new ArrayList<>()).add(new PendingLocation(worldId, x, z, entitySpawnRowId));
            }
        }

        void flush(Connection connection) throws SQLException {
            if (flushed) {
                return;
            }
            for (Map.Entry<Source, List<PendingLocation>> entry : generatedRows.entrySet()) {
                Source source = entry.getKey();
                List<PendingLocation> locations = entry.getValue();
                long lastRowId = sequenceValue(connection, prefix, source.table);
                long rowId = lastRowId - locations.size() + 1L;
                for (PendingLocation location : locations) {
                    state.accumulator(source).add(rowId++, location.worldId, location.x, location.z, location.entitySpawnRowId);
                }
            }
            metadataChanged = state.writeCompleted(connection, prefix);
            flushed = true;
        }

        void publish() {
            synchronized (STATE_LOCK) {
                if (!flushed || !key.equals(runtimeKey) || generation != runtimeGeneration) {
                    runtimeKey = null;
                    runtimeState = null;
                    indexCache = null;
                    runtimeGeneration++;
                    return;
                }
                runtimeState = state;
                runtimeGeneration++;
                if (metadataChanged) {
                    indexCache = null;
                }
            }
        }
    }

    public static final class Builder {

        private final String prefix;
        private final WriterState state = new WriterState();

        private Builder(String prefix) {
            this.prefix = prefix;
        }

        public void add(String table, long rowId, int worldId, int x, int z) throws SQLException {
            add(table, rowId, worldId, x, z, null);
        }

        public void add(String table, long rowId, int worldId, int x, int z, Integer entitySpawnRowId) throws SQLException {
            Source source = Source.fromTable(table);
            if (source != null) {
                state.accumulator(source).add(rowId, worldId, x, z, entitySpawnRowId);
            }
        }

        public void writeCompleted(Connection connection) throws SQLException {
            if (state.writeCompleted(connection, prefix)) {
                invalidateExternalWrite(connection, prefix);
            }
        }

        public boolean finish(Connection connection, String table) throws SQLException {
            Source source = Source.fromTable(table);
            if (source == null) {
                return false;
            }
            Accumulator accumulator = state.accumulator(source);
            accumulator.finish();
            boolean changed = state.writeCompleted(connection, prefix);
            if (changed) {
                invalidateExternalWrite(connection, prefix);
            }
            return changed;
        }
    }

    private static void invalidateExternalWrite(Connection connection, String prefix) throws SQLException {
        String key = databaseKey(connection, prefix);
        synchronized (STATE_LOCK) {
            if (key.equals(runtimeKey)) {
                runtimeKey = null;
                runtimeState = null;
                runtimeGeneration++;
            }
            if (indexCache != null && key.equals(indexCache.key)) {
                indexCache = null;
            }
        }
    }

    private static long sequenceValue(Connection connection, String prefix, String table) throws SQLException {
        String sql = "SELECT currval('" + prefix + table + "_rowid_seq')";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new SQLException("DuckDB did not return the " + table + " row ID sequence value");
            }
            return resultSet.getLong(1);
        }
    }

    private static final class WriterState {

        private final Map<Source, Accumulator> accumulators = new EnumMap<>(Source.class);

        private Accumulator accumulator(Source source) {
            return accumulators.computeIfAbsent(source, Accumulator::new);
        }

        private WriterState copy() {
            WriterState copy = new WriterState();
            for (Map.Entry<Source, Accumulator> entry : accumulators.entrySet()) {
                copy.accumulators.put(entry.getKey(), entry.getValue().copy());
            }
            return copy;
        }

        private boolean writeCompleted(Connection connection, String prefix) throws SQLException {
            List<PendingSegment> pending = new ArrayList<>();
            for (Map.Entry<Source, Accumulator> entry : accumulators.entrySet()) {
                for (Segment segment : entry.getValue().completed) {
                    pending.add(new PendingSegment(entry.getKey(), segment));
                }
            }
            if (pending.isEmpty()) {
                return false;
            }

            String sql = "INSERT INTO " + prefix + "duckdb_spatial_index (table_id,start_rowid,end_rowid,row_count,chunks,entities) VALUES (?,?,?,?,?,?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (PendingSegment value : pending) {
                    statement.setInt(1, value.source.id);
                    statement.setLong(2, value.segment.startRowId);
                    statement.setLong(3, value.segment.endRowId);
                    statement.setInt(4, value.segment.rowCount);
                    statement.setBytes(5, value.segment.chunks.bytes);
                    if (value.segment.entities == null) {
                        statement.setNull(6, Types.BLOB);
                    }
                    else {
                        statement.setBytes(6, value.segment.entities.bytes);
                    }
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            for (Accumulator accumulator : accumulators.values()) {
                accumulator.completed.clear();
            }
            return true;
        }
    }

    private static final class Accumulator {

        private final Source source;
        private final List<Segment> completed = new ArrayList<>();
        private BloomFilter chunks = new BloomFilter();
        private BloomFilter entities;
        private long startRowId;
        private long endRowId;
        private int rowCount;

        private Accumulator(Source source) {
            this.source = source;
            this.entities = source.entityRows ? new BloomFilter() : null;
        }

        private void add(long rowId, int worldId, int x, int z) throws SQLException {
            add(rowId, worldId, x, z, null);
        }

        private void add(long rowId, int worldId, int x, int z, Integer entitySpawnRowId) throws SQLException {
            if (rowId <= 0L || (rowCount > 0 && rowId <= endRowId)) {
                throw new SQLException("DuckDB spatial index rows must have increasing positive row IDs");
            }
            if (source.entityRows && (entitySpawnRowId == null || entitySpawnRowId <= 0)) {
                throw new SQLException("DuckDB entity row-group metadata requires a positive entity spawn row ID");
            }
            if (rowCount == 0) {
                startRowId = rowId;
            }
            endRowId = rowId;
            rowCount++;
            chunks.add(spatialKey(worldId, Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
            if (entities != null) {
                entities.add(entitySpawnRowId);
            }
            if (rowCount == SEGMENT_ROWS) {
                finish();
            }
        }

        private void finish() {
            if (rowCount == 0) {
                return;
            }
            completed.add(new Segment(startRowId, endRowId, rowCount, chunks, entities));
            chunks = new BloomFilter();
            entities = source.entityRows ? new BloomFilter() : null;
            startRowId = 0L;
            endRowId = 0L;
            rowCount = 0;
        }

        private Accumulator copy() {
            Accumulator copy = new Accumulator(source);
            copy.completed.addAll(completed);
            copy.chunks = chunks.copy();
            copy.entities = entities == null ? null : entities.copy();
            copy.startRowId = startRowId;
            copy.endRowId = endRowId;
            copy.rowCount = rowCount;
            return copy;
        }
    }

    private static final class BloomFilter {

        private final byte[] bytes;

        private BloomFilter() {
            this.bytes = new byte[FILTER_BYTES];
        }

        private BloomFilter(byte[] bytes) {
            this.bytes = bytes;
        }

        private void add(long value) {
            long first = mix64(value);
            long second = mix64(value ^ 0xD6E8FEB86659FD93L) | 1L;
            for (int hash = 0; hash < FILTER_HASHES; hash++) {
                int bit = (int) ((first + (second * hash)) & (FILTER_BITS - 1));
                bytes[bit >>> 3] |= (byte) (1 << (bit & 7));
            }
        }

        private boolean mightContainAny(long[] values) {
            for (long value : values) {
                long first = mix64(value);
                long second = mix64(value ^ 0xD6E8FEB86659FD93L) | 1L;
                boolean present = true;
                for (int hash = 0; hash < FILTER_HASHES; hash++) {
                    int bit = (int) ((first + (second * hash)) & (FILTER_BITS - 1));
                    if ((bytes[bit >>> 3] & (1 << (bit & 7))) == 0) {
                        present = false;
                        break;
                    }
                }
                if (present) {
                    return true;
                }
            }
            return false;
        }

        private BloomFilter copy() {
            return new BloomFilter(Arrays.copyOf(bytes, bytes.length));
        }
    }

    private static final class Segment {

        private final long startRowId;
        private final long endRowId;
        private final int rowCount;
        private final BloomFilter chunks;
        private final BloomFilter entities;

        private Segment(long startRowId, long endRowId, int rowCount, BloomFilter chunks, BloomFilter entities) {
            this.startRowId = startRowId;
            this.endRowId = endRowId;
            this.rowCount = rowCount;
            this.chunks = chunks;
            this.entities = entities;
        }
    }

    private static final class PendingSegment {

        private final Source source;
        private final Segment segment;

        private PendingSegment(Source source, Segment segment) {
            this.source = source;
            this.segment = segment;
        }
    }

    private static final class PendingLocation {

        private final int worldId;
        private final int x;
        private final int z;
        private final Integer entitySpawnRowId;

        private PendingLocation(int worldId, int x, int z, Integer entitySpawnRowId) {
            this.worldId = worldId;
            this.x = x;
            this.z = z;
            this.entitySpawnRowId = entitySpawnRowId;
        }
    }

    private static final class IndexCache {

        private final String key;
        private final Map<Source, IndexSnapshot> snapshots;

        private IndexCache(String key, Map<Source, IndexSnapshot> snapshots) {
            this.key = key;
            this.snapshots = snapshots;
        }
    }

    private static final class IndexSnapshot {

        private final List<Segment> segments = new ArrayList<>();
        private long lastIndexedRowId;
        private boolean usable = true;
    }

    static final class RowRange {

        private final long start;
        private long end;

        private RowRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    private enum Source {
        BLOCK(0, "block", false),
        CHAT(1, "chat", false),
        COMMAND(2, "command", false),
        CONTAINER(3, "container", false),
        ENTITY_CONTAINER(4, "entity_container", true),
        ENTITY_INTERACTION(5, "entity_interaction", true),
        ITEM(6, "item", false),
        SESSION(7, "session", false),
        SIGN(8, "sign", false);

        private final int id;
        private final String table;
        private final boolean entityRows;

        Source(int id, String table, boolean entityRows) {
            this.id = id;
            this.table = table;
            this.entityRows = entityRows;
        }

        private static Source fromId(int id) {
            for (Source source : values()) {
                if (source.id == id) {
                    return source;
                }
            }
            return null;
        }

        private static Source fromTable(String table) {
            for (Source source : values()) {
                if (source.table.equals(table)) {
                    return source;
                }
            }
            return null;
        }
    }
}
