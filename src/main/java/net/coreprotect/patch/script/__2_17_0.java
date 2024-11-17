package net.coreprotect.patch.script;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;

import java.sql.Statement;

public class __2_17_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "sign ADD COLUMN color int");
            }
            else {
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "sign ADD COLUMN color INTEGER");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
