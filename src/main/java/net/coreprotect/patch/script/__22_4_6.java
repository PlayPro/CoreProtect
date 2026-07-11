package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ErrorReporter;

public class __22_4_6 {

    protected static boolean patch(Statement statement) {
        try {
            modifyColumn(statement, "block", "meta");
            modifyColumn(statement, "container", "metadata");
            modifyColumn(statement, "item", "data");
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static void modifyColumn(Statement statement, String table, String column) throws Exception {
        statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + table + " MODIFY COLUMN " + column + " String");
    }
}
