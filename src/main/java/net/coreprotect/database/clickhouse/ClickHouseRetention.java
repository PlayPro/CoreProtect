package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

import net.coreprotect.database.PurgePolicy;
import net.coreprotect.model.action.LookupActions;

final class ClickHouseRetention {

    private static final String MUTATION_SETTINGS = " SETTINGS mutations_sync=2,allow_nondeterministic_mutations=1";
    private static final String PURGEABLE_FAMILIES = purgeableFamilies();

    private final ClickHouseJdbc jdbc;
    private final UUID datasetId;
    private final String eventTable;
    private final String highWaterTable;
    private final String prefix;
    private final String database;
    private final String databaseName;
    private final String eventTableName;
    private volatile Connection activePurgeConnection;
    private volatile boolean purgeCancellationRequested;

    ClickHouseRetention(ClickHouseJdbc jdbc, String database, String prefix, UUID datasetId) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId");
        databaseName = ClickHouseIdentifiers.requireIdentifier(database, "ClickHouse database");
        this.database = ClickHouseIdentifiers.quote(databaseName, "ClickHouse database");
        this.prefix = prefix == null || prefix.isEmpty() ? "" : ClickHouseIdentifiers.requireIdentifier(prefix, "ClickHouse table prefix");
        eventTableName = this.prefix + "event_data";
        eventTable = table("event_data");
        highWaterTable = table("retention_high_water");
    }

    synchronized void recoverAbandonedTargets() throws SQLException {
        try (Connection connection = jdbc.openConnection()) {
            cleanupAbandonedTargets(connection);
        }
    }

    void cancelPurge() {
        purgeCancellationRequested = true;
        Connection connection = activePurgeConnection;
        if (connection == null) {
            return;
        }
        try {
            connection.abort(Runnable::run);
        }
        catch (Exception ignored) {
            try {
                connection.close();
            }
            catch (Exception closeIgnored) {
            }
        }
    }

    synchronized long purge(long startTime, long endTime, int worldId, List<Integer> blockTypes, boolean optimize) throws SQLException {
        if (startTime < 0 || endTime <= startTime || endTime > 0xffff_ffffL) {
            throw new IllegalArgumentException("Invalid ClickHouse purge time range");
        }
        if (worldId < 0) {
            throw new IllegalArgumentException("ClickHouse purge world ID cannot be negative");
        }
        List<Integer> restrictedBlockTypes = blockTypes == null ? Collections.emptyList() : new ArrayList<>(blockTypes);
        boolean directTimePurge = worldId == 0 && restrictedBlockTypes.isEmpty();
        String targetSuffix = "purge_targets_" + UUID.randomUUID().toString().replace("-", "");
        String targetTableName = prefix + targetSuffix;
        String targetTable = table(targetSuffix);

        requirePurgeNotCancelled();
        try (Connection connection = jdbc.openConnection()) {
            activePurgeConnection = connection;
            requirePurgeNotCancelled();
            cleanupAbandonedTargets(connection);
            snapshotHighWaterMarks(connection);
            boolean completed = false;
            Throwable failure = null;
            try {
                createTargets(connection, targetTable);
                long removed;
                if (directTimePurge) {
                    removed = countPrimaryRows(connection, startTime, endTime);
                    dropCoveredPartitions(connection, startTime, endTime);
                    deletePrimaryRows(connection, startTime, endTime);
                }
                else {
                    selectPrimaryTargets(connection, targetTable, startTime, endTime, worldId, restrictedBlockTypes);
                    removed = countTargets(connection, targetTable);
                    deleteTargets(connection, targetTable);
                }
                removed += cleanupEntityData(connection, targetTable);

                if (optimize) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("OPTIMIZE TABLE " + eventTable + " FINAL");
                    }
                }
                completed = true;
                return removed;
            }
            catch (SQLException | RuntimeException | Error exception) {
                failure = exception;
                throw exception;
            }
            finally {
                try {
                    if (completed || !hasPendingMutation(connection, targetTableName)) {
                        dropTargets(connection, targetTable);
                    }
                }
                catch (SQLException cleanupFailure) {
                    if (failure == null) {
                        throw cleanupFailure;
                    }
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }
        finally {
            activePurgeConnection = null;
        }
    }

    private void requirePurgeNotCancelled() throws SQLException {
        if (purgeCancellationRequested) {
            throw new SQLException("ClickHouse purge cancelled during shutdown");
        }
    }

    private void cleanupAbandonedTargets(Connection connection) throws SQLException {
        String targetPrefix = prefix + "purge_targets_";
        String sql = "SELECT name FROM system.tables WHERE database=? AND startsWith(name,?)";
        List<String> candidates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, databaseName);
            statement.setString(2, targetPrefix);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString(1);
                    String suffix = tableName.substring(targetPrefix.length());
                    if (suffix.matches("[0-9a-f]{32}")) {
                        candidates.add(tableName);
                    }
                }
            }
        }
        for (String candidate : candidates) {
            if (!hasPendingMutation(connection, candidate)) {
                dropTargets(connection, database + "." + ClickHouseIdentifiers.quote(candidate, "ClickHouse purge target table"));
            }
        }
    }

    private boolean hasPendingMutation(Connection connection, String targetTableName) throws SQLException {
        String sql = "SELECT 1 FROM system.mutations WHERE database=? AND table=? AND is_done=0 AND position(command,?)>0 LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, databaseName);
            statement.setString(2, eventTableName);
            statement.setString(3, targetTableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void snapshotHighWaterMarks(Connection connection) throws SQLException {
        String sql = "INSERT INTO " + highWaterTable
                + " (dataset_id,producer_id,producer_sequence,family,rowid,recorded_at)"
                + " SELECT dataset_id,any(producer_id),max(producer_sequence),family,max(rowid),now64(3, 'UTC')"
                + " FROM " + eventTable + " WHERE dataset_id=? GROUP BY dataset_id,family";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, datasetId);
            statement.execute();
        }
    }

    private void createTargets(Connection connection, String targetTable) throws SQLException {
        execute(connection, "CREATE TABLE " + targetTable
                + " (family LowCardinality(String),rowid UInt64) ENGINE = MergeTree ORDER BY (family,rowid)"
                + " SETTINGS fsync_after_insert=1,fsync_part_directory=1");
    }

    private void dropTargets(Connection connection, String targetTable) throws SQLException {
        execute(connection, "DROP TABLE IF EXISTS " + targetTable);
    }

    private void clearTargets(Connection connection, String targetTable) throws SQLException {
        execute(connection, "TRUNCATE TABLE " + targetTable);
    }

    private void selectPrimaryTargets(Connection connection, String targetTable, long startTime, long endTime, int worldId, List<Integer> blockTypes) throws SQLException {
        for (ClickHouseFamily family : ClickHouseFamily.values()) {
            if (!family.isPurgeable()) {
                continue;
            }
            if (!blockTypes.isEmpty() && !PurgePolicy.supportsBlockRestriction(family.getTableName())) {
                continue;
            }
            if (worldId > 0 && !family.isWorldScoped()) {
                continue;
            }

            StringBuilder sql = new StringBuilder("INSERT INTO ").append(targetTable)
                    .append(" SELECT '").append(family.getTableName()).append("',rowid FROM ").append(table(family.getTableName()))
                    .append(" WHERE time>=? AND time<?");
            List<Integer> integerParameters = new ArrayList<>();

            if (PurgePolicy.supportsBlockRestriction(family.getTableName()) && !blockTypes.isEmpty()) {
                sql.append(" AND action NOT IN(").append(LookupActions.ENTITY_KILL).append(',').append(LookupActions.ENTITY_SPAWN).append(") AND type IN(");
                StringJoiner placeholders = new StringJoiner(",");
                for (Integer blockType : blockTypes) {
                    placeholders.add("?");
                    integerParameters.add(Objects.requireNonNull(blockType, "block type"));
                }
                sql.append(placeholders).append(')');
            }

            if (worldId > 0) {
                if (family == ClickHouseFamily.ENTITY_CONTAINER || family == ClickHouseFamily.ENTITY_INTERACTION) {
                    sql.append(" AND (wid=? OR entity_spawn_rowid IN(SELECT rowid FROM ").append(table("entity_spawn")).append(" WHERE current_wid=?))");
                    integerParameters.add(worldId);
                    integerParameters.add(worldId);
                }
                else {
                    sql.append(" AND wid=?");
                    integerParameters.add(worldId);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                statement.setLong(1, startTime);
                statement.setLong(2, endTime);
                for (int index = 0; index < integerParameters.size(); index++) {
                    statement.setInt(index + 3, integerParameters.get(index));
                }
                statement.execute();
            }
        }
    }

    private long countPrimaryRows(Connection connection, long startTime, long endTime) throws SQLException {
        long count = 0;
        for (ClickHouseFamily family : ClickHouseFamily.values()) {
            if (!family.isPurgeable()) {
                continue;
            }
            String sql = "SELECT count() FROM " + table(family.getTableName()) + " WHERE time>=? AND time<?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, startTime);
                statement.setLong(2, endTime);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new SQLException("ClickHouse did not return a purge row count");
                    }
                    count += resultSet.getLong(1);
                }
            }
        }
        return count;
    }

    private void deletePrimaryRows(Connection connection, long startTime, long endTime) throws SQLException {
        String sql = "ALTER TABLE " + eventTable + " DELETE WHERE dataset_id=toUUID('" + datasetId + "')"
                + " AND family IN(" + PURGEABLE_FAMILIES + ") AND time>=" + startTime + " AND time<" + endTime
                + MUTATION_SETTINGS;
        execute(connection, sql);
    }

    private long cleanupEntityData(Connection connection, String targetTable) throws SQLException {
        clearMissingReference(connection, "block_rowid", "block_rowid_present", "block");
        clearMissingReference(connection, "kill_rowid", "kill_rowid_present", "entity");

        clearTargets(connection, targetTable);
        selectOrphanChildren(connection, targetTable, ClickHouseFamily.ENTITY_CONTAINER);
        selectOrphanChildren(connection, targetTable, ClickHouseFamily.ENTITY_INTERACTION);
        long removed = countTargets(connection, targetTable);
        deleteTargets(connection, targetTable);

        clearTargets(connection, targetTable);
        String orphanSpawns = "INSERT INTO " + targetTable
                + " SELECT 'entity_spawn',rowid FROM " + table("entity_spawn")
                + " WHERE removed=1 AND block_rowid IS NULL AND kill_rowid IS NULL"
                + " AND rowid NOT IN(SELECT assumeNotNull(entity_spawn_rowid) FROM " + table("entity_container") + " WHERE entity_spawn_rowid IS NOT NULL)"
                + " AND rowid NOT IN(SELECT assumeNotNull(entity_spawn_rowid) FROM " + table("entity_interaction") + " WHERE entity_spawn_rowid IS NOT NULL)";
        execute(connection, orphanSpawns);
        removed += countTargets(connection, targetTable);
        deleteTargets(connection, targetTable);
        return removed;
    }

    private void clearMissingReference(Connection connection, String valueColumn, String presenceColumn, String referencedFamily) throws SQLException {
        String sql = "ALTER TABLE " + eventTable + " UPDATE " + valueColumn + "=NULL," + presenceColumn + "=0"
                + " WHERE dataset_id=toUUID('" + datasetId + "') AND family='entity_spawn' AND " + presenceColumn + "=1"
                + " AND " + valueColumn + " IS NOT NULL AND " + valueColumn + " NOT IN(SELECT rowid FROM " + table(referencedFamily) + ")"
                + MUTATION_SETTINGS;
        execute(connection, sql);
    }

    private void selectOrphanChildren(Connection connection, String targetTable, ClickHouseFamily family) throws SQLException {
        String sql = "INSERT INTO " + targetTable + " SELECT '" + family.getTableName() + "',rowid FROM " + table(family.getTableName())
                + " WHERE entity_spawn_rowid IS NULL OR assumeNotNull(entity_spawn_rowid) NOT IN(SELECT rowid FROM " + table("entity_spawn") + ")";
        execute(connection, sql);
    }

    private long countTargets(Connection connection, String targetTable) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT count() FROM " + targetTable)) {
            if (!resultSet.next()) {
                throw new SQLException("ClickHouse did not return a purge target count");
            }
            return resultSet.getLong(1);
        }
    }

    private void deleteTargets(Connection connection, String targetTable) throws SQLException {
        if (countTargets(connection, targetTable) == 0) {
            return;
        }
        String sql = "ALTER TABLE " + eventTable + " DELETE WHERE dataset_id=toUUID('" + datasetId + "')"
                + " AND (family,rowid) IN(SELECT family,rowid FROM " + targetTable + ")" + MUTATION_SETTINGS;
        execute(connection, sql);
    }

    private void dropCoveredPartitions(Connection connection, long startTime, long endTime) throws SQLException {
        String sql = "SELECT _partition_id,min(time),max(time) FROM " + eventTable
                + " WHERE dataset_id=? AND family IN(" + PURGEABLE_FAMILIES + ") GROUP BY _partition_id";
        List<String> partitions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, datasetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String partition = resultSet.getString(1);
                    long minimum = resultSet.getLong(2);
                    long maximum = resultSet.getLong(3);
                    if (!"0".equals(partition) && partition != null && partition.matches("[0-9]+") && minimum >= startTime && maximum < endTime) {
                        partitions.add(partition);
                    }
                }
            }
        }
        for (String partition : partitions) {
            execute(connection, "ALTER TABLE " + eventTable + " DROP PARTITION ID '" + partition + "'");
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String table(String suffix) {
        return database + "." + ClickHouseIdentifiers.quote(prefix + suffix, "ClickHouse table");
    }

    private static String purgeableFamilies() {
        StringJoiner families = new StringJoiner(",");
        for (ClickHouseFamily family : ClickHouseFamily.values()) {
            if (family.isPurgeable()) {
                families.add("'" + family.getTableName() + "'");
            }
        }
        return families.toString();
    }
}
