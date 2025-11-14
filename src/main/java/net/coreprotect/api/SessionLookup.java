package net.coreprotect.api;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;

/**
 * Provides API methods for looking up player session data in the CoreProtect database.
 * Session data includes login/logout events and their associated timestamps and locations.
 */
public class SessionLookup {

    /**
     * The session type ID used for identifying session-related events.
     */
    public static final int ID = 0;

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with static methods only.
     */
    private SessionLookup() {
        throw new IllegalStateException("API class");
    }

    /**
     * Performs a lookup of session-related actions for the specified user.
     * This returns login and logout events within the specified time constraint.
     * 
     * @param user
     *            The username to look up session data for
     * @param offset
     *            Time constraint in seconds (0 means no time constraint)
     * @return List of results in a String array format, empty list if API is disabled or no results found
     */
    public static List<String[]> performLookup(String user, int offset) {
        List<String[]> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        if (user == null) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            // Prepare lookup parameters
            String type = String.valueOf(ID);
            int time = (int) (System.currentTimeMillis() / 1000L);
            int checkTime = calculateCheckTime(time, offset);

            // Get user ID from cache or load it
            int userId = getUserId(connection, user);

            // Query session data from database
            try (Statement statement = connection.createStatement()) {
                String query = buildSessionQuery(userId, checkTime);

                try (ResultSet results = statement.executeQuery(query)) {
                    while (results.next()) {
                        String[] sessionData = extractSessionData(connection, results, type);
                        result.add(sessionData);
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Calculates the time threshold for the session lookup.
     * 
     * @param currentTime
     *            The current time in seconds
     * @param offset
     *            The time offset in seconds
     * @return The calculated time threshold
     */
    private static int calculateCheckTime(int currentTime, int offset) {
        return (offset > 0) ? currentTime - offset : 0;
    }

    /**
     * Gets the user ID for the specified username.
     * If the user ID is not in the cache, it will be loaded from the database.
     * 
     * @param connection
     *            The database connection
     * @param username
     *            The username to get the ID for
     * @return The user ID
     */
    private static int getUserId(Connection connection, String username) {
        String lowerUsername = username.toLowerCase(Locale.ROOT);

        if (ConfigHandler.playerIdCache.get(lowerUsername) == null) {
            UserStatement.loadId(connection, username, null);
        }

        return ConfigHandler.playerIdCache.get(lowerUsername);
    }

    /**
     * Builds the SQL query for retrieving session data.
     * 
     * @param userId
     *            The user ID to query for
     * @param checkTime
     *            The time threshold for the query
     * @return The SQL query string
     */
    private static String buildSessionQuery(int userId, int checkTime) {
        return "SELECT time,user,wid,x,y,z,action FROM " + ConfigHandler.prefix + "session WHERE user = '" + userId + "' AND time > '" + checkTime + "' ORDER BY rowid DESC";
    }

    /**
     * Extracts session data from a result set row.
     * 
     * @param connection
     *            The database connection
     * @param results
     *            The result set to extract data from
     * @param type
     *            The session type ID
     * @return An array of session data values
     * @throws Exception
     *             if an error occurs while extracting data
     */
    private static String[] extractSessionData(Connection connection, ResultSet results, String type) throws Exception {
        String resultTime = results.getString("time");
        int resultUserId = results.getInt("user");
        String resultWorldId = results.getString("wid");
        String resultX = results.getString("x");
        String resultY = results.getString("y");
        String resultZ = results.getString("z");
        String resultAction = results.getString("action");

        // Get username from cache or load it
        if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
            UserStatement.loadName(connection, resultUserId);
        }
        String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);

        // Create and return the session data array
        return new String[] { resultTime, resultUser, resultX, resultY, resultZ, resultWorldId, type, resultAction };
    }
}
