package net.coreprotect.patch.script;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

final class LegacyPatchReferences {

    private final String table;
    private final String column;
    private final Map<String, Integer> values = new HashMap<>();
    private int maxId;

    LegacyPatchReferences(Statement statement, String table, String column) throws SQLException {
        this.table = table;
        this.column = column;
        try (ResultSet rows = statement.executeQuery("SELECT id," + column + " FROM " + table)) {
            while (rows.next()) {
                int id = rows.getInt(1);
                maxId = Math.max(maxId, id);
                values.put(rows.getString(2), id);
            }
        }
    }

    int resolve(Statement statement, String value) throws SQLException {
        Integer existing = values.get(value);
        if (existing != null) {
            return existing;
        }
        int id = Math.addExact(maxId, 1);
        try (PreparedStatement insert = statement.getConnection().prepareStatement("INSERT INTO " + table + " (id," + column + ") VALUES (?,?)")) {
            insert.setInt(1, id);
            insert.setString(2, value);
            if (insert.executeUpdate() != 1) {
                throw new SQLException("Unable to store legacy patch reference in " + table);
            }
        }
        maxId = id;
        values.put(value, id);
        return id;
    }
}
