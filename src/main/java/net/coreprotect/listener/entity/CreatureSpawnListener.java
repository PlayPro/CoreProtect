package net.coreprotect.listener.entity;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.block.BlockUtil;
import net.coreprotect.utility.Util;

public final class CreatureSpawnListener extends Queue implements Listener {

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (event.getEntityType().equals(EntityType.ARMOR_STAND)) {
            World world = event.getEntity().getWorld();
            Location location = event.getEntity().getLocation();
            String key = world.getName() + "-" + location.getBlockX() + "-" + location.getBlockY() + "-" + location.getBlockZ();
            Iterator<Entry<UUID, Object[]>> it = ConfigHandler.entityBlockMapper.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Object[]> pair = it.next();
                UUID uuid = pair.getKey();
                Object[] data = pair.getValue();
                if ((data[0].equals(key) || data[1].equals(key)) && Util.getEntityMaterial(event.getEntityType()).equals(data[2])) {
                    Player player = Bukkit.getServer().getPlayer(uuid);
                    Block gravityLocation = BlockUtil.gravityScan(location, Material.ARMOR_STAND, player.getName());
                    Queue.queueBlockPlace(player.getName(), gravityLocation.getState(), location.getBlock().getType(), location.getBlock().getState(), (Material) data[2], (int) event.getEntity().getLocation().getYaw(), 1, null);
                    it.remove();
                }
            }
        }
    }

}
