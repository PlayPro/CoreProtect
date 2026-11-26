package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.bukkit.command.CommandSender;

import net.coreprotect.database.DuckDBLookupQuery;

final class InspectorLookup {

    private static final String DEFAULT_NAMESPACE = "minecraft:";

    private InspectorLookup() {
    }

    static String resolveCommand(String command, CommandSender sender) {
        if (command != null) {
            return command;
        }
        if (sender.hasPermission("coreprotect.co")) {
            return "co";
        }
        if (sender.hasPermission("coreprotect.core")) {
            return "core";
        }
        return sender.hasPermission("coreprotect.coreprotect") ? "coreprotect" : "co";
    }

    static String displayTarget(String target) {
        return target.startsWith(DEFAULT_NAMESPACE) ? target.substring(DEFAULT_NAMESPACE.length()) : target;
    }

    static int totalPages(long count, int limit) {
        return count == 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, 1L + (count - 1L) / limit);
    }

    static Page queryPage(Statement statement, String table, String spatialTable, String index, String where,
            String columns, String order, boolean orderByTime, int page, int limit) throws SQLException {
        long end = (long) page * limit;
        long offset = end - limit;
        if (spatialTable != null) {
            String qualifiedColumns = "data_rows." + columns.replace(",", ",data_rows.");
            String query = DuckDBLookupQuery.pageQuery(spatialTable, table, where, qualifiedColumns, orderByTime, limit, offset);
            return new Page(query, 0L, end, true);
        }

        String from = " FROM " + table + " " + index + "WHERE " + where;
        long count;
        try (ResultSet results = statement.executeQuery("SELECT COUNT(*) AS count" + from)) {
            count = results.next() ? results.getLong("count") : 0L;
        }
        String query = "SELECT " + columns + from + " ORDER BY " + order + " LIMIT " + limit + " OFFSET " + offset;
        return new Page(query, count, end, false);
    }

    static final class Page {
        final String query;
        final long count;
        final long end;
        final boolean combinedCount;

        private Page(String query, long count, long end, boolean combinedCount) {
            this.query = query;
            this.count = count;
            this.end = end;
            this.combinedCount = combinedCount;
        }
    }
}
