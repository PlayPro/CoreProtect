package net.coreprotect.utility.entity;

import java.util.Locale;

import org.bukkit.Art;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.Util;

public class HangingUtil {

    private HangingUtil() {
        throw new IllegalStateException("Utility class");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void spawnHanging(final BlockState blockstate, final Material rowType, final int rowData, int delay) {
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                Block block = blockstate.getBlock();
                int x = block.getX();
                int y = block.getY();
                int z = block.getZ();

                for (Entity e : block.getChunk().getEntities()) {
                    if ((BukkitAdapter.ADAPTER.isItemFrame(rowType) && e instanceof ItemFrame) || (rowType.equals(Material.PAINTING) && e instanceof Painting)) {
                        Location el = e.getLocation();
                        if (el.getBlockX() == x && el.getBlockY() == y && el.getBlockZ() == z) {
                            e.remove();
                            break;
                        }
                    }
                }

                Block c1 = block.getWorld().getBlockAt((x + 1), y, z);
                Block c2 = block.getWorld().getBlockAt((x - 1), y, z);
                Block c3 = block.getWorld().getBlockAt(x, y, (z + 1));
                Block c4 = block.getWorld().getBlockAt(x, y, (z - 1));

                BlockFace faceSet = null;
                if (!BlockGroup.NON_ATTACHABLE.contains(c1.getType())) {
                    faceSet = BlockFace.WEST;
                    block = c1;
                }
                else if (!BlockGroup.NON_ATTACHABLE.contains(c2.getType())) {
                    faceSet = BlockFace.EAST;
                    block = c2;
                }
                else if (!BlockGroup.NON_ATTACHABLE.contains(c3.getType())) {
                    faceSet = BlockFace.NORTH;
                    block = c3;
                }
                else if (!BlockGroup.NON_ATTACHABLE.contains(c4.getType())) {
                    faceSet = BlockFace.SOUTH;
                    block = c4;
                }

                BlockFace face = null;
                if (!Util.solidBlock(Util.getType(block.getRelative(BlockFace.EAST)))) {
                    face = BlockFace.EAST;
                }
                else if (!Util.solidBlock(Util.getType(block.getRelative(BlockFace.NORTH)))) {
                    face = BlockFace.NORTH;
                }
                else if (!Util.solidBlock(Util.getType(block.getRelative(BlockFace.WEST)))) {
                    face = BlockFace.WEST;
                }
                else if (!Util.solidBlock(Util.getType(block.getRelative(BlockFace.SOUTH)))) {
                    face = BlockFace.SOUTH;
                }

                if (faceSet != null && face != null) {
                    if (rowType.equals(Material.PAINTING)) {
                        String name = Util.getArtName(rowData);
                        Art painting = Art.getByName(name.toUpperCase(Locale.ROOT));
                        int height = painting.getBlockHeight();
                        int width = painting.getBlockWidth();
                        int paintingX = x;
                        int paintingY = y;
                        int paintingZ = z;
                        if (height != 1 || width != 1) {
                            if (height > 1) {
                                if (height != 3) {
                                    paintingY = paintingY - 1;
                                }
                            }
                            if (width > 1) {
                                if (faceSet.equals(BlockFace.WEST)) {
                                    paintingZ--;
                                }
                                else if (faceSet.equals(BlockFace.SOUTH)) {
                                    paintingX--;
                                }
                            }
                        }
                        Block spawnBlock = block.getRelative(face);
                        Util.setTypeAndData(spawnBlock, Material.AIR, null, true);
                        Painting hanging = null;
                        try {
                            hanging = block.getWorld().spawn(spawnBlock.getLocation(), Painting.class);
                        }
                        catch (Exception e) {
                        }
                        if (hanging != null) {
                            hanging.teleport(block.getWorld().getBlockAt(paintingX, paintingY, paintingZ).getLocation());
                            hanging.setFacingDirection(faceSet, true);
                            hanging.setArt(painting, true);
                        }
                    }
                    else if (BukkitAdapter.ADAPTER.isItemFrame(rowType)) {
                        try {
                            Block spawnBlock = block.getRelative(face);
                            Util.setTypeAndData(spawnBlock, Material.AIR, null, true);
                            Class itemFrame = BukkitAdapter.ADAPTER.getFrameClass(rowType);
                            Entity entity = block.getWorld().spawn(spawnBlock.getLocation(), itemFrame);
                            if (entity instanceof ItemFrame) {
                                ItemFrame hanging = (ItemFrame) entity;
                                hanging.teleport(block.getWorld().getBlockAt(x, y, z).getLocation());
                                hanging.setFacingDirection(faceSet, true);

                                Material type = Util.getType(rowData);
                                if (type != null) {
                                    ItemStack istack = new ItemStack(type, 1);
                                    hanging.setItem(istack);
                                }
                            }
                        }
                        catch (Exception e) {
                        }
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, delay);
    }

    public static void removeHanging(final BlockState block, int delay) {
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                for (Entity e : block.getChunk().getEntities()) {
                    if (e instanceof ItemFrame || e instanceof Painting) {
                        Location el = e.getLocation();
                        if (el.getBlockX() == block.getX() && el.getBlockY() == block.getY() && el.getBlockZ() == block.getZ()) {
                            e.remove();
                        }
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, delay);
    }

}
