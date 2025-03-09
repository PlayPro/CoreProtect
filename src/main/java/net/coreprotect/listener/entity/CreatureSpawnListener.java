package net.coreprotect.listener.entity;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.block.BlockUtil;
import net.coreprotect.utility.EntityUtils;

public final class CreatureSpawnListener extends Queue implements Listener {

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled() || !event.getEntityType().equals(EntityType.ARMOR_STAND)) {
            return;
        }

        World world = event.getEntity().getWorld();
        if (!Config.getConfig(world).BLOCK_PLACE) {
            return;
        }

        Location location = event.getEntity().getLocation();
        String key = world.getName() + "-" + location.getBlockX() + "-" + location.getBlockY() + "-" + location.getBlockZ();
        Iterator<Entry<String, Object[]>> it = ConfigHandler.entityBlockMapper.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object[]> pair = it.next();
            String name = pair.getKey();
            Object[] data = pair.getValue();
            if ((data[1].equals(key) || data[2].equals(key)) && EntityUtils.getEntityMaterial(event.getEntityType()) == ((ItemStack) data[3]).getType()) {
                Block gravityLocation = BlockUtil.gravityScan(location, Material.ARMOR_STAND, name);
                Queue.queueBlockPlace(name, gravityLocation.getState(), location.getBlock().getType(), location.getBlock().getState(), ((ItemStack) data[3]).getType(), (int) event.getEntity().getLocation().getYaw(), 1, null);
                it.remove();
            }
        }
    }

}
