package net.coreprotect.consumer.process;

import java.sql.Statement;
import java.util.List;
import java.util.Map;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Database;
import net.coreprotect.model.rollback.RollbackUpdateTargets;
import net.coreprotect.utility.MaterialUtils;

class RollbackUpdateProcess {

    static void process(Statement statement, int processId, int id, int action, int table) {
        Map<Integer, List<Object[]>> updateLists = Consumer.consumerObjectArrayList.get(processId);
        if (updateLists.get(id) != null) {
            List<Object[]> list = updateLists.get(id);
            for (Object[] listRow : list) {
                long rowid = (Long) listRow[0];
                int rolledBack = (Integer) listRow[9];
                if (MaterialUtils.rolledBack(rolledBack, RollbackUpdateTargets.usesInventoryRollbackState(table)) == action) { // 1 = restore, 0 = rollback
                    Database.performUpdate(statement, rowid, rolledBack, table);
                }
            }
            updateLists.remove(id);
        }
    }
}
