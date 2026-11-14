package net.coreprotect.database;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.duckdb.DuckDBConnection;

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
    private static final int INDEX_CHECKPOINT_SEGMENTS = 64;
    private static final int BOOTSTRAP_PAGE_ROWS = 10_000;
    private static final int MAX_SNAPSHOT_LOAD_ATTEMPTS = 3;
    private static final int GAP_BUCKET_SHIFT = 16;
    private static final int MAXIMUM_GAP_BUCKETS = 16;
    private static final int GAP_QUERY_BATCH_SIZE = 8_192;
    private static final String TABLE_DEFINITION = "("
            + "table_id TINYINT NOT NULL,"
            + "start_rowid BIGINT NOT NULL,"
            + "end_rowid BIGINT NOT NULL,"
            + "row_count INTEGER NOT NULL,"
            + "chunks BLOB NOT NULL,"
            + "entities BLOB,"
            + "PRIMARY KEY(table_id,start_rowid))";
    private static final String DEFAULT_ALIAS = "duckdb_spatial_rows";
    private static final Object STATE_LOCK = new Object();
    private static final ReentrantLock INITIALIZATION_LOCK = new ReentrantLock();
    private static final ReentrantLock SNAPSHOT_LOAD_LOCK = new ReentrantLock();

    private static String runtimeKey;
    private static WriterState runtimeState;
    private static IndexCache indexCache;
    private static SnapshotLoad activeLoad;
    private static long runtimeGeneration;
    private static long cacheGeneration;

    private DuckDBSpatialIndex() {
        throw new IllegalStateException("Database class");
    }

    static void createTable(String prefix, Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "duckdb_spatial_index " + TABLE_DEFINITION);
        reset();
    }

    public static boolean repairEmptyGaps(Connection connection, String prefix) throws SQLException {
        String indexTable = prefix + "duckdb_spatial_index";
        if (!hasTable(connection, indexTable) || !hasIndexGaps(connection, indexTable)) {
            return false;
        }

        Map<Source, List<IndexSpan>> spansBySource = loadIndexSpans(connection, indexTable);
        List<Integer> adjustedTables = new ArrayList<>();
        List<Long> oldStarts = new ArrayList<>();
        List<Long> newStarts = new ArrayList<>();
        for (Source source : Source.values()) {
            List<IndexSpan> spans = spansBySource.get(source);
            if (spans == null || spans.isEmpty()) {
                continue;
            }
            String tableName = prefix + source.table;
            if (!hasTable(connection, tableName)) {
                continue;
            }
            collectSourceGapAdjustments(connection, source, tableName, spans, adjustedTables, oldStarts, newStarts);
        }
        if (adjustedTables.isEmpty()) {
            return false;
        }

        rebuildSpatialIndex(connection, indexTable, adjustedTables, oldStarts, newStarts);
        reset();
        return true;
    }

    static Transaction begin(Connection connection, String prefix) throws SQLException {
        String key = databaseKey(connection, prefix);
        synchronized (STATE_LOCK) {
            if (runtimeState != null && key.equals(runtimeKey)) {
                return new Transaction(key, prefix, runtimeGeneration, runtimeState.copy());
            }
        }

        INITIALIZATION_LOCK.lock();
        try {
            while (true) {
                long observedGeneration;
                synchronized (STATE_LOCK) {
                    if (runtimeState != null && key.equals(runtimeKey)) {
                        return new Transaction(key, prefix, runtimeGeneration, runtimeState.copy());
                    }
                    observedGeneration = runtimeGeneration;
                }

                WriterState loaded = loadWriterState(connection, prefix, key, observedGeneration);
                synchronized (STATE_LOCK) {
                    if (observedGeneration != runtimeGeneration) {
                        continue;
                    }
                    runtimeState = loaded;
                    runtimeKey = key;
                    runtimeGeneration++;
                    return new Transaction(key, prefix, runtimeGeneration, runtimeState.copy());
                }
            }
        }
        finally {
            INITIALIZATION_LOCK.unlock();
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
        long totalIndexedRows = 0L;
        long includedRows = 0L;
        long pendingGapRows = 0L;
        long previousEnd = 0L;
        for (Segment segment : snapshot.segments) {
            totalIndexedRows += segment.rowCount;
            if (previousEnd < Long.MAX_VALUE && segment.startRowId > previousEnd + 1L) {
                pendingGapRows = addRange(ranges, previousEnd + 1L, segment.startRowId - 1L, pendingGapRows);
            }
            previousEnd = Math.max(previousEnd, segment.endRowId);
            boolean spatialMatch = chunks.length > 0 && segment.chunks.mightContainAny(chunks);
            boolean entityMatch = entities.length > 0 && segment.entities != null && segment.entities.mightContainAny(entities);
            boolean requiredRowMatch = containsAny(segment, requiredRowIds);
            if (spatialMatch || entityMatch || requiredRowMatch) {
                matchedSegments++;
                includedRows += segment.rowCount;
                pendingGapRows = addRange(ranges, segment.startRowId, segment.endRowId, pendingGapRows);
            }
            else if (!ranges.isEmpty() && segment.endRowId > ranges.get(ranges.size() - 1).end) {
                pendingGapRows += segment.rowCount;
            }
        }
        if (matchedSegments == snapshot.segments.size()) {
            return null;
        }

        if (snapshot.lastIndexedRowId < Long.MAX_VALUE) {
            addRange(ranges, snapshot.lastIndexedRowId + 1L, Long.MAX_VALUE, pendingGapRows);
        }
        if (ranges.isEmpty()) {
            return null;
        }
        return ranges.size() <= MAXIMUM_PREDICATE_RANGES
                ? ranges
                : coalesceRanges(ranges, includedRows, totalIndexedRows);
    }

    private static List<RowRange> coalesceRanges(List<RowRange> ranges, long includedRows, long totalIndexedRows) {
        int gapCount = ranges.size() - 1;
        int merges = ranges.size() - MAXIMUM_PREDICATE_RANGES;
        long[] sortedGaps = new long[gapCount];
        for (int i = 0; i < gapCount; i++) {
            sortedGaps[i] = ranges.get(i + 1).gapBefore;
        }
        Arrays.sort(sortedGaps);
        long threshold = sortedGaps[merges - 1];
        int equalBudget = 0;
        for (int i = merges - 1; i >= 0 && sortedGaps[i] == threshold; i--) {
            equalBudget++;
        }

        List<RowRange> merged = new ArrayList<>(MAXIMUM_PREDICATE_RANGES);
        RowRange current = ranges.get(0);
        for (int i = 1; i < ranges.size(); i++) {
            RowRange next = ranges.get(i);
            long gap = next.gapBefore;
            if (gap < threshold || (gap == threshold && equalBudget > 0)) {
                if (gap == threshold) {
                    equalBudget--;
                }
                current.end = next.end;
                includedRows += gap;
            }
            else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return includedRows >= totalIndexedRows || (merged.size() == 1 && merged.get(0).start <= 1L && merged.get(0).end == Long.MAX_VALUE)
                ? null
                : merged;
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
            invalidateCache(null);
            runtimeGeneration++;
        }
    }

    private static void invalidateCache(String key) {
        if (indexCache != null && (key == null || key.equals(indexCache.key))) {
            indexCache = null;
        }
        if (activeLoad != null && (key == null || key.equals(activeLoad.key))) {
            activeLoad = null;
        }
        cacheGeneration++;
    }

    private static void publishCommittedSegments(String key, List<PendingSegment> committedSegments) {
        if (committedSegments.isEmpty()) {
            return;
        }
        if (activeLoad != null && key.equals(activeLoad.key)) {
            activeLoad.committedSegments.addAll(committedSegments);
        }
        if (indexCache != null && key.equals(indexCache.key)) {
            Map<Source, IndexSnapshot> updated = appendSegments(indexCache.snapshots, committedSegments);
            if (updated == null) {
                invalidateCache(key);
            }
            else if (updated != indexCache.snapshots) {
                indexCache = new IndexCache(key, updated);
            }
        }
    }

    private static WriterState loadWriterState(Connection connection, String prefix, String key, long observedGeneration) throws SQLException {
        WriterState state = new WriterState();
        DuckDBConnection duckDBConnection = connection.unwrap(DuckDBConnection.class);
        try (Connection scanConnection = duckDBConnection.duplicate(); Connection metadataConnection = duckDBConnection.duplicate()) {
            scanConnection.setAutoCommit(false);
            metadataConnection.setAutoCommit(false);
            try (Statement statement = scanConnection.createStatement()) {
                statement.execute("SET jdbc_stream_results=false");
            }
            for (Source source : Source.values()) {
                long indexedThrough = indexedThrough(scanConnection, prefix, source);
                state.accumulator(source).coveredThrough = indexedThrough;
                long highWater = sourceHighWater(scanConnection, prefix, source);
                if (highWater > indexedThrough) {
                    scanSource(scanConnection, metadataConnection, prefix, source, indexedThrough, highWater, state, key, observedGeneration);
                }
            }
        }
        return state;
    }

    private static long indexedThrough(Connection connection, String prefix, Source source) throws SQLException {
        String sql = "SELECT COALESCE(MAX(end_rowid),0) FROM " + prefix + "duckdb_spatial_index WHERE table_id=" + source.id;
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private static long sourceHighWater(Connection connection, String prefix, Source source) throws SQLException {
        String sql = "SELECT COALESCE(MAX(rowid),0) FROM " + prefix + source.table;
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private static void scanSource(Connection connection, Connection metadataConnection, String prefix, Source source,
            long indexedThrough, long highWater, WriterState state, String key, long observedGeneration) throws SQLException {
        String sql = "SELECT rowid,wid,x,z" + (source.entityRows ? ",entity_spawn_rowid" : "") + " FROM " + prefix + source.table
                + " WHERE rowid>? AND rowid<=? ORDER BY rowid";
        String nextSql = "SELECT min(rowid) FROM " + prefix + source.table + " WHERE rowid>? AND rowid<=?";
        Accumulator accumulator = state.accumulator(source);
        long previousRowId = indexedThrough;
        long rangeStart = indexedThrough;
        try (PreparedStatement statement = connection.prepareStatement(sql); PreparedStatement nextStatement = connection.prepareStatement(nextSql)) {
            while (rangeStart < highWater) {
                long rangeEnd = rangeStart > highWater - BOOTSTRAP_PAGE_ROWS ? highWater : rangeStart + BOOTSTRAP_PAGE_ROWS;
                statement.setLong(1, rangeStart);
                statement.setLong(2, rangeEnd);
                boolean found = false;
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        long rowId = resultSet.getLong("rowid");
                        if (rowId <= previousRowId || rowId > rangeEnd) {
                            throw new SQLException("DuckDB spatial index rows must have increasing positive row IDs");
                        }
                        previousRowId = rowId;
                        found = true;
                        int completedCount = accumulator.completed.size();
                        Integer entitySpawnRowId = source.entityRows ? resultSet.getInt("entity_spawn_rowid") : null;
                        accumulator.add(rowId, resultSet.getInt("wid"), resultSet.getInt("x"), resultSet.getInt("z"), entitySpawnRowId);
                        if (accumulator.completed.size() != completedCount && state.completedCount() >= INDEX_CHECKPOINT_SEGMENTS) {
                            writeCompleted(metadataConnection, prefix, state, key, observedGeneration);
                        }
                    }
                }
                rangeStart = rangeEnd;
                if (!found && rangeStart < highWater) {
                    nextStatement.setLong(1, rangeStart);
                    nextStatement.setLong(2, highWater);
                    try (ResultSet resultSet = nextStatement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SQLException("DuckDB spatial index did not return the next row ID for " + source.table);
                        }
                        long nextRowId = resultSet.getLong(1);
                        if (resultSet.wasNull() || nextRowId <= rangeStart || nextRowId > highWater) {
                            throw new SQLException("DuckDB spatial index scan ended before its high water mark for " + source.table);
                        }
                        rangeStart = nextRowId - 1L;
                    }
                }
            }
        }
        if (previousRowId != highWater) {
            throw new SQLException("DuckDB spatial index scan ended before its high water mark for " + source.table);
        }
        accumulator.finish();
        writeCompleted(metadataConnection, prefix, state, key, observedGeneration);
    }

    private static void writeCompleted(Connection connection, String prefix, WriterState state, String key, long observedGeneration) throws SQLException {
        try {
            List<PendingSegment> committedSegments = new ArrayList<>();
            if (state.writeCompleted(connection, prefix, committedSegments)) {
                connection.commit();
                synchronized (STATE_LOCK) {
                    if (observedGeneration == runtimeGeneration) {
                        publishCommittedSegments(key, committedSegments);
                    }
                    else {
                        invalidateCache(key);
                    }
                }
            }
        }
        catch (SQLException | RuntimeException | Error exception) {
            try {
                connection.rollback();
            }
            catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
    }

    private static Map<Source, IndexSnapshot> snapshots(Connection connection, String prefix) throws SQLException {
        String key = databaseKey(connection, prefix);
        synchronized (STATE_LOCK) {
            if (indexCache != null && key.equals(indexCache.key)) {
                return indexCache.snapshots;
            }
        }

        SNAPSHOT_LOAD_LOCK.lock();
        try {
            for (int attempt = 0; attempt < MAX_SNAPSHOT_LOAD_ATTEMPTS; attempt++) {
                long observedCacheGeneration;
                SnapshotLoad load = new SnapshotLoad(key);
                synchronized (STATE_LOCK) {
                    if (indexCache != null && key.equals(indexCache.key)) {
                        return indexCache.snapshots;
                    }
                    observedCacheGeneration = cacheGeneration;
                    activeLoad = load;
                }

                Map<Source, IndexSnapshot> loaded;
                try {
                    loaded = loadSnapshots(connection, prefix);
                }
                catch (SQLException | RuntimeException | Error exception) {
                    synchronized (STATE_LOCK) {
                        if (activeLoad == load) {
                            activeLoad = null;
                        }
                    }
                    throw exception;
                }

                synchronized (STATE_LOCK) {
                    if (activeLoad == load) {
                        activeLoad = null;
                    }
                    if (observedCacheGeneration != cacheGeneration) {
                        continue;
                    }
                    Map<Source, IndexSnapshot> merged = appendSegments(loaded, load.committedSegments);
                    if (merged == null) {
                        continue;
                    }
                    indexCache = new IndexCache(key, merged);
                    return merged;
                }
            }
            return Collections.emptyMap();
        }
        finally {
            SNAPSHOT_LOAD_LOCK.unlock();
        }
    }

    private static Map<Source, IndexSnapshot> loadSnapshots(Connection connection, String prefix) throws SQLException {
        DuckDBConnection duckDBConnection = connection.unwrap(DuckDBConnection.class);
        if (duckDBConnection == null) {
            throw new SQLException("DuckDB connection required");
        }

        Map<Source, List<Segment>> segmentsBySource = new EnumMap<>(Source.class);
        Map<Source, Boolean> usableBySource = new EnumMap<>(Source.class);
        for (Source source : Source.values()) {
            segmentsBySource.put(source, new ArrayList<>());
            usableBySource.put(source, Boolean.TRUE);
        }

        String sql = "SELECT table_id,start_rowid,end_rowid,row_count,chunks,entities FROM " + prefix + "duckdb_spatial_index";
        try (Connection loadConnection = duckDBConnection.duplicate(); Statement statement = loadConnection.createStatement()) {
            statement.execute("SET jdbc_stream_results=true");
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                while (resultSet.next()) {
                    Source source = Source.fromId(resultSet.getInt(1));
                    if (source == null || !usableBySource.get(source)) {
                        continue;
                    }
                    long startRowId = resultSet.getLong(2);
                    long endRowId = resultSet.getLong(3);
                    int rowCount = resultSet.getInt(4);
                    byte[] chunks = resultSet.getBytes(5);
                    byte[] entities = resultSet.getBytes(6);
                    if (startRowId <= 0L || endRowId < startRowId || rowCount <= 0 || chunks == null || chunks.length != FILTER_BYTES
                            || (source.entityRows && (entities == null || entities.length != FILTER_BYTES))
                            || (!source.entityRows && entities != null && entities.length != FILTER_BYTES)) {
                        usableBySource.put(source, Boolean.FALSE);
                        segmentsBySource.get(source).clear();
                        continue;
                    }
                    segmentsBySource.get(source).add(new Segment(startRowId, endRowId, rowCount, new BloomFilter(chunks), entities == null ? null : new BloomFilter(entities)));
                }
            }
        }

        Map<Source, IndexSnapshot> loaded = new EnumMap<>(Source.class);
        for (Source source : Source.values()) {
            if (!usableBySource.get(source)) {
                loaded.put(source, new IndexSnapshot(Collections.emptyList(), 0L, false));
                continue;
            }
            List<Segment> segments = segmentsBySource.get(source);
            segments.sort(Comparator.comparingLong(segment -> segment.startRowId));
            long lastIndexedRowId = 0L;
            for (Segment segment : segments) {
                lastIndexedRowId = Math.max(lastIndexedRowId, segment.endRowId);
            }
            loaded.put(source, new IndexSnapshot(segments, lastIndexedRowId, true));
        }
        return loaded;
    }

    private static boolean hasTable(Connection connection, String table) throws SQLException {
        String sql = "SELECT 1 FROM duckdb_tables() WHERE schema_name = current_schema() AND table_name = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean hasIndexGaps(Connection connection, String indexTable) throws SQLException {
        String sql = "SELECT 1 FROM ("
                + "SELECT start_rowid,end_rowid,row_count,LAG(end_rowid,1,0) OVER (PARTITION BY table_id ORDER BY start_rowid) AS previous_end "
                + "FROM " + indexTable
                + ") WHERE start_rowid>0 AND end_rowid>=start_rowid AND row_count>0 "
                + "AND previous_end>=0 AND previous_end<" + Long.MAX_VALUE + " AND start_rowid>previous_end+1 LIMIT 1";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next();
        }
    }

    private static Map<Source, List<IndexSpan>> loadIndexSpans(Connection connection, String indexTable) throws SQLException {
        Map<Source, List<IndexSpan>> spansBySource = new EnumMap<>(Source.class);
        Map<Source, Boolean> usableBySource = new EnumMap<>(Source.class);
        for (Source source : Source.values()) {
            spansBySource.put(source, new ArrayList<>());
            usableBySource.put(source, Boolean.TRUE);
        }

        String sql = "SELECT table_id,start_rowid,end_rowid,row_count,octet_length(chunks),octet_length(entities) FROM " + indexTable;
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                Source source = Source.fromId(resultSet.getInt(1));
                if (source == null || !usableBySource.get(source)) {
                    continue;
                }
                long startRowId = resultSet.getLong(2);
                long endRowId = resultSet.getLong(3);
                int rowCount = resultSet.getInt(4);
                int chunkBytes = resultSet.getInt(5);
                boolean chunksNull = resultSet.wasNull();
                int entityBytes = resultSet.getInt(6);
                boolean entitiesNull = resultSet.wasNull();
                if (startRowId <= 0L || endRowId < startRowId || rowCount <= 0 || chunksNull || chunkBytes != FILTER_BYTES
                        || (source.entityRows && (entitiesNull || entityBytes != FILTER_BYTES))
                        || (!source.entityRows && !entitiesNull && entityBytes != FILTER_BYTES)) {
                    usableBySource.put(source, Boolean.FALSE);
                    spansBySource.get(source).clear();
                    continue;
                }
                spansBySource.get(source).add(new IndexSpan(startRowId, endRowId));
            }
        }

        for (Source source : Source.values()) {
            if (!usableBySource.get(source)) {
                spansBySource.remove(source);
                continue;
            }
            spansBySource.get(source).sort(Comparator.comparingLong(span -> span.startRowId));
        }
        return spansBySource;
    }

    private static void collectSourceGapAdjustments(Connection connection, Source source, String tableName, List<IndexSpan> spans,
            List<Integer> adjustedTables, List<Long> oldStarts, List<Long> newStarts) throws SQLException {
        int gapCount = 0;
        long lastIndexedRowId = 0L;
        for (IndexSpan span : spans) {
            if (span.startRowId <= lastIndexedRowId) {
                return;
            }
            if (lastIndexedRowId < Long.MAX_VALUE && span.startRowId > lastIndexedRowId + 1L) {
                gapCount++;
            }
            lastIndexedRowId = span.endRowId;
        }
        if (gapCount == 0) {
            return;
        }

        long minimumRowId = 0L;
        long maximumRowId = 0L;
        boolean hasRows = false;
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT MIN(rowid),MAX(rowid) FROM " + tableName)) {
            if (resultSet.next()) {
                minimumRowId = resultSet.getLong(1);
                if (!resultSet.wasNull()) {
                    maximumRowId = resultSet.getLong(2);
                    hasRows = !resultSet.wasNull();
                }
            }
        }

        BitSet candidateGaps = new BitSet(spans.size());
        if (hasRows) {
            long previousEnd = 0L;
            for (int index = 0; index < spans.size(); index++) {
                IndexSpan span = spans.get(index);
                if (previousEnd < Long.MAX_VALUE && span.startRowId > previousEnd + 1L) {
                    long gapStart = previousEnd + 1L;
                    long gapEnd = span.startRowId - 1L;
                    if (gapEnd >= minimumRowId && gapStart <= maximumRowId) {
                        candidateGaps.set(index);
                    }
                }
                previousEnd = span.endRowId;
            }
        }

        BitSet nonEmptyGaps = candidateGaps.isEmpty() ? null : findNonEmptyGaps(connection, tableName, spans, candidateGaps);
        if (nonEmptyGaps != null && nonEmptyGaps.cardinality() == gapCount) {
            return;
        }

        long previousEnd = 0L;
        for (int index = 0; index < spans.size(); index++) {
            IndexSpan span = spans.get(index);
            if (previousEnd < Long.MAX_VALUE && span.startRowId > previousEnd + 1L && (nonEmptyGaps == null || !nonEmptyGaps.get(index))) {
                adjustedTables.add(source.id);
                oldStarts.add(span.startRowId);
                newStarts.add(previousEnd + 1L);
            }
            previousEnd = span.endRowId;
        }
    }

    private static BitSet findNonEmptyGaps(Connection connection, String tableName, List<IndexSpan> spans, BitSet candidateGaps) throws SQLException {
        BitSet nonEmptyGaps = new BitSet(spans.size());
        List<Integer> batchIndices = new ArrayList<>();
        List<Long> batchBuckets = new ArrayList<>();
        List<Long> batchStarts = new ArrayList<>();
        List<Long> batchEnds = new ArrayList<>();
        long batchMinimumStart = Long.MAX_VALUE;
        long batchMaximumEnd = Long.MIN_VALUE;
        PreparedStatement bucketStatement = null;
        PreparedStatement wideStatement = null;
        try {
            try {
                for (int index = candidateGaps.nextSetBit(0); index >= 0; index = candidateGaps.nextSetBit(index + 1)) {
                    long previousEnd = index == 0 ? 0L : spans.get(index - 1).endRowId;
                    IndexSpan span = spans.get(index);
                    long gapStart = previousEnd + 1L;
                    long gapEnd = span.startRowId - 1L;
                    long startBucket = gapStart >>> GAP_BUCKET_SHIFT;
                    long endBucket = gapEnd >>> GAP_BUCKET_SHIFT;
                    if (endBucket - startBucket > MAXIMUM_GAP_BUCKETS) {
                        if (wideStatement == null) {
                            wideStatement = connection.prepareStatement("SELECT 1 FROM " + tableName + " WHERE rowid BETWEEN ? AND ? LIMIT 1");
                        }
                        wideStatement.setLong(1, gapStart);
                        wideStatement.setLong(2, gapEnd);
                        try (ResultSet resultSet = wideStatement.executeQuery()) {
                            if (resultSet.next()) {
                                nonEmptyGaps.set(index);
                            }
                        }
                    }
                    else {
                        if (batchIndices.size() + (int) (endBucket - startBucket + 1L) > GAP_QUERY_BATCH_SIZE && !batchIndices.isEmpty()) {
                            bucketStatement = executeGapBatch(connection, tableName, bucketStatement, batchIndices, batchBuckets, batchStarts, batchEnds, batchMinimumStart, batchMaximumEnd, nonEmptyGaps);
                            batchMinimumStart = Long.MAX_VALUE;
                            batchMaximumEnd = Long.MIN_VALUE;
                        }
                        batchMinimumStart = Math.min(batchMinimumStart, gapStart);
                        batchMaximumEnd = Math.max(batchMaximumEnd, gapEnd);
                        for (long bucket = startBucket; bucket <= endBucket; bucket++) {
                            batchIndices.add(index);
                            batchBuckets.add(bucket);
                            batchStarts.add(gapStart);
                            batchEnds.add(gapEnd);
                        }
                    }
                }
                if (!batchIndices.isEmpty()) {
                    bucketStatement = executeGapBatch(connection, tableName, bucketStatement, batchIndices, batchBuckets, batchStarts, batchEnds, batchMinimumStart, batchMaximumEnd, nonEmptyGaps);
                }
                return nonEmptyGaps;
            }
            finally {
                if (bucketStatement != null) {
                    bucketStatement.close();
                }
            }
        }
        finally {
            if (wideStatement != null) {
                wideStatement.close();
            }
        }
    }

    private static PreparedStatement executeGapBatch(Connection connection, String tableName, PreparedStatement statement,
            List<Integer> indices, List<Long> buckets, List<Long> starts, List<Long> ends,
            long minimumStart, long maximumEnd, BitSet nonEmptyGaps) throws SQLException {
        if (statement == null) {
            statement = connection.prepareStatement(
                    "WITH gaps AS (SELECT UNNEST(?::INTEGER[]) AS idx,UNNEST(?::BIGINT[]) AS bucket,UNNEST(?::BIGINT[]) AS start_rowid,UNNEST(?::BIGINT[]) AS end_rowid) "
                            + "SELECT DISTINCT g.idx FROM " + tableName + " t "
                            + "JOIN gaps g ON (CAST(t.rowid AS BIGINT)>>" + GAP_BUCKET_SHIFT + ")=g.bucket AND t.rowid BETWEEN g.start_rowid AND g.end_rowid "
                            + "WHERE t.rowid BETWEEN ? AND ?");
        }
        Array indexArray = connection.createArrayOf("INTEGER", indices.toArray());
        Array bucketArray = connection.createArrayOf("BIGINT", buckets.toArray());
        Array startArray = connection.createArrayOf("BIGINT", starts.toArray());
        Array endArray = connection.createArrayOf("BIGINT", ends.toArray());
        try {
            statement.setArray(1, indexArray);
            statement.setArray(2, bucketArray);
            statement.setArray(3, startArray);
            statement.setArray(4, endArray);
            statement.setLong(5, minimumStart);
            statement.setLong(6, maximumEnd);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    nonEmptyGaps.set(resultSet.getInt(1));
                }
            }
        }
        finally {
            freeArray(indexArray);
            freeArray(bucketArray);
            freeArray(startArray);
            freeArray(endArray);
            indices.clear();
            buckets.clear();
            starts.clear();
            ends.clear();
        }
        return statement;
    }

    private static void rebuildSpatialIndex(Connection connection, String indexTable, List<Integer> adjustedTables,
            List<Long> oldStarts, List<Long> newStarts) throws SQLException {
        String stagingTable = indexTable + "_new";
        boolean ownTransaction = connection.getAutoCommit();
        if (ownTransaction) {
            connection.setAutoCommit(false);
        }
        Throwable primaryFailure = null;
        try {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE IF EXISTS " + stagingTable);
                statement.executeUpdate("CREATE TABLE " + stagingTable + " " + TABLE_DEFINITION);
            }
            String copySql = "WITH adjustments AS ("
                    + "SELECT UNNEST(?::INTEGER[]) AS table_id,UNNEST(?::BIGINT[]) AS old_start_rowid,UNNEST(?::BIGINT[]) AS new_start_rowid) "
                    + "INSERT INTO " + stagingTable + " (table_id,start_rowid,end_rowid,row_count,chunks,entities) "
                    + "SELECT s.table_id,COALESCE(a.new_start_rowid,s.start_rowid),s.end_rowid,s.row_count,s.chunks,s.entities "
                    + "FROM " + indexTable + " s LEFT JOIN adjustments a ON s.table_id=a.table_id AND s.start_rowid=a.old_start_rowid";
            Array tableArray = connection.createArrayOf("INTEGER", adjustedTables.toArray());
            Array oldStartArray = connection.createArrayOf("BIGINT", oldStarts.toArray());
            Array newStartArray = connection.createArrayOf("BIGINT", newStarts.toArray());
            try (PreparedStatement statement = connection.prepareStatement(copySql)) {
                statement.setArray(1, tableArray);
                statement.setArray(2, oldStartArray);
                statement.setArray(3, newStartArray);
                statement.executeUpdate();
            }
            finally {
                freeArray(tableArray);
                freeArray(oldStartArray);
                freeArray(newStartArray);
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE " + indexTable);
                statement.executeUpdate("ALTER TABLE " + stagingTable + " RENAME TO " + indexTable);
            }
            if (ownTransaction) {
                connection.commit();
            }
        }
        catch (SQLException | RuntimeException | Error exception) {
            primaryFailure = exception;
            if (ownTransaction) {
                try {
                    connection.rollback();
                }
                catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
            }
            throw exception;
        }
        finally {
            if (ownTransaction) {
                try {
                    connection.setAutoCommit(true);
                }
                catch (SQLException autoCommitFailure) {
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(autoCommitFailure);
                    }
                    else {
                        throw autoCommitFailure;
                    }
                }
            }
        }
    }

    private static void freeArray(Array array) {
        if (array != null) {
            try {
                array.free();
            }
            catch (SQLException ignored) {
            }
        }
    }

    private static Map<Source, IndexSnapshot> appendSegments(Map<Source, IndexSnapshot> snapshots, List<PendingSegment> committedSegments) {
        if (committedSegments.isEmpty()) {
            return snapshots;
        }
        Map<Source, List<Segment>> updatedSegments = null;
        Map<Source, Long> updatedLastRowIds = null;
        for (PendingSegment pending : committedSegments) {
            IndexSnapshot existing = snapshots.get(pending.source);
            if (existing == null) {
                return null;
            }
            if (!existing.usable) {
                continue;
            }
            long currentLastRowId = updatedLastRowIds != null && updatedLastRowIds.containsKey(pending.source)
                    ? updatedLastRowIds.get(pending.source)
                    : existing.lastIndexedRowId;
            Segment segment = pending.segment;
            if (segment.endRowId <= currentLastRowId) {
                continue;
            }
            if (segment.startRowId <= currentLastRowId) {
                return null;
            }
            if (updatedSegments == null) {
                updatedSegments = new EnumMap<>(Source.class);
                updatedLastRowIds = new EnumMap<>(Source.class);
            }
            List<Segment> copy = updatedSegments.get(pending.source);
            if (copy == null) {
                copy = new ArrayList<>(existing.segments.size() + committedSegments.size());
                copy.addAll(existing.segments);
                updatedSegments.put(pending.source, copy);
            }
            copy.add(segment);
            updatedLastRowIds.put(pending.source, segment.endRowId);
        }
        if (updatedSegments == null) {
            return snapshots;
        }
        Map<Source, IndexSnapshot> updated = new EnumMap<>(snapshots);
        for (Map.Entry<Source, List<Segment>> entry : updatedSegments.entrySet()) {
            Source source = entry.getKey();
            updated.put(source, new IndexSnapshot(entry.getValue(), updatedLastRowIds.get(source), true));
        }
        return updated;
    }

    private static long addRange(List<RowRange> ranges, long start, long end, long pendingGapRows) {
        if (!ranges.isEmpty()) {
            RowRange previous = ranges.get(ranges.size() - 1);
            if (previous.end == Long.MAX_VALUE || start <= previous.end + 1L) {
                previous.end = Math.max(previous.end, end);
                return 0L;
            }
        }
        ranges.add(new RowRange(start, end, ranges.isEmpty() ? 0L : pendingGapRows));
        return 0L;
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
        private final List<PendingSegment> writtenSegments = new ArrayList<>();
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
            writtenSegments.clear();
            metadataChanged = state.writeCompleted(connection, prefix, writtenSegments);
            flushed = true;
        }

        void publish() {
            synchronized (STATE_LOCK) {
                if (!key.equals(runtimeKey) || generation != runtimeGeneration) {
                    if (key.equals(runtimeKey)) {
                        runtimeKey = null;
                        runtimeState = null;
                    }
                    invalidateCache(key);
                    runtimeGeneration++;
                    return;
                }
                if (!flushed) {
                    return;
                }
                runtimeState = state;
                runtimeGeneration++;
                if (metadataChanged) {
                    publishCommittedSegments(key, writtenSegments);
                }
            }
        }

        void abandon() {
            synchronized (STATE_LOCK) {
                if (key.equals(runtimeKey)) {
                    runtimeKey = null;
                    runtimeState = null;
                }
                invalidateCache(key);
                runtimeGeneration++;
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
            if (state.writeCompleted(connection, prefix, null)) {
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
            boolean changed = state.writeCompleted(connection, prefix, null);
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
            }
            invalidateCache(key);
            runtimeGeneration++;
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

        private int completedCount() {
            int count = 0;
            for (Accumulator accumulator : accumulators.values()) {
                count += accumulator.completed.size();
            }
            return count;
        }

        private WriterState copy() {
            WriterState copy = new WriterState();
            for (Map.Entry<Source, Accumulator> entry : accumulators.entrySet()) {
                copy.accumulators.put(entry.getKey(), entry.getValue().copy());
            }
            return copy;
        }

        private boolean writeCompleted(Connection connection, String prefix, List<PendingSegment> writtenSegments) throws SQLException {
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
            if (writtenSegments != null) {
                writtenSegments.addAll(pending);
            }
            return true;
        }
    }

    private static final class Accumulator {

        private final Source source;
        private final List<Segment> completed = new ArrayList<>();
        private BloomFilter chunks = new BloomFilter();
        private BloomFilter entities;
        private long coveredThrough = -1L;
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
            if (rowId <= 0L || (rowCount > 0 ? rowId <= endRowId : coveredThrough >= 0L && rowId <= coveredThrough)) {
                throw new SQLException("DuckDB spatial index rows must have increasing positive row IDs");
            }
            if (source.entityRows && (entitySpawnRowId == null || entitySpawnRowId <= 0)) {
                throw new SQLException("DuckDB entity row-group metadata requires a positive entity spawn row ID");
            }
            if (rowCount == 0) {
                startRowId = coveredThrough >= 0L ? coveredThrough + 1L : rowId;
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
            if (coveredThrough >= 0L) {
                coveredThrough = endRowId;
            }
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
            copy.coveredThrough = coveredThrough;
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

    private static final class IndexSpan {

        private final long startRowId;
        private final long endRowId;

        private IndexSpan(long startRowId, long endRowId) {
            this.startRowId = startRowId;
            this.endRowId = endRowId;
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

    private static final class SnapshotLoad {

        private final String key;
        private final List<PendingSegment> committedSegments = new ArrayList<>();

        private SnapshotLoad(String key) {
            this.key = key;
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

        private final List<Segment> segments;
        private final long lastIndexedRowId;
        private final boolean usable;

        private IndexSnapshot(List<Segment> segments, long lastIndexedRowId, boolean usable) {
            this.segments = segments;
            this.lastIndexedRowId = lastIndexedRowId;
            this.usable = usable;
        }
    }

    static final class RowRange {

        private final long start;
        private long end;
        private final long gapBefore;

        private RowRange(long start, long end, long gapBefore) {
            this.start = start;
            this.end = end;
            this.gapBefore = gapBefore;
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
