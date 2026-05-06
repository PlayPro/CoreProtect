package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;

import net.coreprotect.api.result.MessageResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.WorldUtils;

/**
 * Provides API methods for player chat and command lookups.
 */
public class MessageAPI {
    private static final int CHAT_ACTION_ID = 6;
    private static final int COMMAND_ACTION_ID = 7;

    private MessageAPI() {
        throw new IllegalStateException("API class");
    }

    public static List<MessageResult> performChatLookup(String user, int offset) {
        return performLookup("chat", CHAT_ACTION_ID, user, offset, -1, null);
    }

    public static List<MessageResult> performChatLookup(String user, int offset, int radius, Location location) {
        return performLookup("chat", CHAT_ACTION_ID, user, offset, radius, location);
    }

    public static List<MessageResult> performCommandLookup(String user, int offset) {
        return performLookup("command", COMMAND_ACTION_ID, user, offset, -1, null);
    }

    public static List<MessageResult> performCommandLookup(String user, int offset, int radius, Location location) {
        return performLookup("command", COMMAND_ACTION_ID, user, offset, radius, location);
    }

    private static List<MessageResult> performLookup(String table, int actionId, String user, int offset, int radius, Location location) {
        List<MessageResult> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        if (location != null && location.getWorld() == null) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            Integer userId = getUserId(connection, user);
            if (userId != null && userId == -1) {
                return result;
            }

            int checkTime = 0;
            if (offset > 0) {
                checkTime = (int) (System.currentTimeMillis() / 1000L) - offset;
            }

            StringBuilder query = new StringBuilder("SELECT time,user,wid,x,y,z,message FROM ");
            query.append(ConfigHandler.prefix).append(table).append(" ");
            if (location != null) {
                query.append(WorldUtils.getWidIndex(table));
            }
            query.append("WHERE time > ?");

            if (userId != null) {
                query.append(" AND user = ?");
            }

            if (location != null) {
                query.append(" AND wid = ?");
                if (radius > 0) {
                    query.append(" AND x >= ? AND x <= ? AND z >= ? AND z <= ?");
                }
                else {
                    query.append(" AND x = ? AND y = ? AND z = ?");
                }
            }

            query.append(" ORDER BY rowid DESC");

            try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                int parameterIndex = 1;
                statement.setInt(parameterIndex++, checkTime);

                if (userId != null) {
                    statement.setInt(parameterIndex++, userId);
                }

                if (location != null) {
                    int x = location.getBlockX();
                    int y = location.getBlockY();
                    int z = location.getBlockZ();
                    statement.setInt(parameterIndex++, WorldUtils.getWorldId(location.getWorld().getName()));

                    if (radius > 0) {
                        statement.setInt(parameterIndex++, clampToInt((long) x - radius));
                        statement.setInt(parameterIndex++, clampToInt((long) x + radius));
                        statement.setInt(parameterIndex++, clampToInt((long) z - radius));
                        statement.setInt(parameterIndex++, clampToInt((long) z + radius));
                    }
                    else {
                        statement.setInt(parameterIndex++, x);
                        statement.setInt(parameterIndex++, y);
                        statement.setInt(parameterIndex, z);
                    }
                }

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        result.add(parseMessageResult(connection, results, actionId));
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private static Integer getUserId(Connection connection, String user) throws Exception {
        if (user == null || user.isEmpty() || user.equalsIgnoreCase("#global")) {
            return null;
        }

        Integer cachedId = ConfigHandler.playerIdCache.get(user.toLowerCase(java.util.Locale.ROOT));
        if (cachedId != null) {
            return cachedId;
        }

        String collate = Config.getGlobal().MYSQL ? "" : " COLLATE NOCASE";
        try (PreparedStatement statement = connection.prepareStatement("SELECT rowid FROM " + ConfigHandler.prefix + "user WHERE user = ?" + collate + " ORDER BY rowid ASC LIMIT 0, 1")) {
            statement.setString(1, user);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    int id = results.getInt("rowid");
                    ConfigHandler.playerIdCache.put(user.toLowerCase(java.util.Locale.ROOT), id);
                    ConfigHandler.playerIdCacheReversed.put(id, user);
                    return id;
                }
            }
        }

        return -1;
    }

    private static MessageResult parseMessageResult(Connection connection, ResultSet results, int actionId) throws Exception {
        int userId = results.getInt("user");
        String username = ConfigHandler.playerIdCacheReversed.get(userId);
        if (username == null) {
            username = UserStatement.loadName(connection, userId);
        }

        return new MessageResult(
                results.getLong("time"), username, WorldUtils.getWorldName(results.getInt("wid")),
                results.getInt("x"), results.getInt("y"), results.getInt("z"),
                results.getString("message"), actionId
        );
    }

    private static int clampToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        else if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }

        return (int) value;
    }
}
