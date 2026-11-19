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
import net.coreprotect.database.clickhouse.ClickHouseDatabase;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.StringUtils;

public class UserStatement {

    private static final String RELATIONAL_NAME_ORDER = "(COALESCE(uuid,'')<>'') DESC,"
            + "CASE WHEN COALESCE(uuid,'')<>'' THEN time END DESC,"
            + "CASE WHEN COALESCE(uuid,'')<>'' THEN -rowid ELSE rowid END";

    private UserStatement() {
        throw new IllegalStateException("Database class");
    }

    public static int insert(Connection connection, String user) {
        try {
            return storeIdentity(connection, null, StringUtils.normalizeUsername(user), null, currentTime()).getId();
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
            return -1;
        }
    }

    public static int getId(ConsumerWriteBatch batch, String user, boolean load) throws Exception {
        user = StringUtils.normalizeUsername(user);
        Integer id = load ? Integer.valueOf(batch.resolveUserId(user, null)) : ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
        if (id == null || id < 1) {
            throw new SQLException("Unable to resolve database user " + user);
        }
        return id;
    }

    public static int findId(Connection connection, String user) throws SQLException {
        return findId(connection, user, true);
    }

    public static int findId(Connection connection, String user, boolean useCache) throws SQLException {
        user = StringUtils.normalizeUsername(user);
        if (useCache) {
            Integer cachedId = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
            if (cachedId != null && cachedId > 0) {
                return cachedId;
            }
        }

        Identity identity = findByName(connection, user);
        if (identity == null) {
            return -1;
        }
        if (useCache) {
            publishIdentity(identity);
        }
        return identity.getId();
    }

    private static Identity findByName(Connection connection, String user) throws SQLException {
        String userMatch = DatabaseUtils.caseInsensitiveEquals(ConfigHandler.databaseType.getUserColumn());
        String order = ConfigHandler.databaseType.isClickHouse() ? ClickHouseDatabase.USER_NAME_ORDER : RELATIONAL_NAME_ORDER;
        return findIdentity(connection, userMatch, user, order);
    }

    public static Identity findIdentity(Connection connection, String user, String uuid) throws SQLException {
        if (uuid != null) {
            Identity identity = findIdentity(connection, "uuid=?", uuid, "rowid ASC");
            if (identity != null) {
                return identity;
            }
        }
        Identity identity = findByName(connection, user);
        return identity == null || uuid == null || identity.getUuid() == null || uuid.equals(identity.getUuid()) ? identity : null;
    }

    private static Identity findIdentity(Connection connection, String condition, String value, String order) throws SQLException {
        String query = "SELECT rowid AS id," + ConfigHandler.databaseType.getUserColumn() + " AS username,uuid FROM " + ConfigHandler.prefix
                + "user WHERE " + condition + " ORDER BY " + order + " LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, value);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? new Identity(results.getInt("id"), results.getString("username"), normalizeUuid(results.getString("uuid"))) : null;
            }
        }
    }

    public static Identity storeIdentity(Connection connection, Identity previous, String user, String uuid, int time) throws SQLException {
        if (user == null || user.isEmpty() || (previous != null && previous.getUuid() != null && !previous.getUuid().equals(uuid))) {
            throw new SQLException("Invalid database user assignment for " + user);
        }
        String column = ConfigHandler.databaseType.getUserColumn();
        String table = ConfigHandler.prefix + "user";
        if (previous != null) {
            String condition = previous.getUuid() == null ? "(uuid IS NULL OR uuid='')" : "uuid=?";
            String query = "UPDATE " + table + " SET time=?," + column + "=?,uuid=? WHERE rowid=? AND " + condition;
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, time);
                statement.setString(2, user);
                statement.setString(3, uuid);
                statement.setInt(4, previous.getId());
                if (previous.getUuid() != null) {
                    statement.setString(5, previous.getUuid());
                }
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Database user identity changed while assigning " + user);
                }
            }
            return new Identity(previous.getId(), user, uuid);
        }

        String query = "INSERT INTO " + table + " (time," + column + ",uuid) VALUES (?,?,?)";
        boolean returning = Database.hasReturningKeys();
        try (PreparedStatement statement = returning ? connection.prepareStatement(query + " RETURNING rowid")
                : connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, time);
            statement.setString(2, user);
            statement.setString(3, uuid);
            if (!returning && statement.executeUpdate() != 1) {
                throw new SQLException("Unable to insert database user " + user);
            }
            try (ResultSet keys = returning ? statement.executeQuery() : statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No identifier returned for database user " + user);
                }
                long id = keys.getLong(1);
                if (id < 1 || id > Integer.MAX_VALUE) {
                    throw new SQLException("Invalid identifier returned for database user " + user);
                }
                return new Identity((int) id, user, uuid);
            }
        }
    }

    public static int loadId(Connection connection, String user, String uuid) {
        user = StringUtils.normalizeUsername(user);
        uuid = normalizeUuid(uuid);

        try {
            if (ConfigHandler.databaseType.isClickHouse()) {
                try (ConsumerWriteBatch batch = Database.openConsumerWriteBatch(connection)) {
                    batch.begin();
                    int resolvedId = batch.resolveUserId(user, uuid);
                    if (!batch.commit()) {
                        throw new SQLException("Unable to publish ClickHouse user " + user);
                    }
                    return resolvedId;
                }
            }
            Identity previous = findIdentity(connection, user, uuid);
            Identity identity = previous;
            if (previous == null || (uuid != null && (previous.getUuid() == null || !previous.getName().equalsIgnoreCase(user)))) {
                identity = storeIdentity(connection, previous, user, uuid, currentTime());
            }
            if (connection.getAutoCommit()) {
                evictPreviousName(previous, identity);
                publishIdentity(identity);
            }
            return identity.getId();
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
            return -1;
        }
    }

    public static void publishIdentity(Identity identity) {
        String key = identity.getName().toLowerCase(Locale.ROOT);
        synchronized (ConfigHandler.playerIdCache) {
            ConfigHandler.playerIdCacheReversed.put(identity.getId(), identity.getName());
            if (identity.getUuid() != null) {
                ConfigHandler.uuidCache.put(key, identity.getUuid());
                ConfigHandler.uuidCacheReversed.put(identity.getUuid(), identity.getName());
            }
            else {
                ConfigHandler.uuidCache.remove(key);
            }
            ConfigHandler.playerIdCache.put(key, identity.getId());
        }
    }

    public static void evictPreviousName(Identity previous, Identity identity) {
        if (previous != null && !previous.getName().equalsIgnoreCase(identity.getName())) {
            String key = previous.getName().toLowerCase(Locale.ROOT);
            synchronized (ConfigHandler.playerIdCache) {
                ConfigHandler.playerIdCache.remove(key, previous.getId());
                if (previous.getUuid() != null) {
                    ConfigHandler.uuidCache.remove(key, previous.getUuid());
                }
            }
        }
    }

    public static String normalizeUuid(String uuid) {
        return uuid == null || uuid.isBlank() ? null : uuid;
    }

    private static int currentTime() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    public static final class Identity {

        private final int id;
        private final String name;
        private final String uuid;

        private Identity(int id, String name, String uuid) throws SQLException {
            if (id < 1 || name == null || name.isEmpty()) {
                throw new SQLException("Invalid database user identity " + id);
            }
            this.id = id;
            this.name = name;
            this.uuid = uuid;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getUuid() {
            return uuid;
        }
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
            if (uuid != null && !uuid.isEmpty()) {
                ConfigHandler.uuidCacheReversed.put(uuid, user);
            }
        }
        catch (Exception e) {
            if (ConfigHandler.databaseType.isClickHouse()) {
                throw new IllegalStateException("Unable to resolve ClickHouse user identifier " + id, e);
            }
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

    public static String getNameByUuid(Connection connection, String uuid) {
        if (normalizeUuid(uuid) == null) {
            return null;
        }
        String user = ConfigHandler.uuidCacheReversed.get(uuid);
        if (user != null) {
            return user;
        }

        try {
            String query = "SELECT rowid," + ConfigHandler.databaseType.getUserColumn() + " FROM " + ConfigHandler.prefix + "user WHERE uuid=? ORDER BY rowid LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, uuid);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return null;
                    }
                    int id = resultSet.getInt("rowid");
                    String resolvedUser = resultSet.getString("user");
                    if (resolvedUser == null || resolvedUser.isEmpty()) {
                        return null;
                    }
                    ConfigHandler.playerIdCacheReversed.putIfAbsent(id, resolvedUser);
                    String cachedUser = ConfigHandler.uuidCacheReversed.putIfAbsent(uuid, resolvedUser);
                    return cachedUser == null ? resolvedUser : cachedUser;
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("Unable to resolve user UUID " + uuid, e);
        }
    }

    public static String getUuid(Connection connection, String user) throws SQLException {
        user = StringUtils.normalizeUsername(user);
        String lowerUser = user.toLowerCase(Locale.ROOT);
        String uuid = ConfigHandler.uuidCache.get(lowerUser);
        if (uuid != null && (!ConfigHandler.databaseType.isClickHouse() || !uuid.isEmpty())) {
            return uuid;
        }
        int id = findId(connection, user);
        if (id < 1) {
            return null;
        }
        if (ConfigHandler.databaseType.isClickHouse()) {
            uuid = ConfigHandler.uuidCache.get(lowerUser);
            if (uuid != null && !uuid.isEmpty()) {
                return uuid;
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT uuid FROM " + ConfigHandler.prefix + "user WHERE rowid=? LIMIT 1")) {
                statement.setInt(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new SQLException("ClickHouse user identifier " + id + " has no user row");
                    }
                    uuid = resultSet.getString("uuid");
                    if (uuid == null || uuid.isEmpty()) {
                        return null;
                    }
                    ConfigHandler.uuidCache.put(lowerUser, uuid);
                    return uuid;
                }
            }
        }
        return ConfigHandler.uuidCache.get(lowerUser);
    }

    public static void clearClickHouseCaches() {
        ConfigHandler.playerIdCache.clear();
        ConfigHandler.playerIdCacheReversed.clear();
        ConfigHandler.uuidCache.clear();
        ConfigHandler.uuidCacheReversed.clear();
    }

}
