package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.sql.Statement;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.MaterialStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;

class MaterialInsertProcess {

    static void process(PreparedStatement preparedStmt, Statement statement, int batchCount, Object name, int materialId) {
        if (name instanceof String) {
            String query = "SELECT id FROM " + ConfigHandler.prefix + "material_map WHERE id = '" + materialId + "' OFFSET 0 LIMIT 1";
            boolean hasMaterial = MaterialStatement.hasMaterial(statement, query);
            if (!hasMaterial) {
                MaterialStatement.insert(preparedStmt, batchCount, materialId, (String) name);

                // validate ID maps to ensure mapping wasn't reloaded from database prior to this insertion completing
                ConfigHandler.materials.put((String) name, materialId);
                ConfigHandler.materialsReversed.put(materialId, (String) name);
                if (materialId > ConfigHandler.materialId) {
                    ConfigHandler.materialId = materialId;
                }
            }
            else {
                Chat.console(Phrase.build(Phrase.CACHE_ERROR, "material"));
                Chat.console(Phrase.build(Phrase.CACHE_RELOAD, Selector.FIRST));
                ConfigHandler.loadTypes(statement);
            }
        }
    }
}
