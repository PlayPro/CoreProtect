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

        Location location = entity.getLocation().getBlock().getLocation();
        List<EntitySpawnEvent> spawns = pendingSpawns.computeIfAbsent(location, key -> new ArrayList<>());
        spawns.add(event);
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            spawns.remove(event);
            if (spawns.isEmpty()) {
                pendingSpawns.remove(location, spawns);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameEvent(GenericGameEvent event) {
        if (event.isAsynchronous() || event.getEvent() != GameEvent.ENTITY_PLACE || !(event.getEntity() instanceof Player)) {
            return;
        }

        List<EntitySpawnEvent> spawns = pendingSpawns.remove(event.getLocation().getBlock().getLocation());
        if (spawns == null) {
            return;
        }
        Entity placed = null;
        for (EntitySpawnEvent spawn : spawns) {
            Entity entity = spawn.getEntity();
            if (spawn.isCancelled() || !entity.isValid()) {
                continue;
            }
            if (placed != null) {
                return;
            }
            placed = entity;
        }
        if (placed != null) {
            EntityPlaceListener.logPlacement(placed, (Player) event.getEntity());
        }
    }
}
