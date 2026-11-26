package net.coreprotect.worldedit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.utility.BlockUtils;

public class WorldEditLogger extends Queue {

    public static WorldEditPlugin getWorldEdit(Server server) {
        Plugin plugin = server.getPluginManager().getPlugin("WorldEdit");
        if (plugin == null || !(plugin instanceof WorldEditPlugin)) {
            return null;
        }

        return (WorldEditPlugin) plugin;
    }

    protected static boolean needsBaseBlock(Material type, Config config) {
        return type == Material.SPAWNER || BlockUtils.isSkull(type) || WorldEditTileData.isBanner(type) || WorldEditTileData.isCommandBlock(type)
                || BlockGroup.SHULKER_BOXES.contains(type) || (config.ITEM_TRANSACTIONS && BlockGroup.CONTAINERS.contains(type))
                || (config.SIGN_TEXT && BukkitAdapter.ADAPTER.isSign(type));
    }

    protected static boolean isLoggedTopHalf(Material type, BlockData blockData) {
        return BlockGroup.LOGGED_BY_LOWER_HALF.contains(type) && blockData instanceof Bisected && ((Bisected) blockData).getHalf() == Half.TOP;
    }

    /**
     * Whether otherData, read from the side the half of blockData points to, is the opposite half of the same double block.
     */
    protected static boolean isOtherHalf(BlockData blockData, BlockData otherData) {
        return otherData.getMaterial() == blockData.getMaterial() && otherData instanceof Bisected && ((Bisected) otherData).getHalf() != ((Bisected) blockData).getHalf();
    }

    /**
     * The other half of a double block, read before this block is changed, since changing one half can make physics remove the other.
     * Null when the neighbouring block is not the matching opposite half.
     */
    protected static BlockState getOtherHalf(Extent extent, BlockVector3 position, Location location, BlockData blockData) {
        if (!(blockData instanceof Bisected) || blockData instanceof Stairs || blockData instanceof TrapDoor || BukkitAdapter.ADAPTER.isSingleBlockPitcherCrop(blockData)) {
            return null;
        }

        int offset = ((Bisected) blockData).getHalf() == Half.TOP ? -1 : 1;
        int otherY = location.getBlockY() + offset;
        if (otherY < BukkitAdapter.ADAPTER.getMinHeight(location.getWorld()) || otherY >= location.getWorld().getMaxHeight()) {
            return null;
        }

        BlockStateConversion other = BlockStateConversion.of(extent.getBlock(position.add(0, offset, 0)));
        if (!isOtherHalf(blockData, other.getBlockData())) {
            return null;
        }

        Location otherLocation = location.clone();
        otherLocation.setY(otherY);
        return new WorldEditBlockState(otherLocation, other.getMaterial(), other.getBlockData());
    }

    /**
     * @param otherHalf The matching other half of a double block as it was before this edit changed it, or null. Callers that do not log block physics only supply the lower half of a top half.
     */
    protected static void postProcess(Actor actor, Location location, BlockStateConversion oldState, CompoundTag oldTile, BlockStateConversion newState, CompoundTag newTile, Config config, boolean logBlockPhysics, BlockState otherHalf) {
        Material oldType = oldState.getMaterial();
        Material newType = newState.getMaterial();
        if (oldType == newType && oldState.getDataString().equals(newState.getDataString()) && !WorldEditTileData.changed(oldTile, newTile)) {
            return;
        }

        BlockData oldData = oldState.getBlockData();
        BlockData newData = newState.getBlockData();
        WorldEditBlockState oldBlock = new WorldEditBlockState(location, oldType, oldData);
        if (isLoggedTopHalf(oldType, oldData)) {
            oldBlock.setLowerHalf(otherHalf);
        }
        String newDataString = newState.getDataString();
        boolean newAir = BlockUtils.isAir(newType);
        boolean logBreak = !BlockUtils.isAir(oldType);
        if (logBlockPhysics && oldType == Material.WATER && newData instanceof Waterlogged) {
            Waterlogged waterlogged = BlockUtils.checkWaterlogged(newData.clone(), oldBlock);
            if (waterlogged != null) {
                newData = waterlogged;
                newDataString = waterlogged.getAsString();
                logBreak = false;
            }
        }

        String user = actor.getName();
        if (logBreak) {
            ItemStack[] items = capturedInventory(oldType, oldTile, config.ITEM_TRANSACTIONS);
            WorldEditTileData.captureBlockMetadata(oldBlock, oldTile, items);
            if (config.ITEM_TRANSACTIONS && items != null && BlockGroup.CONTAINERS.contains(oldType)) {
                Queue.queueContainerBreak(user, location, oldType, items);
            }
            logSign(user, location, oldType, oldTile, config, SignActions.BREAK, 5);
            int extraData = oldType == Material.SPAWNER ? WorldEditTileData.readSpawner(oldTile, 0) : 0;
            Queue.queueBlockBreak(user, oldBlock, oldType, oldState.getDataString(), null, extraData, 0);
            if (logBlockPhysics && oldType != newType && otherHalf != null && !BlockGroup.LOGGED_BY_LOWER_HALF.contains(oldType)) {
                Queue.queueBlockBreak(user, otherHalf, otherHalf.getType(), otherHalf.getBlockData().getAsString(), null, 0, 0);
            }
        }

        WorldEditBlockState newBlock = new WorldEditBlockState(location, newType, newData);
        if (!newAir) {
            WorldEditTileData.captureBlockMetadata(newBlock, newTile, capturedInventory(newType, newTile, false));
            int extraData = newType == Material.SPAWNER ? WorldEditTileData.readSpawner(newTile, -1) : -1;
            Queue.queueBlockPlace(user, newBlock, newType, null, newType, extraData, 0, newDataString);
            logSign(user, location, newType, newTile, config, SignActions.PLACE, 0);
        }
        else if (logBlockPhysics && oldData instanceof Waterlogged && ((Waterlogged) oldData).isWaterlogged()) {
            Queue.queueBlockPlace(user, newBlock, newType, null, Material.WATER, -1, 0, null);
        }
    }

    private static ItemStack[] capturedInventory(Material type, CompoundTag tag, boolean itemTransactions) {
        return BlockGroup.SHULKER_BOXES.contains(type) || (itemTransactions && BlockGroup.CONTAINERS.contains(type)) ? WorldEditTileData.readInventory(tag) : null;
    }

    private static void logSign(String user, Location location, Material type, CompoundTag tile, Config config, int action, int offset) {
        if (tile != null && config.SIGN_TEXT && BukkitAdapter.ADAPTER.isSign(type)) {
            Queue.queueSignText(user, location, action, WorldEditTileData.readSign(tile), true, offset);
        }
    }
}
