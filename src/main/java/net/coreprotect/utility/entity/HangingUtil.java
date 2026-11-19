package net.coreprotect.utility.entity;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.bukkit.Art;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.listener.player.PlayerInteractEntityListener;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.MaterialUtils;

public class HangingUtil {

    private static final List<BlockFace> WALL_FACES = List.of(BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH);
    private static final List<BlockFace> FRAME_FACES = List.of(BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.UP, BlockFace.DOWN);

    private HangingUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isSupportedDecoration(Hanging entity) {
        return entity instanceof ItemFrame || entity instanceof Painting;
    }

    public static Material getMaterial(Hanging entity) {
        return entity instanceof ItemFrame ? BukkitAdapter.ADAPTER.getFrameType(entity) : Material.PAINTING;
    }

    public static String getFacingData(Hanging entity) {
        return "FACING=" + entity.getFacing().name();
    }

    public static int getArtId(Hanging entity) {
        if (entity instanceof Painting) {
            try {
                return MaterialUtils.getArtId(MaterialUtils.getPaintingArtName((Painting) entity), true);
            }
            catch (IncompatibleClassChangeError ignored) {
                return 0;
            }
        }
        return 0;
    }

    public static void logFrameItemRemoval(ItemFrame frame, String user, boolean logDrops) {
        ItemStack item = frame.getItem();
        if (item == null || BlockUtils.isAir(item.getType()) || item.getAmount() <= 0) {
            return;
        }

        ItemStack[] before = { item.clone() };
        PlayerInteractEntityListener.queueContainerSpecifiedItems(user, Material.ITEM_FRAME, new Object[] { before, new ItemStack[0], frame.getFacing() }, frame.getLocation(), logDrops);
    }

    public static void spawnHanging(BlockState state, Material type, String hangingData, int rowData) {
        try {
            boolean painting = type == Material.PAINTING;
            if (!painting && !BukkitAdapter.ADAPTER.isItemFrame(type)) {
                return;
            }
            List<BlockFace> faces = painting ? WALL_FACES : FRAME_FACES;
            Block block = state.getBlock();
            BlockFace stored = parseFacing(hangingData);
            BlockFace face = stored != null && faces.contains(stored) ? stored : findFacing(block, faces);
            if (face == null) {
                return;
            }

            Art art = painting ? BukkitAdapter.ADAPTER.getPaintingArt(MaterialUtils.getArtName(rowData)) : null;
            if (painting && art == null) {
                return;
            }
            Hanging existing = findAt(state, face, decoration -> painting == (decoration instanceof Painting), false);
            if (existing != null) {
                existing.remove();
            }
            Location location = block.getLocation();
            if (painting) {
                Location anchor = paintingAnchor(location, face, art.getBlockWidth(), art.getBlockHeight());
                if (owns(anchor.getWorld(), anchor.getBlockX(), anchor.getBlockZ())) {
                    spawnPainting(anchor, face, art);
                }
                else {
                    Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> spawnPainting(anchor, face, art), anchor, 0);
                }
            }
            else {
                Material itemType = MaterialUtils.getType(rowData);
                BukkitAdapter.ADAPTER.spawn(block.getWorld(), location, BukkitAdapter.ADAPTER.getFrameClass(type), frame -> {
                    frame.setFacingDirection(face, true);
                    if (itemType != null && !BlockUtils.isAir(itemType)) {
                        frame.setItem(new ItemStack(itemType, 1));
                    }
                });
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static void spawnPainting(Location anchor, BlockFace face, Art art) {
        BukkitAdapter.ADAPTER.spawn(anchor.getWorld(), anchor, Painting.class, painting -> {
            painting.setFacingDirection(face, true);
            painting.setArt(art, true);
        });
    }

    static Location paintingAnchor(Location location, BlockFace face, int width, int height) {
        int x = width % 2 == 0 && face == BlockFace.SOUTH ? -1 : 0;
        int y = height % 2 == 0 ? -1 : 0;
        int z = width % 2 == 0 && face == BlockFace.WEST ? -1 : 0;
        return location.clone().add(x, y, z);
    }

    private static BlockFace findFacing(Block block, List<BlockFace> faces) {
        World world = block.getWorld();
        for (BlockFace face : faces) {
            BlockFace support = face.getOppositeFace();
            if (owns(world, block.getX() + support.getModX(), block.getZ() + support.getModZ())) {
                Material material = block.getRelative(support).getType();
                if (!BlockUtils.isAir(material) && !BlockGroup.NON_ATTACHABLE.contains(material)) {
                    return face;
                }
            }
        }
        return null;
    }

    private static boolean owns(World world, int x, int z) {
        return !ConfigHandler.isFolia || PaperAdapter.ADAPTER.isOwnedByCurrentRegion(world, x >> 4, z >> 4);
    }

    static BlockFace parseFacing(String data) {
        if (data == null || !data.regionMatches(true, 0, "FACING=", 0, "FACING=".length())) {
            return null;
        }
        return parseFrameFace(data.substring("FACING=".length()));
    }

    public static BlockFace parseFrameFace(String value) {
        try {
            BlockFace face = BlockFace.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return FRAME_FACES.contains(face) ? face : null;
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void removeHanging(BlockState state, String hangingData) {
        removeHanging(state, null, hangingData);
    }

    public static void removeHanging(BlockState state, Material type, String hangingData) {
        try {
            BlockFace face = parseFacing(hangingData);
            if (face == null && hangingData != null && !hangingData.isBlank()) {
                return;
            }
            Hanging target = findAt(state, face, decoration -> type == null || getMaterial(decoration) == type, true);
            if (target != null) {
                target.remove();
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static Hanging findAt(BlockState state, BlockFace face, Predicate<Hanging> matchesType, boolean requireUnique) {
        Hanging target = null;
        for (Entity entity : state.getChunk().getEntities()) {
            if (!(entity instanceof Hanging) || !isSupportedDecoration((Hanging) entity)) {
                continue;
            }
            Hanging decoration = (Hanging) entity;
            if (!matchesType.test(decoration)) {
                continue;
            }
            Location location = entity.getLocation();
            if (location.getBlockX() == state.getX() && location.getBlockY() == state.getY() && location.getBlockZ() == state.getZ()
                    && (face == null || decoration.getFacing() == face)) {
                if (!requireUnique) {
                    return decoration;
                }
                if (target != null) {
                    return null;
                }
                target = decoration;
            }
        }
        return target;
    }
}
