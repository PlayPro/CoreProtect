package net.coreprotect.api;

import net.coreprotect.model.item.ItemTransactionActions;

/**
 * Item transaction actions used by typed item lookup filters.
 */
public enum ItemAction {
    DROP(ItemTransactionActions.DROP),
    PICKUP(ItemTransactionActions.PICKUP),
    REMOVE_ENDER(ItemTransactionActions.REMOVE_ENDER),
    ADD_ENDER(ItemTransactionActions.ADD_ENDER),
    THROW(ItemTransactionActions.THROW),
    SHOOT(ItemTransactionActions.SHOOT),
    BREAK(ItemTransactionActions.BREAK),
    DESTROY(ItemTransactionActions.DESTROY),
    CREATE(ItemTransactionActions.CREATE),
    SELL(ItemTransactionActions.SELL),
    BUY(ItemTransactionActions.BUY);

    private final int id;

    ItemAction(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
