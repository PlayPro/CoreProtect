package net.coreprotect.patch.script;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.patch.Patch;

public class __2_16_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                try {
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "skull MODIFY owner VARCHAR(64), DROP COLUMN type, DROP COLUMN data, DROP COLUMN rotation");
                }
                catch (Exception e) {
                    // update already ran
                }
            }
            else {
                statement.executeUpdate("BEGIN TRANSACTION");
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "skull RENAME TO " + ConfigHandler.prefix + "skull_temp");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + ConfigHandler.prefix + "skull (id INTEGER PRIMARY KEY ASC, time INTEGER, owner TEXT);");
                statement.executeUpdate("INSERT INTO " + ConfigHandler.prefix + "skull SELECT id, time, owner FROM " + ConfigHandler.prefix + "skull_temp");
                statement.executeUpdate("DROP TABLE " + ConfigHandler.prefix + "skull_temp");
                statement.executeUpdate("COMMIT TRANSACTION");
            }

            if (!Patch.continuePatch()) {
                return false;
            }

            try {
                String idList = "";
                String query = "SELECT id FROM " + ConfigHandler.prefix + "material_map WHERE material LIKE '%_CONCRETE' OR material LIKE '%_CONCRETE_POWDER'";
                ResultSet resultSet = statement.executeQuery(query);
                while (resultSet.next()) {
                    String id = resultSet.getString("id");
                    if (idList.length() == 0) {
                        idList = id;
                    }
                    else {
                        idList = idList + ", " + id;
                    }
                }
                resultSet.close();

                if (idList.length() > 0) {
                    query = "SELECT rowid as id FROM " + ConfigHandler.prefix + "block WHERE type IN(" + idList + ") AND y='0'";
                    String preparedQueryDelete = "DELETE FROM " + ConfigHandler.prefix + "block WHERE rowid = ?";
                    PreparedStatement preparedStatementDelete = statement.getConnection().prepareStatement(preparedQueryDelete);
                    Database.beginTransaction(statement, Config.getGlobal().MYSQL);
                    resultSet = statement.executeQuery(query);
                    while (resultSet.next()) {
                        int rowid = resultSet.getInt("id");
                        preparedStatementDelete.setInt(1, rowid);
                        preparedStatementDelete.executeUpdate();
                    }
                    resultSet.close();
                    Database.commitTransaction(statement, Config.getGlobal().MYSQL);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            if (!Patch.continuePatch()) {
                return false;
            }

            String query = "SELECT rowid as id, user FROM " + ConfigHandler.prefix + "user WHERE uuid IS NULL";
            String preparedQuerySelect = "SELECT EXISTS (SELECT user FROM " + ConfigHandler.prefix + "session WHERE user = ?) OR EXISTS (SELECT user FROM " + ConfigHandler.prefix + "container WHERE user = ?) OR EXISTS (SELECT user FROM " + ConfigHandler.prefix + "command WHERE user = ?) OR EXISTS (SELECT user FROM " + ConfigHandler.prefix + "chat WHERE user = ?) OR EXISTS (SELECT user FROM " + ConfigHandler.prefix + "block WHERE user = ?) as userExists";
            String preparedQueryDelete = "DELETE FROM " + ConfigHandler.prefix + "user WHERE rowid = ?";
            PreparedStatement preparedStatementSelect = statement.getConnection().prepareStatement(preparedQuerySelect);
            PreparedStatement preparedStatementDelete = statement.getConnection().prepareStatement(preparedQueryDelete);

            Database.beginTransaction(statement, Config.getGlobal().MYSQL);
            ResultSet resultSet = statement.executeQuery(query);
            while (resultSet.next()) {
                int rowid = resultSet.getInt("id");
                String user = resultSet.getString("user");
                if (!user.startsWith("#")) {
                    Database.setMultiInt(preparedStatementSelect, rowid, 5);
                    ResultSet resultSetUser = preparedStatementSelect.executeQuery();
                    resultSetUser.next();
                    boolean userExists = resultSetUser.getBoolean("userExists");
                    if (!userExists) {
                        preparedStatementDelete.setInt(1, rowid);
                        preparedStatementDelete.executeUpdate();
                    }
                    resultSetUser.close();
                }
            }
            resultSet.close();
            Database.commitTransaction(statement, Config.getGlobal().MYSQL);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
