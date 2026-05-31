package net.coreprotect.model.item;

public final class InventorySources {
    public static final int BLOCK = 0;
    public static final int CONTAINER = 1;
    public static final int ITEM = 2;

    private InventorySources() {
        throw new IllegalStateException("Model class");
    }

    public static String getSourceString(int source) {
        switch (source) {
            case BLOCK:
                return "block";
            case CONTAINER:
                return "container";
            case ITEM:
                return "item";
            default:
                return "unknown";
        }
    }
}
