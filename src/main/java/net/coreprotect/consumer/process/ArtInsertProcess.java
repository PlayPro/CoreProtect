package net.coreprotect.consumer.process;

import java.sql.Statement;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.MaterialStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;

class ArtInsertProcess {

    static void process(ConsumerWriteBatch preparedStmt, Statement statement, int batchCount, Object name, int materialId) {
        if (name instanceof String) {
            String query = "SELECT id FROM " + ConfigHandler.prefix + "art_map WHERE id = " + materialId + " LIMIT 1 OFFSET 0";
            boolean hasMaterial = !ConfigHandler.databaseType.isClickHouse() && MaterialStatement.hasMaterial(statement, query);
            if (!hasMaterial) {
                MaterialStatement.insert(preparedStmt, ConsumerWriteBatch.ReferenceKind.ART, batchCount, materialId, (String) name);

                // validate ID maps to ensure mapping wasn't reloaded from database prior to this insertion completing
                ConfigHandler.art.put((String) name, materialId);
                ConfigHandler.artReversed.put(materialId, (String) name);
                if (materialId > ConfigHandler.artId) {
                    ConfigHandler.artId = materialId;
                }
            }
            else {
                Chat.console(Phrase.build(Phrase.CACHE_ERROR, "art"));
                Chat.console(Phrase.build(Phrase.CACHE_RELOAD, Selector.FIRST));
                ConfigHandler.loadTypes(statement);
            }
        }
    }
}
