package net.coreprotect.patch.script;

import java.sql.SQLException;
import java.sql.Statement;

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
        String type = MySqlColumnPatch.type(statement, table, "user");
        if (type.equals("tinytext")) {
            MySqlColumnPatch.widen(statement, table, "user", type, "TEXT");
        }
        else if (!hasMySQLUsernameCapacity(table, type)) {
            MySqlColumnPatch.widen(statement, table, "user", type, "VARCHAR(" + StringUtils.MAX_USERNAME_LENGTH + ")");
        }
    }

    private static boolean hasMySQLUsernameCapacity(String table, String type) throws SQLException {
        if (type.equals("text") || type.equals("mediumtext") || type.equals("longtext")) {
            return true;
        }
        int openParen = type.indexOf('(');
        int closeParen = type.indexOf(')', openParen + 1);
        if ((type.startsWith("varchar(") || type.startsWith("char(")) && closeParen > openParen + 1 && closeParen == type.length() - 1) {
            int length;
            try {
                length = Integer.parseInt(type.substring(openParen + 1, closeParen));
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
