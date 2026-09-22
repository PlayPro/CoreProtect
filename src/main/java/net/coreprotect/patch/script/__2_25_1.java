package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ErrorReporter;

public class __2_25_1 {

    protected static boolean patchClickHouse(Statement statement) {
        return true;
    }

    protected static boolean patchDuckDB(Statement statement) {
        return true;
    }

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "sign MODIFY line_1 TEXT, MODIFY line_2 TEXT, MODIFY line_3 TEXT, MODIFY line_4 TEXT, MODIFY line_5 TEXT, MODIFY line_6 TEXT, MODIFY line_7 TEXT, MODIFY line_8 TEXT");
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

}
