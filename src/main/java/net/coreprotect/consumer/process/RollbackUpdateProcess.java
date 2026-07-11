package net.coreprotect.consumer.process;

import java.sql.SQLException;
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
        process(statement, processId, id, action, table, RollbackUpdateTargets.usesInventoryRollbackState(table));
    }

    static void process(Statement statement, int processId, int id, int action, int table, boolean inventoryRollback) {
        Map<Integer, List<Object[]>> updateLists = Consumer.consumerObjectArrayList.get(processId);
        if (updateLists.get(id) != null) {
            List<Object[]> list = updateLists.get(id);
            process(statement, list, action, table, inventoryRollback);
            updateLists.remove(id);
        }
    }

    static void process(Statement statement, List<Object[]> list, int action, int table, boolean inventoryRollback) {
        Map<Integer, List<Long>> rowIdsByValue = groupRowIdsByValue(list, action, inventoryRollback);
        for (Entry<Integer, List<Long>> entry : rowIdsByValue.entrySet()) {
            Database.performRolledBackUpdate(statement, entry.getKey(), entry.getValue(), table);
        }
    }

    static void processChecked(Statement statement, List<Object[]> list, int action, int table, boolean inventoryRollback) throws SQLException {
        Map<Integer, List<Long>> rowIdsByValue = groupRowIdsByValue(list, action, inventoryRollback);
        for (Entry<Integer, List<Long>> entry : rowIdsByValue.entrySet()) {
            Database.performRolledBackUpdateChecked(statement, entry.getKey(), entry.getValue(), table);
        }
    }

    private static Map<Integer, List<Long>> groupRowIdsByValue(List<Object[]> list, int action, boolean inventoryRollback) {
        Map<Integer, List<Long>> rowIdsByValue = new HashMap<>();
        for (Object[] listRow : list) {
            long rowid = (Long) listRow[0];
            int rolledBack = (Integer) listRow[9];
            if (MaterialUtils.rolledBack(rolledBack, inventoryRollback) == action) { // 1 = restore, 0 = rollback
                int toggledValue = MaterialUtils.toggleRolledBack(rolledBack, inventoryRollback);
                rowIdsByValue.computeIfAbsent(toggledValue, key -> new ArrayList<>()).add(rowid);
            }
        }
        return rowIdsByValue;
    }
}
