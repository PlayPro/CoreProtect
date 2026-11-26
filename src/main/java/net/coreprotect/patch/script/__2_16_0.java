package net.coreprotect.patch.script;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.patch.Patch;
import net.coreprotect.utility.ErrorReporter;

public class __2_16_0 {

    protected static boolean patch(Statement statement) {
        try {
            String prefix = ConfigHandler.prefix;
            updateSkulls(statement, prefix + "skull");
            if (!Patch.continuePatch()) {
                return false;
            }
            LegacyPatchTransaction.run(statement, () -> statement.executeUpdate("DELETE FROM " + prefix + "block WHERE y=0 AND type IN (SELECT id FROM "
                    + prefix + "material_map WHERE material LIKE '%_CONCRETE' OR material LIKE '%_CONCRETE_POWDER')"));
            if (!Patch.continuePatch()) {
                return false;
            }
            LegacyPatchTransaction.run(statement, () -> removeUnusedUsers(statement, prefix));
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static void updateSkulls(Statement statement, String table) throws SQLException {
        Map<String, String> columns = LegacyPatchSchema.columns(statement, table);
        List<String> obsolete = new ArrayList<>();
        for (String column : List.of("type", "data", "rotation")) {
            if (columns.containsKey(column)) {
                obsolete.add(column);
            }
        }
        if (ConfigHandler.databaseType.isMySQL()) {
            List<String> changes = new ArrayList<>();
            if (!obsolete.isEmpty()) {
                changes.add("MODIFY owner VARCHAR(64)");
            }
            for (String column : obsolete) {
                changes.add("DROP COLUMN " + column);
            }
            if (!changes.isEmpty()) {
                statement.executeUpdate("ALTER TABLE " + table + " " + String.join(", ", changes));
            }
        }
        else if (!obsolete.isEmpty()) {
            String temporary = table + "_temp";
            String copied = columns.containsKey("skin") ? "id,time,owner,skin" : "id,time,owner";
            String skin = columns.containsKey("skin") ? ", skin TEXT" : "";
            LegacyPatchTransaction.run(statement, () -> {
                statement.executeUpdate("ALTER TABLE " + table + " RENAME TO " + temporary);
                statement.executeUpdate("CREATE TABLE " + table + " (id INTEGER PRIMARY KEY ASC, time INTEGER, owner TEXT" + skin + ")");
                statement.executeUpdate("INSERT INTO " + table + " (" + copied + ") SELECT " + copied + " FROM " + temporary);
                statement.executeUpdate("DROP TABLE " + temporary);
            });
        }
    }

    private static void removeUnusedUsers(Statement statement, String prefix) throws SQLException {
        String users = "SELECT rowid,user FROM " + prefix + "user WHERE uuid IS NULL";
        String references = "SELECT EXISTS (SELECT user FROM " + prefix + "session WHERE user = ?) OR EXISTS (SELECT user FROM " + prefix
                + "container WHERE user = ?) OR EXISTS (SELECT user FROM " + prefix + "command WHERE user = ?) OR EXISTS (SELECT user FROM "
                + prefix + "chat WHERE user = ?) OR EXISTS (SELECT user FROM " + prefix + "block WHERE user = ?) as userExists";
        try (PreparedStatement select = statement.getConnection().prepareStatement(references);
                PreparedStatement delete = statement.getConnection().prepareStatement("DELETE FROM " + prefix + "user WHERE rowid = ?");
                ResultSet rows = statement.executeQuery(users)) {
            while (rows.next()) {
                int rowId = rows.getInt("rowid");
                String name = rows.getString("user");
                if (name == null || name.startsWith("#")) {
                    continue;
                }
                Database.setMultiInt(select, rowId, 5);
                try (ResultSet referenced = select.executeQuery()) {
                    if (!referenced.next()) {
                        throw new SQLException("Unable to check legacy user references");
                    }
                    if (!referenced.getBoolean("userExists")) {
                        delete.setInt(1, rowId);
                        delete.executeUpdate();
                    }
                }
            }
        }
    }

}
