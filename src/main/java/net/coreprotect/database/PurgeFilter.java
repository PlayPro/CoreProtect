package net.coreprotect.database;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import net.coreprotect.model.action.LookupActions;

/**
 * Selects the rows that one /co purge removes on the relational backends (SQLite, MySQL, DuckDB).
 *
 * <p>
 * co_entity has no world or type column: each row belongs to the co_block kill row whose data column holds its
 * rowid. The statements built here remove co_entity rows through those kill rows, so a scoped purge never leaves
 * entity data behind without the kill row that references it. Player kills also use the kill action, but they
 * store type 0 and a user id in data, so they are never treated as co_entity references.
 */
public final class PurgeFilter {

    private final long timeStart;
    private final long timeEnd;
    private final int worldId;
    private final List<Integer> blockTypes;

    /**
     * @param timeStart
     *            the oldest time (inclusive) to purge
     * @param timeEnd
     *            the newest time (exclusive) to purge
     * @param worldId
     *            the world to purge, or 0 for every world
     * @param blockTypes
     *            material ids (i:) that restrict the purge to those co_block rows, or an empty list
     */
    public PurgeFilter(long timeStart, long timeEnd, int worldId, List<Integer> blockTypes) {
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.worldId = worldId;
        this.blockTypes = List.copyOf(blockTypes);
    }

    /**
     * Returns whether the purge is limited to selected co_block rows, which leaves the other tables untouched.
     */
    private boolean restrictsTables() {
        return !blockTypes.isEmpty();
    }

    /**
     * Returns whether the purge removes co_block kill rows. A restriction to materials keeps every kill row.
     */
    public boolean removesKills() {
        return blockTypes.isEmpty();
    }

    /**
     * Returns whether co_entity rows can be removed by time alone. This holds when every kill row in the time range
     * is purged, because a co_entity row always has the same time as its kill row.
     */
    public boolean purgesEntitiesByTime() {
        return worldId <= 0 && !restrictsTables();
    }

    /**
     * Builds the condition that matches the rows this purge removes from a table.
     *
     * @param table
     *            the table name without prefix
     * @param prefix
     *            the prefix of the tables that subqueries read from
     * @return the SQL condition, or null when the purge keeps every row of the table
     */
    public String deleteCondition(String table, String prefix) {
        if (table.equals("block")) {
            return blockCondition("");
        }
        if (table.equals("entity")) {
            return purgesEntitiesByTime() ? timeCondition("") : null;
        }
        if (!PurgePolicy.isPurgeable(table) || restrictsTables()) {
            return null;
        }
        if (worldId <= 0) {
            return timeCondition("");
        }
        if (!PurgePolicy.isWorldScoped(table)) {
            return null;
        }
        if (table.equals("entity_container") || table.equals("entity_interaction")) {
            return timeCondition("") + " AND (wid = " + worldId + " OR entity_spawn_rowid IN(SELECT rowid FROM " + prefix + "entity_spawn WHERE current_wid = " + worldId + "))";
        }
        return timeCondition("") + " AND wid = " + worldId;
    }

    /**
     * Builds the condition that matches the co_block rows this purge removes.
     *
     * @param qualifier
     *            the table alias to prefix each column with, such as "b.", or an empty string
     * @return the SQL condition
     */
    private String blockCondition(String qualifier) {
        StringBuilder condition = new StringBuilder(timeCondition(qualifier));
        if (worldId > 0) {
            condition.append(" AND ").append(qualifier).append("wid = ").append(worldId);
        }

        if (!blockTypes.isEmpty()) {
            condition.append(" AND ").append(qualifier).append("action NOT IN(").append(LookupActions.ENTITY_KILL).append(",").append(LookupActions.ENTITY_SPAWN).append(")");
            condition.append(" AND ").append(qualifier).append("type IN(").append(joinIds(blockTypes)).append(")");
        }
        return condition.toString();
    }

    /**
     * Builds the MySQL or DuckDB statement that deletes the co_entity rows of the kill rows this purge removes. Run it
     * before the co_block delete, while those kill rows still exist.
     *
     * @param databaseType
     *            the MySQL or DuckDB backend
     * @param prefix
     *            the table prefix
     * @return the SQL statement
     */
    public String deleteEntitiesOfPurgedKills(DatabaseType databaseType, String prefix) {
        if (databaseType.isMySQL()) {
            // The join form avoids a dependent subquery on MySQL 5.7 and MariaDB.
            return "DELETE e FROM " + prefix + "entity AS e INNER JOIN " + prefix + "block AS b ON b.data = e.rowid WHERE " + killReference("b.") + " AND " + blockCondition("b.");
        }
        return "DELETE FROM " + prefix + "entity WHERE rowid IN(SELECT data FROM " + prefix + "block WHERE " + killReference("") + " AND " + blockCondition("") + ")";
    }

    /**
     * Builds the SQLite copy condition that keeps the co_entity rows still referenced by a retained kill row.
     *
     * @param retainedBlockTable
     *            the co_block table that holds the rows the purge keeps
     * @return the SQL condition
     */
    public static String entityRetainCondition(String retainedBlockTable) {
        return "rowid IN(" + killReferences(retainedBlockTable) + ")";
    }

    /**
     * Builds the statement that deletes co_entity rows that no kill row references.
     *
     * @param entityTable
     *            the co_entity table to clean
     * @param blockTable
     *            the co_block table that holds the kill rows
     * @return the SQL statement
     */
    public static String deleteUnreferencedEntities(String entityTable, String blockTable) {
        return "DELETE FROM " + entityTable + " WHERE rowid NOT IN(" + killReferences(blockTable) + ")";
    }

    /**
     * Builds the MySQL statements that prepare an orphan sweep: a temporary table of the co_entity ids that kill rows
     * reference. The table avoids an anti-join on the unindexed co_block.data column.
     *
     * @param prefix
     *            the table prefix
     * @return the SQL statements, in order
     */
    public static List<String> mysqlOrphanSweepSetup(String prefix) {
        String keepTable = prefix + "entity_keep";
        List<String> statements = new ArrayList<>();
        statements.add("DROP TEMPORARY TABLE IF EXISTS " + keepTable);
        statements.add("CREATE TEMPORARY TABLE " + keepTable + " (rowid INT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
        statements.add("INSERT IGNORE INTO " + keepTable + " (rowid) " + killReferences(prefix + "block"));
        return statements;
    }

    /**
     * Builds the MySQL statement that deletes co_entity rows no kill row references, such as rows left by earlier
     * world purges. Run {@link #mysqlOrphanSweepSetup(String)} first.
     *
     * @param prefix
     *            the table prefix
     * @return the SQL statement
     */
    public static String mysqlOrphanSweepDelete(String prefix) {
        return "DELETE e FROM " + prefix + "entity AS e LEFT JOIN " + prefix + "entity_keep AS k ON k.rowid = e.rowid WHERE k.rowid IS NULL";
    }

    /**
     * Builds the MySQL statement that drops the temporary table of an orphan sweep.
     *
     * @param prefix
     *            the table prefix
     * @return the SQL statement
     */
    public static String mysqlOrphanSweepTeardown(String prefix) {
        return "DROP TEMPORARY TABLE IF EXISTS " + prefix + "entity_keep";
    }

    private String timeCondition(String qualifier) {
        return qualifier + "time < " + timeEnd + " AND " + qualifier + "time >= " + timeStart;
    }

    private static String killReferences(String blockTable) {
        return "SELECT data FROM " + blockTable + " WHERE " + killReference("") + " AND data IS NOT NULL";
    }

    private static String killReference(String qualifier) {
        return qualifier + "action = " + LookupActions.ENTITY_KILL + " AND " + qualifier + "type <> 0";
    }

    private static String joinIds(List<Integer> ids) {
        StringJoiner joiner = new StringJoiner(",");
        for (Integer id : ids) {
            joiner.add(String.valueOf(id));
        }
        return joiner.toString();
    }
}
