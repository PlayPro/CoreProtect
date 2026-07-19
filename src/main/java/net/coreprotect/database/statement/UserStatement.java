package net.coreprotect.database.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.utility.ErrorReporter;

public class UserStatement {

    private UserStatement() {
        throw new IllegalStateException("Database class");
    }

    public static int insert(Connection connection, String user) {
        int id = -1;

        try {
            int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);

            PreparedStatement preparedStmt = null;
            if (Database.hasReturningKeys()) {
                preparedStmt = connection.prepareStatement("INSERT INTO " + ConfigHandler.prefix + "user (time, " + ConfigHandler.databaseType.getUserColumn() + ") VALUES (?, ?) RETURNING rowid");
            }
            else {
                preparedStmt = connection.prepareStatement("INSERT INTO " + ConfigHandler.prefix + "user (time, " + ConfigHandler.databaseType.getUserColumn() + ") VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            }

            preparedStmt.setInt(1, unixtimestamp);
            preparedStmt.setString(2, user);

            if (Database.hasReturningKeys()) {
                ResultSet resultSet = preparedStmt.executeQuery();
                resultSet.next();
                id = resultSet.getInt(1);
                resultSet.close();
            }
            else {
                preparedStmt.executeUpdate();
                ResultSet keys = preparedStmt.getGeneratedKeys();
                keys.next();
                id = keys.getInt(1);
                keys.close();
            }

            preparedStmt.close();
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
        return id;
    }

    public static int getId(ConsumerWriteBatch batch, String user, boolean load) throws Exception {
        String cacheKey = user.toLowerCase(Locale.ROOT);
        Integer id = ConfigHandler.playerIdCache.get(cacheKey);
        if (load && id == null) {
            id = batch.resolveUserId(user, null);
        }

        if (id == null) {
            throw new SQLException("Unable to resolve database user " + user);
        }
        return id;
    }

    public static int findId(Connection connection, String user) throws SQLException {
        String lowerUser = user.toLowerCase(Locale.ROOT);
        Integer cachedId = ConfigHandler.playerIdCache.get(lowerUser);
        if (cachedId != null) {
            return cachedId;
        }

        String userMatch = DatabaseUtils.caseInsensitiveEquals(ConfigHandler.databaseType.getUserColumn());
        String query = "SELECT rowid AS id," + ConfigHandler.databaseType.getUserColumn() + " AS username,uuid FROM " + ConfigHandler.prefix + "user WHERE " + userMatch + " ORDER BY rowid ASC LIMIT 1 OFFSET 0";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, user);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return -1;
                }
                int id = results.getInt("id");
                String username = results.getString("username");
                String uuid = results.getString("uuid");
                String canonicalLowerUser = username.toLowerCase(Locale.ROOT);
                ConfigHandler.playerIdCache.put(canonicalLowerUser, id);
                ConfigHandler.playerIdCacheReversed.put(id, username);
                if (uuid != null && !uuid.isEmpty()) {
                    ConfigHandler.uuidCache.put(canonicalLowerUser, uuid);
                    ConfigHandler.uuidCacheReversed.put(uuid, username);
                }
                return id;
            }
        }
    }

    public static int loadId(Connection connection, String user, String uuid) {
        // generate if doesn't exist
        int id = -1;

        try {
            String where = DatabaseUtils.caseInsensitiveEquals(ConfigHandler.databaseType.getUserColumn());
            if (uuid != null) {
                where = where + " OR uuid = ?";
            }

            String query = "SELECT rowid as id, uuid FROM " + ConfigHandler.prefix + "user WHERE " + where + " ORDER BY rowid ASC LIMIT 1 OFFSET 0";
            PreparedStatement preparedStmt = connection.prepareStatement(query);
            preparedStmt.setString(1, user);

            if (uuid != null) {
                preparedStmt.setString(2, uuid);
            }

            ResultSet resultSet = preparedStmt.executeQuery();
            while (resultSet.next()) {
                id = resultSet.getInt("id");
                uuid = resultSet.getString("uuid");
            }
            resultSet.close();
            preparedStmt.close();

            if (id == -1) {
                if (ConfigHandler.databaseType.isClickHouse()) {
                    try (ConsumerWriteBatch batch = Database.openConsumerWriteBatch(connection)) {
                        batch.begin();
                        id = batch.resolveUserId(user, uuid);
                        if (!batch.commit()) {
                            throw new SQLException("Unable to publish ClickHouse user " + user);
                        }
                    }
                }
                else {
                    id = insert(connection, user);
                }
            }

            ConfigHandler.playerIdCache.put(user.toLowerCase(Locale.ROOT), id);
            ConfigHandler.playerIdCacheReversed.put(id, user);
            if (uuid != null) {
                ConfigHandler.uuidCache.put(user.toLowerCase(Locale.ROOT), uuid);
                ConfigHandler.uuidCacheReversed.put(uuid, user);
            }
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }

        return id;
    }

    public static String loadName(Connection connection, int id) {
        String user = "";
        String uuid = null;

        try {
            String query = "SELECT " + ConfigHandler.databaseType.getUserColumn() + ",uuid FROM " + ConfigHandler.prefix + "user WHERE rowid=? LIMIT 1 OFFSET 0";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        user = resultSet.getString("user");
                        uuid = resultSet.getString("uuid");
                    }
                }
            }

            if (user.length() == 0) {
                return user;
            }

            ConfigHandler.playerIdCacheReversed.put(id, user);
            ConfigHandler.playerIdCache.put(user.toLowerCase(Locale.ROOT), id);
            if (uuid != null) {
                ConfigHandler.uuidCache.put(user.toLowerCase(Locale.ROOT), uuid);
                ConfigHandler.uuidCacheReversed.put(uuid, user);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return user;
    }

    public static String getName(Connection connection, int id) {
        String user = ConfigHandler.playerIdCacheReversed.get(id);
        if (user == null) {
            user = loadName(connection, id);
        }
        return user;
    }

    public static String getNameByUuid(String uuid) {
        return ConfigHandler.uuidCacheReversed.get(uuid);
    }

    public static String getUuid(Connection connection, String user) throws SQLException {
        String lowerUser = user.toLowerCase(Locale.ROOT);
        String uuid = ConfigHandler.uuidCache.get(lowerUser);
        if (uuid == null && findId(connection, user) > -1) {
            uuid = ConfigHandler.uuidCache.get(lowerUser);
        }
        return uuid;
    }

}
