package net.coreprotect.spigot.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.GenericGameEvent;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.listener.entity.EntityPlaceListener;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.EntitySpawnTracking;

public final class CushionPlaceListener implements Listener {

    private final Map<Location, List<EntitySpawnEvent>> pendingSpawns = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getItem() != null
                && event.getItem().getType().name().endsWith("_CUSHION")
                && Boolean.TRUE.equals(ConfigHandler.inspecting.get(event.getPlayer().getName()))) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (event.isAsynchronous() || event.isCancelled() || !EntitySpawnTracking.isCushion(entity)
                || EntitySpawnTracking.isTracked(entity) || !Config.getConfig(entity.getWorld()).ENTITY_SPAWNS) {
            return;
        }

        Location location = blockLocation(entity.getLocation());
        List<EntitySpawnEvent> spawns = pendingSpawns.computeIfAbsent(location, key -> new ArrayList<>());
        spawns.add(event);
        Scheduler.runTask(CoreProtect.getInstance(), () -> removePendingSpawn(location, event));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameEvent(GenericGameEvent event) {
        Entity actor = event.getEntity();
        if (event.isAsynchronous() || event.getEvent() != GameEvent.ENTITY_PLACE || (actor != null && !(actor instanceof Player))) {
            return;
        }

        Location reported = blockLocation(event.getLocation());
        Location matchedLocation = null;
        EntitySpawnEvent match = null;
        for (Location location : List.of(reported, reported.clone().add(0, 1, 0))) {
            List<EntitySpawnEvent> spawns = pendingSpawns.get(location);
            if (spawns == null) {
                continue;
            }
            for (EntitySpawnEvent spawn : spawns) {
                Entity entity = spawn.getEntity();
                if (spawn.isCancelled() || !entity.isValid() || EntitySpawnTracking.isTracked(entity)) {
                    continue;
                }
                if (match != null) {
                    return;
                }
                match = spawn;
                matchedLocation = location;
            }
        }
        if (match != null) {
            removePendingSpawn(matchedLocation, match);
            EntityPlaceListener.logPlacement(match.getEntity(), (Player) actor);
        }
    }

    private static Location blockLocation(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private void removePendingSpawn(Location location, EntitySpawnEvent spawn) {
        List<EntitySpawnEvent> spawns = pendingSpawns.get(location);
        if (spawns != null && spawns.remove(spawn) && spawns.isEmpty()) {
            pendingSpawns.remove(location);
        }
    }
}
