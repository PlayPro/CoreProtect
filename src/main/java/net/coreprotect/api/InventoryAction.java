package net.coreprotect.api;

import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.item.InventorySources;
import net.coreprotect.model.item.ItemTransactionActions;

/**
 * Source-specific transaction actions used by typed inventory lookup filters.
 * Container actions include tracked entity-container transactions.
 */
public enum InventoryAction {
    BLOCK_PLACE(InventorySources.BLOCK, LookupActions.BLOCK_PLACE),
    CONTAINER_REMOVE(InventorySources.CONTAINER, ItemTransactionActions.REMOVE),
    CONTAINER_ADD(InventorySources.CONTAINER, ItemTransactionActions.ADD),
    ITEM_DROP(InventorySources.ITEM, ItemTransactionActions.DROP),
    ITEM_PICKUP(InventorySources.ITEM, ItemTransactionActions.PICKUP),
    ITEM_REMOVE_ENDER(InventorySources.ITEM, ItemTransactionActions.REMOVE_ENDER),
    ITEM_ADD_ENDER(InventorySources.ITEM, ItemTransactionActions.ADD_ENDER),
    ITEM_THROW(InventorySources.ITEM, ItemTransactionActions.THROW),
    ITEM_SHOOT(InventorySources.ITEM, ItemTransactionActions.SHOOT),
    ITEM_BREAK(InventorySources.ITEM, ItemTransactionActions.BREAK),
    ITEM_DESTROY(InventorySources.ITEM, ItemTransactionActions.DESTROY),
    ITEM_CREATE(InventorySources.ITEM, ItemTransactionActions.CREATE),
    ITEM_SELL(InventorySources.ITEM, ItemTransactionActions.SELL),
    ITEM_BUY(InventorySources.ITEM, ItemTransactionActions.BUY);

    private final int sourceId;
    private final int id;

    InventoryAction(int sourceId, int id) {
        this.sourceId = sourceId;
        this.id = id;
    }

    public int sourceId() {
        return sourceId;
    }

    public int id() {
        return id;
    }
}
