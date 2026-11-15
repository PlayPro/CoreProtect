package net.coreprotect.consumer.process;

import java.sql.ResultSet;
import java.sql.Statement;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.MaterialStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;

class VirtualContainerInsertProcess {

    static void process(ConsumerWriteBatch preparedStmt, Statement statement, int batchCount, Object key, int containerId) {
        if (key instanceof String) {
            String storedKey = ConfigHandler.databaseType.isClickHouse() ? null : getStoredKey(statement, containerId);
            if (storedKey == null) {
                MaterialStatement.insert(preparedStmt, ConsumerWriteBatch.ReferenceKind.VIRTUAL_CONTAINER, batchCount, containerId, (String) key);

                // validate ID maps to ensure mapping wasn't reloaded from database prior to this insertion completing
                ConfigHandler.virtualContainers.put((String) key, containerId);
                ConfigHandler.virtualContainersReversed.put(containerId, (String) key);
                if (containerId > ConfigHandler.virtualContainerId) {
                    ConfigHandler.virtualContainerId = containerId;
                }
            }
            else if (!storedKey.equals(key)) {
                // another server sharing the database took this id: forget it, so the key is resolved again above the stored ids
                Chat.console(Phrase.build(Phrase.CACHE_ERROR, "virtual container"));
                ConfigHandler.uncacheIdentifier(ConsumerWriteBatch.ReferenceKind.VIRTUAL_CONTAINER, containerId, (String) key);
                ConfigHandler.loadVirtualContainerId(statement);
            }
        }
    }

    private static String getStoredKey(Statement statement, int containerId) {
        String query = "SELECT container FROM " + ConfigHandler.prefix + "virtual_container_map WHERE id = " + containerId + " LIMIT 1 OFFSET 0";
        try (ResultSet resultSet = statement.executeQuery(query)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
            return null;
        }
    }
}
