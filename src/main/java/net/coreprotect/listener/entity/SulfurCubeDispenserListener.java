package net.coreprotect.listener.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.listener.player.PlayerDropItemListener;
import net.coreprotect.utility.EntityUtils;

public final class SulfurCubeDispenserListener implements Listener {

    private static final long ATTRIBUTION_MS = 2000L;
    private static final double TARGET_SEARCH_RADIUS = 1.25D;
    private static final Map<UUID, Long> dispenserTargets = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onBlockDispense(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.SHEARS) {
            return;
        }

        // Dispenser inventory changes are already logged by BlockPreDispenseListener/BlockDispenseListener.
        // This only attributes returned item drops because the dispense event does not expose old/new cube content.
        Entity target = getTargetSulfurCube(event.getBlock());
        if (target != null) {
            clearExpiredAttributions();
            dispenserTargets.put(target.getUniqueId(), System.currentTimeMillis() + ATTRIBUTION_MS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onEntityDropItem(EntityDropItemEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || !EntityUtils.isSulfurCube(entity.getType()) || !consumeRecentDispenserAttribution(entity.getUniqueId())) {
            return;
        }

        Item itemDrop = event.getItemDrop();
        if (itemDrop == null) {
            return;
        }

        PlayerDropItemListener.playerDropItem(itemDrop.getLocation(), "#dispenser", itemDrop.getItemStack());
    }

    private static Entity getTargetSulfurCube(Block dispenserBlock) {
        BlockData blockData = dispenserBlock.getBlockData();
        if (!(blockData instanceof Dispenser)) {
            return null;
        }

        BlockFace facing = ((Dispenser) blockData).getFacing();
        Block targetBlock = dispenserBlock.getRelative(facing);
        Location targetLocation = targetBlock.getLocation().add(0.5D, 0.5D, 0.5D);
        World world = targetLocation.getWorld();
        if (world == null) {
            return null;
        }

        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : world.getNearbyEntities(targetLocation, TARGET_SEARCH_RADIUS, TARGET_SEARCH_RADIUS, TARGET_SEARCH_RADIUS)) {
            if (!EntityUtils.isSulfurCube(entity.getType())) {
                continue;
            }

            double distance = entity.getLocation().distanceSquared(targetLocation);
            if (distance < closestDistance) {
                closest = entity;
                closestDistance = distance;
            }
        }

        return closest;
    }

    private static boolean consumeRecentDispenserAttribution(UUID entityId) {
        if (entityId == null) {
            return false;
        }

        Long expiresAt = dispenserTargets.remove(entityId);
        if (expiresAt == null) {
            return false;
        }

        return expiresAt >= System.currentTimeMillis();
    }

    private static void clearExpiredAttributions() {
        long currentTime = System.currentTimeMillis();
        dispenserTargets.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }
}
