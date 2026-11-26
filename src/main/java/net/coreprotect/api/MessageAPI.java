package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import net.coreprotect.api.result.MessageResult;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.LookupRaw;
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
        return LookupFilter.execute(options, (connection, filter, selected, result) -> {
            StringBuilder query = new StringBuilder("SELECT time," + ConfigHandler.databaseType.getUserColumn() + ",wid,x,y,z,message FROM ");
            query.append(filter.table(connection, table, "")).append(' ');
            if (filter.hasLocation() && !ConfigHandler.databaseType.isDuckDB()) {
                query.append(WorldUtils.getWidIndex(table));
            }
            filter.appendWhere(query);
            List<String> messageBindings = new ArrayList<>();
            query = new StringBuilder(LookupRaw.appendMessageFilters(query.toString(),
                    selected.getTextStartsWithAny(),
                    selected.getTextStartsWithNone(), table, messageBindings));
            query.append(" ORDER BY ").append(ConfigHandler.getDescendingEventOrder());
            filter.appendLimit(query);

            try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                int parameterIndex = filter.bind(statement);
                for (String binding : messageBindings) {
                    statement.setString(parameterIndex++, binding);
                }

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        result.add(parseMessageResult(connection, results, action.id()));
                    }
                }
            }
        });
    }

    private static MessageResult parseMessageResult(Connection connection, ResultSet results, int actionId) throws Exception {
        int userId = results.getInt("user");
        String username = UserStatement.getName(connection, userId);

        return new MessageResult(
                results.getLong("time"), username, WorldUtils.getWorldName(results.getInt("wid")),
                results.getInt("x"), results.getInt("y"), results.getInt("z"),
                results.getString("message"), actionId
        );
    }

}
