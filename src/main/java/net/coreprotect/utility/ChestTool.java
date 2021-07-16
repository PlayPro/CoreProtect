package net.coreprotect.utility;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.inventory.DoubleChestInventory;

public class ChestTool {

    public static void updateDoubleChest(Block block, BlockData blockData) {
        try {
            // modifying existing container, trigger physics update on both sides of double chest
            if (blockData != null && blockData instanceof Chest) {
                int chestType = 0;
                switch (((Chest) blockData).getType()) {
                    case LEFT:
                        chestType = 1;
                        break;
                    case RIGHT:
                        chestType = 2;
                        break;
                    default:
                        break;
                }

                if (chestType > 0) {
                    // check that not already a double chest
                    BlockState blockState = block.getState();
                    if (blockState instanceof org.bukkit.block.Chest) {
                        org.bukkit.block.Chest chest = (org.bukkit.block.Chest) blockState;
                        if (chest.getInventory() instanceof DoubleChestInventory) {
                            return;
                        }
                    }

                    Directional directional = (Directional) blockData;
                    BlockFace blockFace = directional.getFacing();
                    BlockFace newFace = null;
                    if (chestType == 1) {
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
                    else if (chestType == 2) {
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
                        Block relativeBlock = block.getRelative(newFace);
                        String relativeBlockData = relativeBlock.getBlockData().getAsString();
                        String newChestType = (chestType == 1) ? "type=right" : "type=left";
                        relativeBlockData = relativeBlockData.replace("type=single", newChestType);
                        relativeBlock.setBlockData(Bukkit.createBlockData(relativeBlockData), true);
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
