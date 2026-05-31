package net.coreprotect.listener.block;

import java.util.Arrays;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.listener.BlockPreDispenseListener;
import net.coreprotect.thread.CacheHandler;

public final class BlockDispenseListener extends Queue implements Listener {

    private static final int DISPENSER_LIQUID_DUPLICATE_THRESHOLD = 256;
    private static final int DISPENSER_LIQUID_DUPLICATE_WINDOW_SECONDS = 1200;

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        Config config = Config.getConfig(world);
        if (!event.isCancelled() && config.BLOCK_PLACE) {
            BlockData blockData = block.getBlockData();
            ItemStack item = event.getItem();
            if (item != null && blockData instanceof Dispenser) {
                Dispenser dispenser = (Dispenser) blockData;
                Material material = item.getType();
                Material type = Material.AIR;
                String user = "#dispenser";
                boolean forceItem = true;

                Block newBlock = block.getRelative(dispenser.getFacing());
                BlockData newBlockData = newBlock.getBlockData();
                Location velocityLocation = event.getVelocity().toLocation(world);
                boolean dispenseSuccess = !event.getVelocity().equals(new Vector()); // true if velocity is set
                boolean dispenseRelative = newBlock.getLocation().equals(velocityLocation); // true if velocity location matches relative location

                if (!BlockPreDispenseListener.useBlockPreDispenseEvent || (!BlockPreDispenseListener.useForDroppers && block.getType() == Material.DROPPER)) {
                    if (dispenseRelative || material.equals(Material.FLINT_AND_STEEL) || material.equals(Material.SHEARS)) {
                        forceItem = false;
                    }

                    if (block.getType() == Material.DROPPER) {
                        forceItem = true; // droppers always drop items
                    }

                    ItemStack[] inventory = ((InventoryHolder) block.getState()).getInventory().getStorageContents();
                    if (forceItem) {
                        inventory = Arrays.copyOf(inventory, inventory.length + 1);
                        inventory[inventory.length - 1] = item;
                    }
                    InventoryChangeListener.inventoryTransaction(user, block.getLocation(), inventory);
                }

                if (material.equals(Material.WATER_BUCKET)) {
                    type = Material.WATER;
                }
                else if (material.equals(Material.LAVA_BUCKET)) {
                    type = Material.LAVA;
                }
                else if (material.equals(Material.FLINT_AND_STEEL)) {
                    type = Material.FIRE;
                    user = "#fire";
                }
                else {
                    type = BukkitAdapter.ADAPTER.getBucketContents(material);
                }

                if (!dispenseSuccess && material == Material.BONE_MEAL) {
                    String key = CacheHandler.locationKey(newBlock.getLocation());
                    if (!key.isEmpty()) {
                        CacheHandler.redstoneCache.put(key, new Object[] { System.currentTimeMillis(), user });
                    }
                }

                if (type == Material.FIRE && (!config.BLOCK_IGNITE || !(newBlockData instanceof Lightable))) {
                    return;
                }
                else if (type != Material.FIRE && (!config.BUCKETS || (!config.WATER_FLOW && type.equals(Material.WATER)) || (!config.LAVA_FLOW && type.equals(Material.LAVA)))) {
                    return;
                }

                if (!type.equals(Material.AIR) || !newBlock.getType().equals(Material.AIR)) {
                    if (type == Material.FIRE) { // lit a lightable block
                        type = newBlock.getType();
                        if (BlockGroup.LIGHTABLES.contains(type)) {
                            Lightable lightable = (Lightable) newBlockData;
                            lightable.setLit(true);

                            queueBlockPlace(user, newBlock.getState(), newBlock.getType(), newBlock.getState(), type, -1, 0, newBlockData.getAsString());
                        }
                    }
                    else if (dispenseRelative) {
                        BlockState blockState = newBlock.getState();
                        if (config.DUPLICATE_SUPPRESSION && shouldSuppressDispenseLiquidDuplicate(user, newBlock, type)) {
                            return;
                        }
                        if (!type.equals(Material.AIR)) {
                            queueBlockPlaceValidate(user, blockState, newBlock, blockState, type, 1, 1, null, 0);
                        }
                        else {
                            Queue.queueBlockBreak(user, newBlock.getState(), newBlock.getType(), newBlock.getBlockData().getAsString(), 0);
                        }
                    }
                }
            }
        }
    }

    private boolean shouldSuppressDispenseLiquidDuplicate(String user, Block targetBlock, Material newType) {
        Material oldType = targetBlock.getType();
        boolean placeLiquid = ("#water".equals(user) || "#lava".equals(user) || "#dispenser".equals(user)) && (newType == Material.WATER || newType == Material.LAVA);
        boolean removeLiquid = "#dispenser".equals(user) && newType == Material.AIR && (oldType == Material.WATER || oldType == Material.LAVA);
        if (!placeLiquid && !removeLiquid) {
            return false;
        }

        if (!isLiquidToggleType(oldType) || !isLiquidToggleType(newType) || oldType == newType) {
            return false;
        }

        String left = oldType.name();
        String right = newType.name();
        if (left.compareTo(right) > 0) {
            String swap = left;
            left = right;
            right = swap;
        }

        String signature = targetBlock.getWorld().getUID().toString() + "." + targetBlock.getX() + "." + targetBlock.getY() + "." + targetBlock.getZ() + ".#dispense-liquid." + left + "<>" + right;
        return CacheHandler.shouldSuppressRepeat(CacheHandler.flowDuplicateCache, signature, DISPENSER_LIQUID_DUPLICATE_THRESHOLD, DISPENSER_LIQUID_DUPLICATE_WINDOW_SECONDS);
    }

    private boolean isLiquidToggleType(Material type) {
        return type == Material.AIR || type == Material.WATER || type == Material.LAVA;
    }

}
