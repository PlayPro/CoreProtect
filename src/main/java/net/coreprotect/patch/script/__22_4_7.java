package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ErrorReporter;

public class __22_4_7 {

    protected static boolean patch(Statement statement) {
        try {
            modifyRollbackOrderBy(statement, "block");
            modifyRollbackOrderBy(statement, "container");
            modifyRollbackOrderBy(statement, "item");
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static void modifyRollbackOrderBy(Statement statement, String table) throws Exception {
        statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + table + " MODIFY ORDER BY (wid, y, x, z, time, user, type, rowid)");
    }
}
