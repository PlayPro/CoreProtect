package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigFile;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.patch.Patch;
import net.coreprotect.utility.Chat;

public class __2_21_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                try {
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "item ADD COLUMN rolled_back tinyint(1) DEFAULT 0;");
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.PATCH_SKIP_UPDATE, ConfigHandler.prefix + "item", Selector.FIRST, Selector.FIRST));
                }

                if (!Patch.continuePatch()) {
                    return false;
                }

                try {
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "container ADD COLUMN rolled_back_inventory tinyint(1) DEFAULT 0;");
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.PATCH_SKIP_UPDATE, ConfigHandler.prefix + "container", Selector.FIRST, Selector.FIRST));
                }
            }
            else {
                try {
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "item ADD COLUMN rolled_back INTEGER DEFAULT 0;");
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.PATCH_SKIP_UPDATE, ConfigHandler.prefix + "item", Selector.FIRST, Selector.FIRST));
                }

                if (!Patch.continuePatch()) {
                    return false;
                }

                try {
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "container ADD COLUMN rolled_back_inventory INTEGER DEFAULT 0;");
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.PATCH_SKIP_UPDATE, ConfigHandler.prefix + "container", Selector.FIRST, Selector.FIRST));
                }
            }

            if (!Patch.continuePatch()) {
                return false;
            }

            ConfigFile.modifyLine("language.yml", "LOOKUP_VIEW_PAGE: \"To view a page, type \\\"{0}\\\".\"", null);
            ConfigFile.modifyLine("language.yml", "PREVIEW_CONTAINER: \"You can't preview container transactions.\"", null);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
