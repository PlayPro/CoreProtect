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

    protected static boolean patch(Statement statement) {
        if (Config.getGlobal().MYSQL) {
            return createMySQLMessagePrefixIndex(statement, ConfigHandler.prefix + "chat")
                    && createMySQLMessagePrefixIndex(statement, ConfigHandler.prefix + "command")
                    && widenMySQLEntityData(statement, ConfigHandler.prefix + "entity");
        }

        Connection connection = null;
        boolean autoCommit = true;
        try {
            connection = statement.getConnection();
            autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS chat_message_prefix_index ON " + ConfigHandler.prefix + "chat(substr(message,1,16) COLLATE NOCASE)");
            if (!Patch.continuePatch()) {
                connection.rollback();
                return false;
            }
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS command_message_prefix_index ON " + ConfigHandler.prefix + "command(substr(message,1,16) COLLATE NOCASE)");
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

    private static boolean createMySQLMessagePrefixIndex(Statement statement, String table) {
        try {
            statement.executeUpdate("CREATE INDEX message_prefix_index ON " + table + "(message(16))");
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
