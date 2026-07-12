package net.coreprotect.model.item;

public final class InventorySources {
    public static final int BLOCK = 0;
    public static final int CONTAINER = 1;
    public static final int ITEM = 2;
    public static final int ENTITY_CONTAINER = 3;
    public static final int ENTITY_INTERACTION = 4;

    private InventorySources() {
        throw new IllegalStateException("Model class");
    }

    public static String getSourceString(int source) {
        switch (source) {
            case BLOCK:
                return "block";
            case CONTAINER:
            case ENTITY_CONTAINER:
                return "container";
            case ITEM:
                return "item";
            case ENTITY_INTERACTION:
                return "interaction";
            default:
                return "unknown";
        }
    }
}
