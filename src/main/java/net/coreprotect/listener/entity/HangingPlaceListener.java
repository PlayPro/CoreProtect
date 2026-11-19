package net.coreprotect.listener.entity;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingPlaceEvent;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.utility.MaterialUtils;

public final class HangingPlaceListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    protected void onInspectHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && (event.getEntity() instanceof ItemFrame || event.getEntity() instanceof Painting)
                && Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onHangingPlace(HangingPlaceEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        Entity entity = event.getEntity();
        Player player = event.getPlayer();

        if (entity instanceof ItemFrame || entity instanceof Painting) {
            Block blockEvent = event.getEntity().getLocation().getBlock();
            String blockData = null;
            Material material;
            int artId;

            if (entity instanceof ItemFrame) {
                material = BukkitAdapter.ADAPTER.getFrameType(entity);
                ItemFrame itemFrame = (ItemFrame) entity;
                blockData = "FACING=" + itemFrame.getFacing().name();
                artId = 0;
            }
            else {
                material = Material.PAINTING;
                Painting painting = (Painting) entity;
                blockData = "FACING=" + painting.getFacing().name();
                try {
                    artId = MaterialUtils.getArtId(MaterialUtils.getPaintingArtName(painting), true);
                }
                catch (IncompatibleClassChangeError e) {
                    artId = 0;
                    // 1.21.2+
                }
            }

            if (Config.getConfig(blockEvent.getWorld()).BLOCK_PLACE) {
                Queue.queueBlockPlace(player.getName(), blockEvent.getState(), blockEvent.getType(), null, material, artId, 1, blockData);
            }
        }
    }
}
