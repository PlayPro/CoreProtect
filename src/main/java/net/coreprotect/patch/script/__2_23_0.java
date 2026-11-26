package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ErrorReporter;

public class __2_23_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (ConfigHandler.databaseType.isMySQL()) {
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "skull MODIFY owner VARCHAR(255)");
            }
            return __2_23_1.patch(statement);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

}
