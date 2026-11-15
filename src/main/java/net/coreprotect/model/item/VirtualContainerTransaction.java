package net.coreprotect.model.item;

import org.bukkit.inventory.ItemStack;

import net.coreprotect.utility.ItemUtils;

public final class VirtualContainerTransaction {

    private static final ItemStack[] EMPTY_CONTENTS = new ItemStack[0];

    private final String key;
    private final int time;
    private final ItemStack[] oldContents;
    private final ItemStack[] newContents;

    public VirtualContainerTransaction(String key, ItemStack[] oldContents, ItemStack[] newContents) {
        this.key = key;
        this.time = (int) (System.currentTimeMillis() / 1000L);
        this.oldContents = oldContents == null ? EMPTY_CONTENTS : ItemUtils.getContainerState(oldContents);
        this.newContents = newContents == null ? EMPTY_CONTENTS : ItemUtils.getContainerState(newContents);
    }

    public String getKey() {
        return key;
    }

    public int getTime() {
        return time;
    }

    public ItemStack[] getOldContents() {
        return ItemUtils.getContainerState(oldContents);
    }

    public ItemStack[] getNewContents() {
        return ItemUtils.getContainerState(newContents);
    }
}
