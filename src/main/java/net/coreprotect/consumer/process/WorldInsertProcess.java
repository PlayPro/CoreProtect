package net.coreprotect.consumer.process;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.MaterialStatement;
import net.coreprotect.database.statement.WorldStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;

import java.sql.PreparedStatement;
import java.sql.Statement;

class WorldInsertProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, Statement statement, Object world, int worldId) {
        if (world instanceof String) {
            String query = "SELECT id FROM " + ConfigHandler.prefix + "world WHERE id = '" + worldId + "' LIMIT 0, 1";
            boolean hasMaterial = MaterialStatement.hasMaterial(statement, query);
            if (!hasMaterial) {
                WorldStatement.insert(preparedStmt, batchCount, worldId, (String) world);

                // validate ID maps to ensure mapping wasn't reloaded from database prior to this insertion completing
                ConfigHandler.worlds.put((String) world, worldId);
                ConfigHandler.worldsReversed.put(worldId, (String) world);
                if (worldId > ConfigHandler.worldId) {
                    ConfigHandler.worldId = worldId;
                }
            }
            else {
                Chat.console(Phrase.build(Phrase.CACHE_ERROR, "world"));
                Chat.console(Phrase.build(Phrase.CACHE_RELOAD, Selector.SECOND));
                ConfigHandler.loadWorlds(statement);
            }
        }
    }
}
