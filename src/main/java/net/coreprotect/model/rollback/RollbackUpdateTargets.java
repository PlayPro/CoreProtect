package net.coreprotect.model.rollback;

public final class RollbackUpdateTargets {
    public static final int BLOCK = 0;
    public static final int CONTAINER = 1;
    public static final int INVENTORY_ITEM = 2;
    public static final int INVENTORY_CONTAINER = 3;
    public static final int BLOCK_INVENTORY = 4;

    private RollbackUpdateTargets() {
        throw new IllegalStateException("Model class");
    }

    public static boolean usesInventoryRollbackState(int target) {
        return target == INVENTORY_ITEM || target == INVENTORY_CONTAINER || target == BLOCK_INVENTORY;
    }

    public static boolean updatesContainerTable(int target) {
        return target == CONTAINER || target == INVENTORY_CONTAINER;
    }

    public static boolean updatesItemTable(int target) {
        return target == INVENTORY_ITEM;
    }
}
