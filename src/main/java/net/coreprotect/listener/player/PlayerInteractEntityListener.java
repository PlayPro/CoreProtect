package net.coreprotect.listener.player;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.utility.Util;

public final class PlayerInteractEntityListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerArmorStandManipulateEvent) {
            return;
        }

        Player player = event.getPlayer();
        final Entity entity = event.getRightClicked(); // change item in ItemFrame, etc
        World world = entity.getWorld();

        if (entity instanceof ItemFrame && !event.isCancelled() && Config.getConfig(world).BLOCK_PLACE) {
            ItemFrame frame = (ItemFrame) entity;
            if (frame.getItem().getType().equals(Material.AIR) && !player.getInventory().getItemInMainHand().getType().equals(Material.AIR)) {
                Material material = BukkitAdapter.ADAPTER.getFrameType(entity);
                int hand = Util.getBlockId(player.getInventory().getItemInMainHand().getType());
                int data = 0;

                if (frame.getItem() != null) {
                    data = Util.getBlockId(frame.getItem().getType());
                }

                String playerName = player.getName();
                Block block = frame.getLocation().getBlock();
                Queue.queueBlockBreak(playerName, block.getState(), material, null, data);
                Queue.queueBlockPlace(playerName, block.getState(), block.getType(), null, material, hand, 1, null);
            }
        }
    }
}
