package net.coreprotect.database.lookup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.ErrorReporter;

public class PlayerLookup {

    public static boolean playerExists(Connection connection, String user) {
        try {
            int id = -1;
            String uuid = null;

            if (ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT)) != null) {
                return true;
            }

            try (PreparedStatement preparedStmt = connection.prepareStatement("SELECT rowid as id, uuid FROM " + ConfigHandler.prefix + "user WHERE lower(user) = ? LIMIT 1")) {
                preparedStmt.setString(1, user.toLowerCase(Locale.ROOT));

                ResultSet results = preparedStmt.executeQuery();
                if (results.next()) {
                    id = results.getInt("id");
                    uuid = results.getString("uuid");
                }
            }

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
            ErrorReporter.report(e);
        }

        return false;
    }

    public static String playerName(int playerId) {
        final String cachedName = ConfigHandler.playerIdCacheReversed.get(playerId);
        if (cachedName != null) {
            return cachedName;
        }

        try (final Connection connection = Database.getConnection(false, 250)) {
            return UserStatement.loadName(connection, playerId);
        } catch (SQLException e) {
            CoreProtect.getInstance().getSLF4JLogger().warn("Failed to query player name for id {}", playerId, e);
            return null;
        }
    }
}
