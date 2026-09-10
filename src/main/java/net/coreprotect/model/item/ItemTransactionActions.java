package net.coreprotect.model.item;

public final class ItemTransactionActions {
    public static final int REMOVE = 0;
    public static final int ADD = 1;
    public static final int DROP = 2;
    public static final int PICKUP = 3;
    public static final int REMOVE_ENDER = 4;
    public static final int ADD_ENDER = 5;
    public static final int THROW = 6;
    public static final int SHOOT = 7;
    public static final int BREAK = 8;
    public static final int DESTROY = 9;
    public static final int CREATE = 10;
    public static final int SELL = 11;
    public static final int BUY = 12;

    private ItemTransactionActions() {
        throw new IllegalStateException("Internal class");
    }

    public static String getActionString(int action) {
        switch (action) {
            case REMOVE:
                return "remove";
            case ADD:
                return "add";
            case DROP:
                return "drop";
            case PICKUP:
                return "pickup";
            case REMOVE_ENDER:
                return "withdraw";
            case ADD_ENDER:
                return "deposit";
            case THROW:
                return "throw";
            case SHOOT:
                return "shoot";
            case BREAK:
                return "break";
            case DESTROY:
                return "destroy";
            case CREATE:
                return "create";
            case SELL:
                return "sell";
            case BUY:
                return "buy";
            default:
                return "unknown";
        }
    }

    public static int getInventoryActionId(int action) {
        switch (action) {
            case REMOVE:
            case PICKUP:
            case REMOVE_ENDER:
            case CREATE:
            case BUY:
                return ADD;
            default:
                return REMOVE;
        }
    }
}
