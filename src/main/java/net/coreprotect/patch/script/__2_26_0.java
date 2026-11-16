package net.coreprotect.patch.script;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.StringUtils;

public class __2_26_0 {

    protected static boolean patchClickHouse(Statement statement) {
        return true;
    }

    protected static boolean patchDuckDB(Statement statement) {
        return true;
    }

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                widenMySQLUsernameColumn(statement, ConfigHandler.prefix + "user");
                widenMySQLUsernameColumn(statement, ConfigHandler.prefix + "username_log");
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static void widenMySQLUsernameColumn(Statement statement, String table) throws SQLException {
        String type;
        try (ResultSet columns = statement.executeQuery("SHOW COLUMNS FROM " + table + " LIKE 'user'")) {
            if (!columns.next()) {
                throw new SQLException("Missing MySQL column " + table + ".user");
            }
            type = columns.getString("Type");
        }
        if (hasMySQLUsernameCapacity(table, type)) {
            return;
        }
        statement.executeUpdate("ALTER TABLE " + table + " MODIFY user VARCHAR(" + StringUtils.MAX_USERNAME_LENGTH + ")");
    }

    private static boolean hasMySQLUsernameCapacity(String table, String type) throws SQLException {
        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (normalizedType.equals("text") || normalizedType.equals("mediumtext") || normalizedType.equals("longtext")) {
            return true;
        }
        if (normalizedType.equals("tinytext")) {
            return false;
        }
        int openParen = normalizedType.indexOf('(');
        int closeParen = normalizedType.indexOf(')', openParen + 1);
        if ((normalizedType.startsWith("varchar(") || normalizedType.startsWith("char("))
                && closeParen > openParen + 1 && closeParen == normalizedType.length() - 1) {
            int length;
            try {
                length = Integer.parseInt(normalizedType.substring(openParen + 1, closeParen));
            }
            catch (NumberFormatException exception) {
                throw new SQLException("Unsupported MySQL " + table + ".user type: " + type, exception);
            }
            if (length <= 0) {
                throw new SQLException("Unsupported MySQL " + table + ".user type: " + type);
            }
            return length >= StringUtils.MAX_USERNAME_LENGTH;
        }
        throw new SQLException("Unsupported MySQL " + table + ".user type: " + type);
    }
}
