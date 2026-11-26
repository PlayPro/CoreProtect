package net.coreprotect.utility;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Chest.Type;

import net.coreprotect.CoreProtect;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;

public class ChestTool {

    private ChestTool() {
        throw new IllegalStateException("Utility class");
    }

    public static void updateDoubleChest(Block block, BlockData blockData, boolean forceValidation) {
        if (block == null || !(blockData instanceof Chest) || ((Chest) blockData).getType() == Type.SINGLE) {
            return;
        }

        Directional directional = (Directional) blockData;
        BlockFace blockFace = directional.getFacing();
        BlockFace newFace = null;

        Type chestType = ((Chest) blockData).getType();
        if (chestType == Type.LEFT) {
            switch (blockFace) {
                case NORTH:
                    newFace = BlockFace.EAST;
                    break;
                case WEST:
                    newFace = BlockFace.NORTH;
                    break;
                case EAST:
                    newFace = BlockFace.SOUTH;
                    break;
                case SOUTH:
                    newFace = BlockFace.WEST;
                    break;
                default:
                    break;
            }
        }
        else if (chestType == Type.RIGHT) {
            switch (blockFace) {
                case NORTH:
                    newFace = BlockFace.WEST;
                    break;
                case WEST:
                    newFace = BlockFace.SOUTH;
                    break;
                case EAST:
                    newFace = BlockFace.NORTH;
                    break;
                case SOUTH:
                    newFace = BlockFace.EAST;
                    break;
                default:
                    break;
            }
        }

        if (newFace != null) {
            Type newType = (chestType == Type.LEFT) ? Type.RIGHT : Type.LEFT;
            Material sourceMaterial = blockData.getMaterial();
            String expectedSourceString = blockData.getAsString();
            Block relativeBlock = block.getRelative(newFace);
            boolean relativeOwned = PaperAdapter.ADAPTER == null
                    || PaperAdapter.ADAPTER.isOwnedByCurrentRegion(relativeBlock.getWorld(), relativeBlock.getX() >> 4, relativeBlock.getZ() >> 4);

            if (relativeOwned && !forceValidation) {
                BlockData relativeBlockData = relativeBlock.getBlockData();
                if (isCompatibleChest(relativeBlockData, sourceMaterial, blockFace) && ((Chest) relativeBlockData).getType() == newType) {
                    return;
                }
            }

            validateContainer(expectedSourceString, sourceMaterial, blockFace, newType, block, relativeBlock, relativeOwned);
        }
    }

    private static void validateContainer(String expectedSourceString, Material sourceMaterial, BlockFace expectedFacing, Type newType, Block block, Block relativeBlock, boolean relativeOwned) {
        if (relativeOwned) {
            Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
                try {
                    if (PaperAdapter.ADAPTER != null && !PaperAdapter.ADAPTER.isOwnedByCurrentRegion(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
                        scheduleCrossRegionValidation(expectedSourceString, sourceMaterial, expectedFacing, newType, block, relativeBlock);
                        return;
                    }
                    if (!expectedSourceString.equals(block.getBlockData().getAsString())) {
                        return;
                    }
                    applyRelativeChestUpdate(relativeBlock, sourceMaterial, expectedFacing, newType);
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }, relativeBlock.getLocation(), 2);
            return;
        }

        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(),
                () -> verifySourceAndDispatch(expectedSourceString, sourceMaterial, expectedFacing, newType, block, relativeBlock),
                block.getLocation(), 2);
    }

    private static void scheduleCrossRegionValidation(String expectedSourceString, Material sourceMaterial, BlockFace expectedFacing, Type newType, Block block, Block relativeBlock) {
        Scheduler.runTask(CoreProtect.getInstance(),
                () -> verifySourceAndDispatch(expectedSourceString, sourceMaterial, expectedFacing, newType, block, relativeBlock),
                block.getLocation());
    }

    private static void verifySourceAndDispatch(String expectedSourceString, Material sourceMaterial, BlockFace expectedFacing, Type newType, Block block, Block relativeBlock) {
        try {
            if (!expectedSourceString.equals(block.getBlockData().getAsString())) {
                return;
            }
            Scheduler.runTask(CoreProtect.getInstance(),
                    () -> applyRelativeChestUpdate(relativeBlock, sourceMaterial, expectedFacing, newType),
                    relativeBlock.getLocation());
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static void applyRelativeChestUpdate(Block relativeBlock, Material expectedMaterial, BlockFace expectedFacing, Type newType) {
        try {
            BlockData relativeBlockData = relativeBlock.getBlockData();
            if (!isCompatibleChest(relativeBlockData, expectedMaterial, expectedFacing)) {
                return;
            }
            Chest chestData = (Chest) relativeBlockData;
            if (chestData.getType() != Type.SINGLE) {
                return;
            }
            Chest updated = (Chest) relativeBlockData.clone();
            updated.setType(newType);
            relativeBlock.setBlockData(updated, true);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static boolean isCompatibleChest(BlockData relativeBlockData, Material expectedMaterial, BlockFace expectedFacing) {
        if (!(relativeBlockData instanceof Chest) || !(relativeBlockData instanceof Directional)) {
            return false;
        }
        if (relativeBlockData.getMaterial() != expectedMaterial) {
            return false;
        }
        return ((Directional) relativeBlockData).getFacing() == expectedFacing;
    }

}
