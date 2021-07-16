package net.coreprotect.database.lookup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;

public class PlayerLookup {

    public static boolean playerExists(Connection connection, String user) {
        try {
            int id = -1;
            String uuid = null;

            if (ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT)) != null) {
                return true;
            }

            String collate = "";
            if (!Config.getGlobal().MYSQL) {
                collate = " COLLATE NOCASE";
            }

            String query = "SELECT rowid as id, uuid FROM " + ConfigHandler.prefix + "user WHERE user = ?" + collate + " LIMIT 0, 1";
            PreparedStatement preparedStmt = connection.prepareStatement(query);
            preparedStmt.setString(1, user);

            ResultSet results = preparedStmt.executeQuery();

            while (results.next()) {
                id = results.getInt("id");
                uuid = results.getString("uuid");
            }
            results.close();
            preparedStmt.close();

            if (id > -1) {
                if (uuid != null) {
                    ConfigHandler.uuidCache.put(user.toLowerCase(Locale.ROOT), uuid);
                    ConfigHandler.uuidCacheReversed.put(uuid, user);
                }

                ConfigHandler.playerIdCache.put(user.toLowerCase(Locale.ROOT), id);
                ConfigHandler.playerIdCacheReversed.put(id, user);
                return true;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

}
