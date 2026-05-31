package net.coreprotect.api;

import net.coreprotect.model.action.LookupActions;

/**
 * CoreProtect action identifiers used by lookup filters.
 */
public enum CoreProtectAction {
    BLOCK_BREAK(LookupActions.BLOCK_BREAK),
    BLOCK_PLACE(LookupActions.BLOCK_PLACE),
    INTERACTION(LookupActions.INTERACTION),
    ENTITY_KILL(LookupActions.ENTITY_KILL),
    CONTAINER(LookupActions.CONTAINER),
    CHAT(LookupActions.CHAT),
    COMMAND(LookupActions.COMMAND),
    SESSION(LookupActions.SESSION),
    USERNAME(LookupActions.USERNAME),
    SIGN(LookupActions.SIGN),
    ITEM(LookupActions.ITEM),
    UNKNOWN(-1);

    private final int id;

    CoreProtectAction(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public String actionString() {
        return LookupActions.getActionString(id);
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
