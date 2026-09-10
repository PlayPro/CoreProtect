package net.coreprotect.model;

import org.bukkit.block.data.BlockData;

public final class PendingBlockChange {
    private final BlockData blockData;
    private final boolean applyPhysics;

    public PendingBlockChange(BlockData blockData, boolean applyPhysics) {
        this.blockData = blockData;
        this.applyPhysics = applyPhysics;
    }

    public BlockData blockData() {
        return blockData;
    }

    public boolean applyPhysics() {
        return applyPhysics;
    }
}
