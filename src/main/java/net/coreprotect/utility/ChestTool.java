package net.coreprotect.utility;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Chest.Type;

import net.coreprotect.CoreProtect;
import net.coreprotect.thread.Scheduler;

public class ChestTool {

    private ChestTool() {
        throw new IllegalStateException("Utility class");
    }

    public static void updateDoubleChest(Block block, BlockData blockData, boolean forceValidation) {
        if (!(blockData instanceof Chest) || ((Chest) blockData).getType() == Type.SINGLE) {
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
            Block relativeBlock = block.getRelative(newFace);
            if (!forceValidation && (relativeBlock.getBlockData() instanceof Chest) && ((Chest) relativeBlock.getBlockData()).getType() == newType) {
                return;
            }

            validateContainer(blockData, newType, block, relativeBlock);
        }
    }

    private static void validateContainer(BlockData blockData, Type newType, Block block, Block relativeBlock) {
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                BlockData relativeBlockData = relativeBlock.getBlockData();
                if (!blockData.getAsString().equals(block.getBlockData().getAsString()) || !(relativeBlockData instanceof Chest) || ((Chest) relativeBlockData).getType() == newType) {
                    return;
                }

                Chest chestData = (Chest) blockData;
                chestData.setType(newType);
                relativeBlock.setBlockData(chestData, true);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, relativeBlock.getLocation(), 2);
    }

}
