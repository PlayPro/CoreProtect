package net.coreprotect.consumer.process;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Database;
import net.coreprotect.model.rollback.RollbackUpdateTargets;
import net.coreprotect.utility.MaterialUtils;

class RollbackUpdateProcess {

    static void process(Statement statement, int processId, int id, int action, int table) {
        Map<Integer, List<Object[]>> updateLists = Consumer.consumerObjectArrayList.get(processId);
        if (updateLists.get(id) != null) {
            List<Object[]> list = updateLists.get(id);
            boolean inventoryState = RollbackUpdateTargets.usesInventoryRollbackState(table);
            Map<Integer, List<Long>> rowIdsByValue = new HashMap<>();
            for (Object[] listRow : list) {
                long rowid = (Long) listRow[0];
                int rolledBack = (Integer) listRow[9];
                if (MaterialUtils.rolledBack(rolledBack, inventoryState) == action) { // 1 = restore, 0 = rollback
                    int toggledValue = MaterialUtils.toggleRolledBack(rolledBack, inventoryState);
                    rowIdsByValue.computeIfAbsent(toggledValue, key -> new ArrayList<>()).add(rowid);
                }
            }
            for (Entry<Integer, List<Long>> entry : rowIdsByValue.entrySet()) {
                Database.performRolledBackUpdate(statement, entry.getKey(), entry.getValue(), table);
            }
            updateLists.remove(id);
        }
    }
}
