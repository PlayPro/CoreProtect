package net.coreprotect.consumer.process;

import java.sql.Statement;
import java.util.List;
import java.util.Map;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Database;

class RollbackUpdateProcess {

    static void process(Statement statement, int processId, int id, int action, int table) {
        Map<Integer, List<Object[]>> updateLists = Consumer.consumerObjectArrayList.get(processId);
        if (updateLists.get(id) != null) {
            List<Object[]> list = updateLists.get(id);
            for (Object[] listRow : list) {
                long rowid = (Long) listRow[0];
                int rolledBack = (Integer) listRow[9];
                if (rolledBack == action) {
                    Database.performUpdate(statement, rowid, action, table);
                }
            }
            updateLists.remove(id);
        }
    }
}
