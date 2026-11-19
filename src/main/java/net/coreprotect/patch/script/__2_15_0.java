package net.coreprotect.patch.script;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ErrorReporter;

public class __2_15_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "chat MODIFY message VARCHAR(1000)");
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "command MODIFY message VARCHAR(1000)");
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "user MODIFY user VARCHAR(100)");
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "username_log MODIFY user VARCHAR(100)");
            }

            String query = "SELECT rowid as id, material FROM " + ConfigHandler.prefix + "material_map";
            String preparedQuery = "UPDATE " + ConfigHandler.prefix + "material_map SET material = ? WHERE rowid = ?";
            LegacyPatchTransaction.run(statement, () -> {
                try (PreparedStatement update = statement.getConnection().prepareStatement(preparedQuery);
                        ResultSet rows = statement.executeQuery(query)) {
                    while (rows.next()) {
                        String material = rows.getString("material");
                        if (material != null && material.startsWith("minecraft:") && !material.startsWith("minecraft:legacy_")) {
                            update.setString(1, "minecraft:legacy_" + material.substring("minecraft:".length()));
                            update.setLong(2, rows.getLong("id"));
                            update.executeUpdate();
                        }
                    }
                }
            });

            String block = ConfigHandler.prefix + "block";
            if (Config.getGlobal().MYSQL && !LegacyPatchSchema.columns(statement, block).getOrDefault("rowid", "").startsWith("bigint")) {
                statement.executeUpdate("ALTER TABLE " + block + " MODIFY COLUMN rowid BIGINT NOT NULL AUTO_INCREMENT");
            }
            LegacyPatchSchema.ensureColumn(statement, block, "blockdata", "BLOB");
            return ConfigHandler.loadTypes(statement);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

}
