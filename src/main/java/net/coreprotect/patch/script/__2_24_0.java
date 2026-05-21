package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ErrorReporter;

public class __2_24_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                try {
                    statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "sign CONVERT TO CHARACTER SET utf8mb4");
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.PATCH_SKIP_UPDATE, ConfigHandler.prefix + "sign", Selector.FIRST, Selector.FIRST));
                    ErrorReporter.report(e);
                    return false;
                }

                if (!updateItemMetadataColumns(statement)) {
                    return false;
                }

                if (!updateSkullSkinColumn(statement)) {
                    return false;
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }

        return true;
    }

    protected static boolean updateItemMetadataColumns(Statement statement) {
        if (Config.getGlobal().MYSQL) {
            return modifyColumn(statement, ConfigHandler.prefix + "container", "ALTER TABLE " + ConfigHandler.prefix + "container MODIFY metadata MEDIUMBLOB") &&
                    modifyColumn(statement, ConfigHandler.prefix + "item", "ALTER TABLE " + ConfigHandler.prefix + "item MODIFY data MEDIUMBLOB");
        }

        return true;
    }

    protected static boolean updateSkullSkinColumn(Statement statement) {
        if (Config.getGlobal().MYSQL) {
            return modifyColumn(statement, ConfigHandler.prefix + "skull", "ALTER TABLE " + ConfigHandler.prefix + "skull MODIFY skin TEXT");
        }

        return true;
    }

    private static boolean modifyColumn(Statement statement, String table, String query) {
        try {
            statement.executeUpdate(query);
            return true;
        }
        catch (Exception e) {
            Chat.console(Phrase.build(Phrase.PATCH_SKIP_UPDATE, table, Selector.FIRST, Selector.FIRST));
            ErrorReporter.report(e);
        }

        return false;
    }

}
