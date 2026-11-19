package net.coreprotect.patch.script;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.bukkit.Material;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.patch.Patch;
import net.coreprotect.utility.ErrorReporter;

public class __2_19_0 {

    protected static boolean patch(Statement statement) {
        try {
            String prefix = ConfigHandler.prefix;
            boolean mysql = ConfigHandler.databaseType.isMySQL();
            String integer = mysql ? "INT" : "INTEGER";
            LegacyPatchSchema.ensureColumn(statement, prefix + "sign", "action", integer);
            if (mysql) {
                LegacyPatchSchema.ensureMySQLIndex(statement, prefix + "sign", "wid", List.of("wid", "x", "z", "time"));
                LegacyPatchSchema.ensureMySQLIndex(statement, prefix + "sign", "user", List.of("user", "time"));
                LegacyPatchSchema.ensureMySQLIndex(statement, prefix + "sign", "time", List.of("time"));
            }
            else {
                LegacyPatchSchema.ensureSQLiteIndex(statement, prefix + "sign", "sign_index", List.of("wid", "x", "z", "time"));
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS sign_user_index ON " + prefix + "sign(user,time)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS sign_time_index ON " + prefix + "sign(time)");
            }
            if (!Patch.continuePatch()) {
                return false;
            }
            for (String table : List.of("chat", "command")) {
                String qualified = prefix + table;
                Map<String, String> columns = LegacyPatchSchema.columns(statement, qualified);
                for (String coordinate : List.of("wid", "x", "y", "z")) {
                    if (!columns.containsKey(coordinate)) {
                        statement.executeUpdate("ALTER TABLE " + qualified + " ADD COLUMN " + coordinate + " " + integer);
                    }
                }
                if (mysql) {
                    LegacyPatchSchema.ensureMySQLIndex(statement, qualified, "wid", List.of("wid", "x", "z", "time"));
                }
                else {
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + table + "_wid_index ON " + qualified + "(wid,x,z,time)");
                }
            }
            if (!Patch.continuePatch()) {
                return false;
            }

            StringJoiner signTypes = new StringJoiner(",");
            try (ResultSet rows = statement.executeQuery("SELECT id,material FROM " + prefix + "material_map WHERE material LIKE '%sign'")) {
                while (rows.next()) {
                    String name = rows.getString("material");
                    if (name.endsWith("_sign") && !name.endsWith("_hanging_sign") && Material.matchMaterial(name, false) != null) {
                        signTypes.add(Integer.toString(rows.getInt("id")));
                    }
                }
            }
            if (signTypes.length() > 0) {
                LegacyPatchTransaction.run(statement, () -> markPlacedSigns(statement, prefix, signTypes.toString()));
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static void markPlacedSigns(Statement statement, String prefix, String signTypes) throws SQLException {
        String blockQuery = "SELECT time,user,wid,x,y,z FROM " + prefix + "block WHERE type IN(" + signTypes + ") AND action=1 ORDER BY rowid ASC";
        String signQuery = "SELECT rowid FROM " + prefix + "sign WHERE user=? AND wid=? AND x=? AND y=? AND z=? AND time>=? ORDER BY rowid ASC LIMIT 1";
        try (PreparedStatement sign = statement.getConnection().prepareStatement(signQuery);
                PreparedStatement update = statement.getConnection().prepareStatement("UPDATE " + prefix + "sign SET action=1 WHERE rowid=?");
                ResultSet blocks = statement.executeQuery(blockQuery)) {
            while (blocks.next()) {
                sign.setInt(1, blocks.getInt("user"));
                sign.setInt(2, blocks.getInt("wid"));
                sign.setInt(3, blocks.getInt("x"));
                sign.setInt(4, blocks.getInt("y"));
                sign.setInt(5, blocks.getInt("z"));
                sign.setInt(6, blocks.getInt("time"));
                try (ResultSet rows = sign.executeQuery()) {
                    if (rows.next()) {
                        update.setLong(1, rows.getLong("rowid"));
                        update.executeUpdate();
                    }
                }
            }
        }
    }
}
