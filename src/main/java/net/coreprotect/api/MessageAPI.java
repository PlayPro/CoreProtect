package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

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
    private MessageAPI() {
        throw new IllegalStateException("API class");
    }

    public static List<MessageResult> performChatLookup(String user, int offset) {
        return performChatLookup(LookupOptions.builder().user(user).time(offset).build());
    }

    public static List<MessageResult> performChatLookup(LookupOptions options) {
        return performLookup("chat", CoreProtectAction.CHAT, options);
    }

    public static List<MessageResult> performCommandLookup(String user, int offset) {
        return performCommandLookup(LookupOptions.builder().user(user).time(offset).build());
    }

    public static List<MessageResult> performCommandLookup(LookupOptions options) {
        return performLookup("command", CoreProtectAction.COMMAND, options);
    }

    private static List<MessageResult> performLookup(String table, CoreProtectAction action, LookupOptions options) {
        List<MessageResult> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            LookupFilter filter = LookupFilter.fromOptions(connection, options);
            if (filter.hasInvalidUser() || filter.hasInvalidLocation()) {
                return result;
            }

            StringBuilder query = new StringBuilder("SELECT time,user,wid,x,y,z,message FROM ");
            query.append(ConfigHandler.prefix).append(table).append(" ");
            if (filter.hasLocation()) {
                query.append(WorldUtils.getWidIndex(table));
            }
            filter.appendWhere(query);
            query.append(" ORDER BY rowid DESC");
            filter.appendLimit(query);

            try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                filter.bind(statement);

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        result.add(parseMessageResult(connection, results, action.id()));
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    static Integer getUserId(Connection connection, String user) throws Exception {
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

    static int clampToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        else if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }

        return (int) value;
    }
}
