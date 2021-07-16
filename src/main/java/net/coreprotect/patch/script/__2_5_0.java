package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.patch.Patch;

public class __2_5_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                try {
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "sign MODIFY line_1 VARCHAR(100)");
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "sign MODIFY line_2 VARCHAR(100)");
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "sign MODIFY line_3 VARCHAR(100)");
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "sign MODIFY line_4 VARCHAR(100)");
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "user MODIFY user VARCHAR(32)");
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

                if (!Patch.continuePatch()) {
                    return false;
                }
            }

            statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "block ADD COLUMN meta BLOB");
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
