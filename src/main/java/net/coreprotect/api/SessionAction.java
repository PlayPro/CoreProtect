package net.coreprotect.api;

import net.coreprotect.model.action.SessionActions;

/**
 * Session actions used by typed session lookup filters.
 */
public enum SessionAction {
    LOGOUT(SessionActions.LOGOUT),
    LOGIN(SessionActions.LOGIN);

    private final int id;

    SessionAction(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
