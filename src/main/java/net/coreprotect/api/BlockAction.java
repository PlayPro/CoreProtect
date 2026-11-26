package net.coreprotect.api;

import net.coreprotect.model.action.LookupActions;

/**
 * Block actions used by typed block lookup filters.
 */
public enum BlockAction {
    BREAK(LookupActions.BLOCK_BREAK),
    PLACE(LookupActions.BLOCK_PLACE),
    INTERACTION(LookupActions.INTERACTION);

    private final int id;

    BlockAction(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
