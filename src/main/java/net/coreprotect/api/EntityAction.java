package net.coreprotect.api;

import net.coreprotect.model.action.LookupActions;

/**
 * Entity actions used by typed entity lookup filters.
 */
public enum EntityAction {
    KILL(LookupActions.ENTITY_KILL),
    SPAWN(LookupActions.ENTITY_SPAWN);

    private final int id;

    EntityAction(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
