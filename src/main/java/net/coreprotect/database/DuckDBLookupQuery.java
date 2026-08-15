package net.coreprotect.database;

import java.sql.Connection;
import java.util.Collection;

import net.coreprotect.config.ConfigHandler;

public final class DuckDBLookupQuery {

    private DuckDBLookupQuery() {
        throw new IllegalStateException("Database class");
    }

    public static String spatialTable(Connection connection, String table, int worldId, int minimumX, int maximumX, int minimumZ, int maximumZ, String alias) {
        if (!ConfigHandler.databaseType.isDuckDB()) {
            return ConfigHandler.prefix + table + alias(alias);
        }
        return DuckDBSpatialIndex.tableExpression(connection, ConfigHandler.prefix, table, worldId, minimumX, maximumX, minimumZ, maximumZ, null, null, alias);
    }

    public static String entityTable(Connection connection, String table, Collection<Integer> entitySpawnRowIds, String alias) {
        if (!ConfigHandler.databaseType.isDuckDB()) {
            return ConfigHandler.prefix + table + alias(alias);
        }
        return DuckDBSpatialIndex.entityTableExpression(connection, ConfigHandler.prefix, table, entitySpawnRowIds, alias);
    }

    public static String pageQuery(String sourceTable, String baseTable, String where, String columns, boolean orderByTime, int limit, int offset) {
        String matchingColumns = orderByTime ? "rowid,time" : "rowid";
        String pageOrder = orderByTime ? "time DESC,rowid DESC" : "rowid DESC";
        String resultOrder = orderByTime ? "page_rows.time DESC NULLS LAST,page_rows.rowid DESC NULLS LAST" : "page_rows.rowid DESC NULLS LAST";
        return "WITH matching_rows AS MATERIALIZED (SELECT " + matchingColumns + " FROM " + sourceTable + " WHERE " + where + "),"
                + "total_rows AS (SELECT COUNT(*) AS count FROM matching_rows),"
                + "page_rows AS (SELECT " + matchingColumns + " FROM matching_rows ORDER BY " + pageOrder + " LIMIT " + limit + " OFFSET " + offset + ") "
                + "SELECT total_rows.count,page_rows.rowid AS result_id," + columns + " FROM total_rows "
                + "LEFT JOIN page_rows ON true LEFT JOIN " + baseTable + " data_rows ON data_rows.rowid=page_rows.rowid "
                + "ORDER BY " + resultOrder;
    }

    private static String alias(String alias) {
        return alias == null || alias.isEmpty() ? "" : " AS " + alias;
    }
}
