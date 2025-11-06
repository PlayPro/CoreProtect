package net.coreprotect.listener.block;

import java.util.Arrays;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Waterlogged;
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

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (!event.isCancelled() && Config.getConfig(world).BLOCK_PLACE) {
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
                    user = "#water";
                }
                else if (material.equals(Material.LAVA_BUCKET)) {
                    type = Material.LAVA;
                    user = "#lava";
                }
                else if (material.equals(Material.FLINT_AND_STEEL)) {
                    type = Material.FIRE;
                    user = "#fire";
                }
                else {
                    type = BukkitAdapter.ADAPTER.getBucketContents(material);
                }

                if (!dispenseSuccess && material == Material.BONE_MEAL) {
                    CacheHandler.redstoneCache.put(newBlock.getLocation(), new Object[] { System.currentTimeMillis(), user });
                }

                if (type == Material.FIRE && (!Config.getConfig(world).BLOCK_IGNITE || !(newBlockData instanceof Lightable))) {
                    return;
                }
                else if (type != Material.FIRE && (!Config.getConfig(world).BUCKETS || (!Config.getConfig(world).WATER_FLOW && type.equals(Material.WATER)) || (!Config.getConfig(world).LAVA_FLOW && type.equals(Material.LAVA)))) {
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
                        if (type.equals(Material.WATER)) {
                            if (newBlockData instanceof Waterlogged) {
                                blockState = null;
                            }
                        }

                        if (!type.equals(Material.AIR)) {
                            queueBlockPlace(user, newBlock.getState(), newBlock.getType(), blockState, type, 1, 1, null);
                        }
                        else {
                            Queue.queueBlockBreak(user, newBlock.getState(), newBlock.getType(), newBlock.getBlockData().getAsString(), 0);
                        }
                    }
                }
            }
        }
    }

}
