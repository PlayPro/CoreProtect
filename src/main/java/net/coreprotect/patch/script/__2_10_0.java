package net.coreprotect.patch.script;

import java.sql.Statement;
import java.util.Locale;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;

public class __2_10_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (!Config.getGlobal().TYPE_DATABASE.toLowerCase(Locale.ROOT).equals("sqlite")) {
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "user ADD COLUMN uuid varchar(64), ADD INDEX(uuid)");
            }
            else {
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "user ADD COLUMN uuid TEXT;");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS uuid_index ON " + ConfigHandler.prefix + "user(uuid);");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
