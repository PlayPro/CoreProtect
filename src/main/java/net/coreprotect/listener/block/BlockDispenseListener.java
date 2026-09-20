package net.coreprotect.listener.block;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.paper.listener.BlockPreDispenseListener;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.TransactionId;
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

import java.util.Arrays;

public final class BlockDispenseListener extends Queue implements Listener {

    private static final Vector NO_VELOCITY = new Vector();
    private static final int DISPENSER_LIQUID_DUPLICATE_THRESHOLD = 256;
    private static final int DISPENSER_LIQUID_DUPLICATE_WINDOW_SECONDS = 1200;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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

                if (config.DISPENSER_TRANSACTIONS && (!BlockPreDispenseListener.useBlockPreDispenseEvent || (!BlockPreDispenseListener.useForDroppers && block.getType() == Material.DROPPER))) {
                    if (isDispenseRelative(event, newBlock) || material.equals(Material.FLINT_AND_STEEL) || material.equals(Material.SHEARS)) {
                        forceItem = false;
                    }

                    if (block.getType() == Material.DROPPER) {
                        forceItem = true; // droppers always drop items
                    }

                    BlockState dispenserState = PaperAdapter.ADAPTER.getBlockState(block, false);
                    ItemStack[] inventory = ((InventoryHolder) dispenserState).getInventory().getStorageContents();
                    if (forceItem) {
                        inventory = Arrays.copyOf(inventory, inventory.length + 1);
                        inventory[inventory.length - 1] = item;
                    }
                    InventoryChangeListener.inventoryTransaction(user, dispenserState, inventory);
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

                if (material == Material.BONE_MEAL && event.getVelocity().equals(NO_VELOCITY)) {
                    CacheHandler.redstoneCache.put(TransactionId.of(newBlock.getLocation()), new Object[] { System.currentTimeMillis(), user });
                }

                // The item transaction and bone meal hand-off above are unaffected by this option.
                if (!config.DISPENSERS) {
                    return;
                }

                BlockData newBlockData = type == Material.FIRE ? newBlock.getBlockData() : null;
                if (type == Material.FIRE && (!config.BLOCK_IGNITE || !(newBlockData instanceof Lightable))) {
                    return;
                }
                else if (type != Material.FIRE && (!config.BUCKETS || (!config.WATER_FLOW && type.equals(Material.WATER)) || (!config.LAVA_FLOW && type.equals(Material.LAVA)))) {
                    return;
                }

                if (type == Material.FIRE) { // lit a lightable block
                    type = newBlock.getType();
                    if (BlockGroup.LIGHTABLES.contains(type)) {
                        Lightable lightable = (Lightable) newBlockData;
                        lightable.setLit(true);

                        queueBlockPlace(user, newBlock.getState(), newBlock.getType(), newBlock.getState(), type, -1, 0, newBlockData.getAsString());
                    }
                }
                else if (!type.equals(Material.AIR) && isDispenseRelative(event, newBlock)) {
                    BlockState blockState = newBlock.getState();
                    if (config.DUPLICATE_SUPPRESSION && shouldSuppressDispenseLiquidDuplicate(user, newBlock, type)) {
                        return;
                    }
                    queueBlockPlaceValidate(user, blockState, newBlock, blockState, type, 1, 1, null, 0);
                }
                else if (material == Material.BUCKET && isDispenseRelative(event, newBlock)) {
                    BlockState blockState = newBlock.getState();
                    if (config.DUPLICATE_SUPPRESSION && shouldSuppressDispenseLiquidDuplicate(user, newBlock, type)) {
                        return;
                    }
                    queueBucketRemovalValidate(user, newBlock, blockState);
                }
            }
        }
    }

    /**
     * True when the event velocity was set to the target block position, compared bit for bit as Location.equals did.
     */
    private static boolean isDispenseRelative(BlockDispenseEvent event, Block newBlock) {
        Vector velocity = event.getVelocity();
        return Double.compare(velocity.getX(), newBlock.getX()) == 0 && Double.compare(velocity.getY(), newBlock.getY()) == 0 && Double.compare(velocity.getZ(), newBlock.getZ()) == 0;
    }

    private static void queueBucketRemovalValidate(String user, Block block, BlockState blockState) {
        Material originalType = blockState.getType();
        BlockData originalBlockData = blockState.getBlockData();
        String blockData = originalBlockData.getAsString();
        boolean waterlogged = originalBlockData instanceof Waterlogged && ((Waterlogged) originalBlockData).isWaterlogged();

        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                Material currentType = block.getType();
                Material removedType = originalType;
                boolean removed = !BlockUtils.isAir(originalType) && BlockUtils.isAir(currentType);

                if (waterlogged) {
                    BlockData currentBlockData = block.getBlockData();
                    removed = BlockUtils.isAir(currentType) || (currentType == originalType && currentBlockData instanceof Waterlogged && !((Waterlogged) currentBlockData).isWaterlogged());
                    removedType = Material.WATER;
                }

                if (removed) {
                    Queue.queueBlockBreak(user, blockState, removedType, blockData, 0);
                }
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }
        }, block.getLocation(), 0);
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
