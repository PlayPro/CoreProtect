package net.coreprotect.patch.script;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.patch.Patch;
import net.coreprotect.utility.ErrorReporter;

public class __2_25_0 {

    private static final int MYSQL_DUPLICATE_KEY_NAME = 1061;

    protected static boolean patchClickHouse(Statement statement) {
        return true;
    }

    protected static boolean patchDuckDB(Statement statement) {
        return true;
    }

    protected static boolean patch(Statement statement) {
        if (Config.getGlobal().MYSQL) {
            if (!createEntityInteractionTable(statement)
                    || !createMySQLPrefixIndex(statement, ConfigHandler.prefix + "chat", "message_prefix_index", "message(16)")
                    || !createMySQLPrefixIndex(statement, ConfigHandler.prefix + "command", "message_prefix_index", "message(16)")) {
                return false;
            }
            for (int line = 1; line <= 8; line++) {
                if (!createMySQLPrefixIndex(statement, ConfigHandler.prefix + "sign", "line_" + line + "_prefix_index", "line_" + line + "(16)")) {
                    return false;
                }
            }
            return widenMySQLEntityData(statement, ConfigHandler.prefix + "entity");
        }

        Connection connection = null;
        boolean autoCommit = true;
        try {
            connection = statement.getConnection();
            autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            if (!createEntityInteractionTable(statement)) {
                connection.rollback();
                return false;
            }
            String[] indexQueries = new String[10];
            indexQueries[0] = "CREATE INDEX IF NOT EXISTS chat_message_prefix_index ON " + ConfigHandler.prefix + "chat(substr(message,1,16) COLLATE NOCASE)";
            indexQueries[1] = "CREATE INDEX IF NOT EXISTS command_message_prefix_index ON " + ConfigHandler.prefix + "command(substr(message,1,16) COLLATE NOCASE)";
            for (int line = 1; line <= 8; line++) {
                indexQueries[line + 1] = "CREATE INDEX IF NOT EXISTS sign_line_" + line + "_prefix_index ON " + ConfigHandler.prefix + "sign(substr(line_" + line + ",1,16) COLLATE NOCASE)";
            }
            for (String query : indexQueries) {
                statement.executeUpdate(query);
                if (!Patch.continuePatch()) {
                    connection.rollback();
                    return false;
                }
            }
            connection.commit();
            return true;
        }
        catch (Exception e) {
            rollback(connection);
            ErrorReporter.report(e);
            return false;
        }
        finally {
            restoreAutoCommit(connection, autoCommit);
        }
    }

    private static boolean createEntityInteractionTable(Statement statement) {
        String table = ConfigHandler.prefix + "entity_interaction";
        try {
            if (Config.getGlobal().MYSQL) {
                String indexes = ", INDEX(wid,x,z,time), INDEX(entity_spawn_rowid,time), INDEX(user,time), INDEX(type,time), INDEX(action,time)";
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + "(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, user int, entity_spawn_rowid int NOT NULL, wid int, x int, y int, z int, type int, action tinyint, metadata mediumblob, rolled_back tinyint" + indexes + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
            }
            else {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " (time INTEGER, user INTEGER, entity_spawn_rowid INTEGER NOT NULL, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, type INTEGER, action INTEGER, metadata BLOB, rolled_back INTEGER)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS entity_interaction_index ON " + table + "(wid,x,z,time)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS entity_interaction_spawn_index ON " + table + "(entity_spawn_rowid,time)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS entity_interaction_user_index ON " + table + "(user,time)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS entity_interaction_type_index ON " + table + "(type,time)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS entity_interaction_action_index ON " + table + "(action,time)");
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static boolean createMySQLPrefixIndex(Statement statement, String table, String index, String column) {
        try {
            statement.executeUpdate("CREATE INDEX " + index + " ON " + table + "(" + column + ")");
            return true;
        }
        catch (SQLException e) {
            if (e.getErrorCode() == MYSQL_DUPLICATE_KEY_NAME) {
                return true;
            }

            ErrorReporter.report(e);
            return false;
        }
    }

    private static boolean widenMySQLEntityData(Statement statement, String table) {
        try {
            try (ResultSet resultSet = statement.executeQuery("SHOW COLUMNS FROM " + table + " LIKE 'data'")) {
                if (resultSet.next() && "mediumblob".equalsIgnoreCase(resultSet.getString("Type"))) {
                    return true;
                }
            }

            statement.executeUpdate("ALTER TABLE " + table + " MODIFY data MEDIUMBLOB");
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean autoCommit) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(autoCommit);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }
}
