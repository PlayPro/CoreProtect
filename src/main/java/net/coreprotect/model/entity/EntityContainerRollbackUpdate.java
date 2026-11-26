package net.coreprotect.model.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EntityContainerRollbackUpdate {

    private final String user;
    private final EntitySpawnData transition;
    private final List<Object[]> rows;
    private final int rollbackType;
    private final boolean inventoryRollback;

    public EntityContainerRollbackUpdate(String user, EntitySpawnData transition, List<Object[]> rows, int rollbackType, boolean inventoryRollback) {
        if (user == null || user.isEmpty() || transition == null || rollbackType < 0 || rollbackType > 1) {
            throw new IllegalArgumentException("Invalid entity container rollback update");
        }
        this.user = user;
        this.transition = transition;
        this.rows = copyRows(rows);
        this.rollbackType = rollbackType;
        this.inventoryRollback = inventoryRollback;
    }

    public String getUser() {
        return user;
    }

    public EntitySpawnData getTransition() {
        return transition;
    }

    public List<Object[]> getRows() {
        return copyRows(rows);
    }

    public int getRollbackType() {
        return rollbackType;
    }

    public boolean isInventoryRollback() {
        return inventoryRollback;
    }

    private static List<Object[]> copyRows(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object[]> copy = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            if (row != null && row.length > 9 && row[0] instanceof Long && row[9] instanceof Integer) {
                Object[] rowCopy = new Object[10];
                rowCopy[0] = row[0];
                rowCopy[9] = row[9];
                copy.add(rowCopy);
            }
        }
        return Collections.unmodifiableList(copy);
    }
}
