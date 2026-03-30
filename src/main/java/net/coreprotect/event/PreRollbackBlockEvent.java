package net.coreprotect.event;

import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

/**
 * This is called BEFORE it checks if the block is already rolled back so should be skipped. It is called for every potential rollback that can occur on a block
 */
public class PreRollbackBlockEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final Block block;
    private final int rollbackType;
    private final List<Object> meta;

    public PreRollbackBlockEvent(Block block, int rollbackType, List<Object> meta) {
        this.block = block;
        this.rollbackType = rollbackType;
        this.meta = meta;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public Block getBlock() {
        return block;
    }

    public int getRollbackType() {
        return rollbackType;
    }

    public List<Object> getMeta() {
        return meta;
    }
}
