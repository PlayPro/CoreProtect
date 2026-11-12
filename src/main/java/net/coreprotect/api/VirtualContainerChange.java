package net.coreprotect.api;

import org.bukkit.inventory.ItemStack;

/**
 * One item change that a rollback or restore applies to a virtual container.
 */
public final class VirtualContainerChange {

    private final ItemStack item;
    private final boolean addition;
    private final int slot;

    public VirtualContainerChange(ItemStack item, boolean addition, int slot) {
        this.item = item.clone();
        this.addition = addition;
        this.slot = slot;
    }

    /**
     * @return A copy of the items to add or remove. Logged changes are merged per item, so the amount can exceed the
     *         maximum stack size.
     */
    public ItemStack getItem() {
        return item.clone();
    }

    /**
     * @return True to add the items to the container, false to remove them. Rollbacks and restores are already
     *         resolved, so a rollback of a removal is an addition.
     */
    public boolean isAddition() {
        return addition;
    }

    /**
     * @return The slot in the container where the logged change happened, or -1 when it is unknown. It is a hint: the
     *         handler decides where the items go.
     */
    public int getSlot() {
        return slot;
    }
}
