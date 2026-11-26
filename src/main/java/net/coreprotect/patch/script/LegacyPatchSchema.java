package net.coreprotect.patch.script;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import net.coreprotect.config.ConfigHandler;

final class LegacyPatchSchema {

    private LegacyPatchSchema() {
    }

    static Map<String, String> columns(Statement statement, String table) throws SQLException {
        boolean mysql = ConfigHandler.databaseType.isMySQL();
        String query = mysql ? "SHOW COLUMNS FROM " + table : "PRAGMA table_info('" + table + "')";
        Map<String, String> columns = new LinkedHashMap<>();
        try (ResultSet rows = statement.executeQuery(query)) {
            while (rows.next()) {
                columns.put(rows.getString(mysql ? "Field" : "name").toLowerCase(Locale.ROOT), rows.getString(mysql ? "Type" : "type"));
            }
        }
        if (columns.isEmpty()) {
            throw new SQLException("Unable to inspect legacy table " + table);
        }
        return columns;
    }

    static void ensureColumn(Statement statement, String table, String column, String definition) throws SQLException {
        if (!columns(statement, table).containsKey(column)) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    static List<String> mysqlIndexColumns(Statement statement, String table, String name) throws SQLException {
        Map<Integer, String> positions = new TreeMap<>();
        try (ResultSet rows = statement.executeQuery("SHOW INDEX FROM " + table + " WHERE Key_name='" + name + "'")) {
            while (rows.next()) {
                positions.put(rows.getInt("Seq_in_index"), rows.getString("Column_name").toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(positions.values());
    }

    static void ensureMySQLIndex(Statement statement, String table, String name, List<String> columns) throws SQLException {
        List<String> existing = mysqlIndexColumns(statement, table, name);
        if (!existing.equals(columns)) {
            String drop = existing.isEmpty() ? "" : "DROP INDEX " + name + ", ";
            statement.executeUpdate("ALTER TABLE " + table + " " + drop + "ADD INDEX " + name + " (" + String.join(",", columns) + ")");
        }
    }

    static void ensureSQLiteIndex(Statement statement, String table, String name, List<String> columns) throws SQLException {
        Map<Integer, String> positions = new TreeMap<>();
        try (ResultSet rows = statement.executeQuery("PRAGMA index_info('" + name + "')")) {
            while (rows.next()) {
                positions.put(rows.getInt("seqno"), rows.getString("name"));
            }
        }
        if (!List.copyOf(positions.values()).equals(columns)) {
            LegacyPatchTransaction.run(statement, () -> {
                statement.executeUpdate("DROP INDEX IF EXISTS " + name);
                statement.executeUpdate("CREATE INDEX " + name + " ON " + table + " (" + String.join(",", columns) + ")");
            });
        }
    }
}
