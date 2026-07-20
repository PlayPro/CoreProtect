package net.coreprotect.model.action;

public final class SignActions {
    public static final int BREAK = LookupActions.BLOCK_BREAK;
    public static final int PLACE = LookupActions.BLOCK_PLACE;
    public static final int EDIT = 2;

    private SignActions() {
        throw new IllegalStateException("Model class");
    }

    public static String getActionString(int action) {
        switch (action) {
            case BREAK:
                return "break";
            case PLACE:
                return "place";
            case EDIT:
                return "edit";
            default:
                return "unknown";
        }
    }
}
