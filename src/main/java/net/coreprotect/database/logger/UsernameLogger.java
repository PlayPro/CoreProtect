package net.coreprotect.database.logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

import net.coreprotect.config.ConfigHandler;

public class UsernameLogger {

    private UsernameLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(Connection connection, String user, String uuid, int configUsernames, int time) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }

            int idRow = -1;
            String userRow = null;
            String query = "SELECT rowid as id, user FROM " + ConfigHandler.prefix + "user WHERE uuid = ? LIMIT 0, 1";
            PreparedStatement preparedStmt = connection.prepareStatement(query);
            preparedStmt.setString(1, uuid);
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                idRow = rs.getInt("id");
                userRow = rs.getString("user").toLowerCase(Locale.ROOT);
            }
            rs.close();
            preparedStmt.close();

            boolean update = false;
            if (userRow == null) {
                idRow = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
                update = true;
            }
            else if (!user.equalsIgnoreCase(userRow)) {
                update = true;
            }

            if (update) {
                preparedStmt = connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "user SET user = ?, uuid = ? WHERE rowid = ?");
                preparedStmt.setString(1, user);
                preparedStmt.setString(2, uuid);
                preparedStmt.setInt(3, idRow);
                preparedStmt.executeUpdate();
                preparedStmt.close();

                /*
                    //Commented out to prevent potential issues if player manages to stay logged in with old username
                    if (ConfigHandler.playerIdCache.get(user_row)!=null){
                        int cache_id = ConfigHandler.playerIdCache.get(user_row);
                        if (cache_id==id_row){
                            ConfigHandler.playerIdCache.remove(user_row);
                        }
                    }
                 */
            }
            else {
                boolean foundUUID = false;
                query = "SELECT rowid as id FROM " + ConfigHandler.prefix + "username_log WHERE uuid = ? AND user = ? LIMIT 0, 1";
                PreparedStatement preparedStatement = connection.prepareStatement(query);
                preparedStatement.setString(1, uuid);
                preparedStatement.setString(2, user);
                rs = preparedStatement.executeQuery();
                while (rs.next()) {
                    foundUUID = true;
                }
                rs.close();
                preparedStatement.close();

                if (!foundUUID) {
                    update = true;
                }
            }

            if (update && configUsernames == 1) {
                preparedStmt = connection.prepareStatement("INSERT INTO " + ConfigHandler.prefix + "username_log (time, uuid, user) VALUES (?, ?, ?)");
                preparedStmt.setInt(1, time);
                preparedStmt.setString(2, uuid);
                preparedStmt.setString(3, user);
                preparedStmt.executeUpdate();
                preparedStmt.close();
            }

            ConfigHandler.playerIdCache.put(user.toLowerCase(Locale.ROOT), idRow);
            ConfigHandler.playerIdCacheReversed.put(idRow, user);
            ConfigHandler.uuidCache.put(user.toLowerCase(Locale.ROOT), uuid);
            ConfigHandler.uuidCacheReversed.put(uuid, user);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
