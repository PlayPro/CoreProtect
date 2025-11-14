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

    public static boolean useCampfireStartEvent = true;

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
        if (data != null && data[1].equals(event.getSource())) {
            long newTime = System.currentTimeMillis();
            long oldTime = (long) data[0];
            if ((newTime - oldTime) < 20) { // 50ms = 1 tick
                user = (String) data[2];
            }
            CacheHandler.interactCache.remove(coordinates);
        }

        if (user.equals("#entity")) {
            return;
        }

        ItemStack itemStack = event.getSource().clone();
        itemStack.setAmount(1);
        PlayerDropItemListener.playerDropItem(event.getBlock().getLocation(), user, itemStack);
    }

}
