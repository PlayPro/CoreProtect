package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;

public class __2_23_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                try {
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "skull MODIFY owner VARCHAR(255);");
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.PATCH_SKIP_UPDATE, ConfigHandler.prefix + "skull", Selector.FIRST, Selector.FIRST));
                }
            }

            __2_23_1.patch(statement);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
