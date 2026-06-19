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
        if (event.isCancelled() || !isTrackedPlacementEntity(event.getEntityType())) {
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
            Material placedMaterial = ((ItemStack) data[3]).getType();
            if ((data[1].equals(key) || data[2].equals(key)) && matchesPlacedEntityMaterial(event.getEntityType(), placedMaterial)) {
                Block blockLocation = placedMaterial == Material.ARMOR_STAND ? BlockUtil.gravityScan(location, Material.ARMOR_STAND, name) : location.getBlock();
                int forceData = placedMaterial == Material.ARMOR_STAND ? (int) event.getEntity().getLocation().getYaw() : -1;
                Queue.queueBlockPlace(name, blockLocation.getState(), location.getBlock().getType(), location.getBlock().getState(), placedMaterial, forceData, 1, null);
                it.remove();
            }
        }
    }

    private static boolean isTrackedPlacementEntity(EntityType type) {
        return type == EntityType.ARMOR_STAND || EntityUtils.isSulfurCube(type);
    }

    private static boolean matchesPlacedEntityMaterial(EntityType type, Material material) {
        Material entityMaterial = EntityUtils.getEntityMaterial(type);
        return entityMaterial == material || (EntityUtils.isSulfurCube(type) && EntityUtils.isSulfurCubePlacementMaterial(material));
    }

}
