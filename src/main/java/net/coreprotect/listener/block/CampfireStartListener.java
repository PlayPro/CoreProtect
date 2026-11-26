package net.coreprotect.listener.block;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CampfireStartEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.PlayerDropItemListener;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.WorldUtils;

public final class CampfireStartListener extends Queue implements Listener {

    public static volatile boolean useCampfireStartEvent = true;

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onCampfireStart(CampfireStartEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        int worldId = WorldUtils.getWorldId(location.getWorld().getName());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        String coordinates = x + "." + y + "." + z + "." + worldId + "." + block.getType().name();
        String user = "#entity";

        Object[] data = CacheHandler.interactCache.get(coordinates);
        ItemStack source = event.getSource();
        if (data != null && data[1] instanceof ItemStack && ((ItemStack) data[1]).isSimilar(source)
                && CacheHandler.interactCache.remove(coordinates, data)) {
            long elapsed = System.currentTimeMillis() - (long) data[0];
            if (elapsed >= 0 && elapsed <= 50) {
                user = (String) data[2];
            }
        }

        if (user.equals("#entity")) {
            return;
        }

        ItemStack itemStack = source.clone();
        itemStack.setAmount(1);
        PlayerDropItemListener.playerDropItem(event.getBlock().getLocation(), user, itemStack);
    }

}
