package net.coreprotect.api;

/**
 * CoreProtect action identifiers used by lookup filters.
 */
public enum CoreProtectAction {
    BLOCK_BREAK(0, "break"),
    BLOCK_PLACE(1, "place"),
    INTERACTION(2, "click"),
    ENTITY_KILL(3, "kill"),
    CONTAINER(4, "container"),
    CHAT(6, "chat"),
    COMMAND(7, "command"),
    SESSION(8, "session"),
    USERNAME(9, "username"),
    SIGN(10, "sign"),
    ITEM(11, "item"),
    UNKNOWN(-1, "unknown");

    private final int id;
    private final String actionString;

    CoreProtectAction(int id, String actionString) {
        this.id = id;
        this.actionString = actionString;
    }

    public int id() {
        return id;
    }

    public String actionString() {
        return actionString;
    }

    public static CoreProtectAction fromId(int id) {
        for (CoreProtectAction action : values()) {
            if (action.id == id) {
                return action;
            }
        }

        return UNKNOWN;
    }
}
