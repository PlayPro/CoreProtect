package net.coreprotect.patch.script;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;

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
            PreparedStatement preparedStatement = statement.getConnection().prepareStatement(preparedQuery);

            Database.beginTransaction(statement, Config.getGlobal().MYSQL);
            ResultSet rs = statement.executeQuery(query);
            while (rs.next()) {
                int rowid = rs.getInt("id");
                String material = rs.getString("material");
                if (material.startsWith("minecraft:") && !material.contains("minecraft:legacy_")) {
                    material = material.replace("minecraft:", "minecraft:legacy_");
                    preparedStatement.setString(1, material);
                    preparedStatement.setInt(2, rowid);
                    preparedStatement.executeUpdate();
                }
            }
            rs.close();
            Database.commitTransaction(statement, Config.getGlobal().MYSQL);

            try {
                if (Config.getGlobal().MYSQL) {
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "block MODIFY COLUMN rowid bigint NOT NULL AUTO_INCREMENT, ADD COLUMN blockdata BLOB");
                }
                else {
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "block ADD COLUMN blockdata BLOB");
                }
            }
            catch (Exception e) {
                // already updated
            }

            ConfigHandler.loadTypes(statement);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
