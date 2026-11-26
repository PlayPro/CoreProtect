package net.coreprotect.listener.entity;

import org.bukkit.block.Block;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Lookup;
import net.coreprotect.utility.entity.HangingUtil;

public final class HangingBreakListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent || event.isCancelled()) {
            return;
        }
        Hanging entity = event.getEntity();
        RemoveCause cause = event.getCause();
        if (!HangingUtil.isSupportedDecoration(entity) || (cause != RemoveCause.EXPLOSION && cause != RemoveCause.PHYSICS && cause != RemoveCause.OBSTRUCTION)) {
            return;
        }

        Block block = entity.getLocation().getBlock();
        Block attachedBlock = null;
        String user = "#explosion";
        boolean logDrops = false;
        if (cause != RemoveCause.EXPLOSION) {
            attachedBlock = block.getRelative(entity.getAttachedFace());
            String removed = Lookup.whoRemovedCache(attachedBlock.getState());
            if (!removed.isEmpty()) {
                user = removed;
                logDrops = true;
            }
            else {
                user = cause == RemoveCause.PHYSICS ? "#physics" : "#obstruction";
            }
        }

        if (entity instanceof ItemFrame && Config.getConfig(entity.getWorld()).ITEM_TRANSACTIONS) {
            HangingUtil.logFrameItemRemoval((ItemFrame) entity, user, logDrops);
        }
        if (Config.getConfig(block.getWorld()).NATURAL_BREAK) {
            Queue.queueNaturalBlockBreak(user, block.getState(), attachedBlock, HangingUtil.getMaterial(entity), HangingUtil.getFacingData(entity), HangingUtil.getArtId(entity));
        }
    }
}
