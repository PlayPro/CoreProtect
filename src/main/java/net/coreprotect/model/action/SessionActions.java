package net.coreprotect.model.action;

public final class SessionActions {
    public static final int LOGOUT = 0;
    public static final int LOGIN = 1;

    private SessionActions() {
        throw new IllegalStateException("Model class");
    }

    public static String getActionString(int action) {
        switch (action) {
            case LOGOUT:
                return "logout";
            case LOGIN:
                return "login";
            default:
                return "unknown";
        }
    }
}
