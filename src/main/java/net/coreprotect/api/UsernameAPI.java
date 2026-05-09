package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.coreprotect.api.result.UsernameResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;

/**
 * Provides API methods for username history lookups.
 */
public class UsernameAPI {

    private UsernameAPI() {
        throw new IllegalStateException("API class");
    }

    public static List<UsernameResult> performLookup(String user, int offset) {
        return performLookup(LookupOptions.builder().user(user).time(offset).build());
    }

    public static List<UsernameResult> performLookup(LookupOptions options) {
        List<UsernameResult> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        if (options == null) {
            options = LookupOptions.builder().build();
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            Set<String> uuids = getUuids(connection, options.getUser());
            if (uuids == null) {
                return result;
            }

            int checkTime = 0;
            if (options.getTime() > 0) {
                checkTime = (int) (System.currentTimeMillis() / 1000L) - options.getTime();
            }

            StringBuilder query = new StringBuilder("SELECT time,uuid,user FROM ");
            query.append(ConfigHandler.prefix).append("username_log WHERE time > ?");
            if (!uuids.isEmpty()) {
                query.append(" AND uuid IN (");
                appendPlaceholders(query, uuids.size());
                query.append(")");
            }
            query.append(" ORDER BY rowid DESC");
            if (options.hasLimit()) {
                query.append(" LIMIT ").append(options.getLimitOffset()).append(", ").append(options.getLimitCount());
            }

            try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                int parameterIndex = 1;
                statement.setInt(parameterIndex++, checkTime);
                for (String uuid : uuids) {
                    statement.setString(parameterIndex++, uuid);
                }

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        result.add(parseUsernameResult(results));
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private static void appendPlaceholders(StringBuilder query, int count) {
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                query.append(",");
            }
            query.append("?");
        }
    }

    private static Set<String> getUuids(Connection connection, String user) throws Exception {
        Set<String> result = new LinkedHashSet<>();

        if (user == null || user.isEmpty() || user.equalsIgnoreCase("#global")) {
            return result;
        }

        String cachedUuid = ConfigHandler.uuidCache.get(user.toLowerCase(Locale.ROOT));
        if (cachedUuid != null) {
            result.add(cachedUuid);
        }

        String collate = Config.getGlobal().MYSQL ? "" : " COLLATE NOCASE";
        try (PreparedStatement statement = connection.prepareStatement("SELECT uuid FROM " + ConfigHandler.prefix + "user WHERE user = ?" + collate)) {
            statement.setString(1, user);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    result.add(results.getString("uuid"));
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("SELECT uuid FROM " + ConfigHandler.prefix + "username_log WHERE user = ?" + collate)) {
            statement.setString(1, user);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    result.add(results.getString("uuid"));
                }
            }
        }

        if (looksLikeUuid(user)) {
            result.add(user);
        }

        return result.isEmpty() ? null : result;
    }

    private static boolean looksLikeUuid(String value) {
        return value.length() == 36 && value.charAt(8) == '-' && value.charAt(13) == '-' && value.charAt(18) == '-' && value.charAt(23) == '-';
    }

    private static UsernameResult parseUsernameResult(ResultSet results) throws Exception {
        String uuid = results.getString("uuid");
        String username = results.getString("user");
        String player = ConfigHandler.uuidCacheReversed.get(uuid);
        if (player == null) {
            player = username;
        }

        return new UsernameResult(results.getLong("time"), uuid, username, player);
    }
}
