package net.coreprotect.listener.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.utility.ErrorReporter;

public final class BlockExplodeListener extends Queue implements Listener {

    // (dx, dy, dz) for the five scanned neighbours: +x, -x, +z, -z, +y
    private static final int[] SCAN_OFFSETS = { 1, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, -1, 0, 1, 0 };

    // Packs block coordinates into one long, so the scan keys its map without a Location per block
    private static long positionKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    public static void processBlockExplode(String user, World world, List<Block> blockList) {
        HashMap<Long, Block> blockMap = new HashMap<>();

        for (Block block : blockList) {
            blockMap.put(positionKey(block.getX(), block.getY(), block.getZ()), block);
        }

        if (Config.getConfig(world).NATURAL_BREAK) {
            int worldMaxHeight = world.getMaxHeight();
            int worldMinHeight = BukkitAdapter.ADAPTER.getMinHeight(world);
            for (Block block : new ArrayList<>(blockMap.values())) {
                int x = block.getX();
                int y = block.getY();
                int z = block.getZ();

                for (int scan = 0; scan < 5; scan++) {
                    int scanX = x + SCAN_OFFSETS[scan * 3];
                    int scanY = y + SCAN_OFFSETS[scan * 3 + 1];
                    int scanZ = z + SCAN_OFFSETS[scan * 3 + 2];
                    long key = positionKey(scanX, scanY, scanZ);
                    if (blockMap.get(key) == null) {
                        Block scanBlock = world.getBlockAt(scanX, scanY, scanZ);
                        Material scanType = scanBlock.getType();
                        if (BlockGroup.TRACK_ANY.contains(scanType) || BlockGroup.TRACK_TOP.contains(scanType) || BlockGroup.TRACK_TOP_BOTTOM.contains(scanType) || BlockGroup.TRACK_BOTTOM.contains(scanType) || BlockGroup.TRACK_SIDE.contains(scanType)) {
                            blockMap.put(key, scanBlock);

                            // Properly log double blocks, such as doors
                            BlockData blockData = scanBlock.getBlockData();
                            if (blockData instanceof Bisected) {
                                int bisectY = ((Bisected) blockData).getHalf() == Half.TOP ? scanY - 1 : scanY + 1;
                                long bisectKey = positionKey(scanX, bisectY, scanZ);
                                if (bisectY >= worldMinHeight && bisectY < worldMaxHeight && blockMap.get(bisectKey) == null) {
                                    blockMap.put(bisectKey, world.getBlockAt(scanX, bisectY, scanZ));
                                }
                            }
                        }
                    }
                }
            }
        }

        for (Map.Entry<Long, Block> entry : blockMap.entrySet()) {
            Block block = entry.getValue();
            Material blockType = block.getType();
            BlockState blockState = block.getState();
            if (BukkitAdapter.ADAPTER.isSign(blockType) && Config.getConfig(world).SIGN_TEXT) {
                try {
                    Location location = blockState.getLocation();
                    Sign sign = (Sign) blockState;
                    String line1 = PaperAdapter.ADAPTER.getLine(sign, 0);
                    String line2 = PaperAdapter.ADAPTER.getLine(sign, 1);
                    String line3 = PaperAdapter.ADAPTER.getLine(sign, 2);
                    String line4 = PaperAdapter.ADAPTER.getLine(sign, 3);
                    String line5 = PaperAdapter.ADAPTER.getLine(sign, 4);
                    String line6 = PaperAdapter.ADAPTER.getLine(sign, 5);
                    String line7 = PaperAdapter.ADAPTER.getLine(sign, 6);
                    String line8 = PaperAdapter.ADAPTER.getLine(sign, 7);

                    boolean isFront = true;
                    int color = BukkitAdapter.ADAPTER.getColor(sign, isFront);
                    int colorSecondary = BukkitAdapter.ADAPTER.getColor(sign, !isFront);
                    boolean frontGlowing = BukkitAdapter.ADAPTER.isGlowing(sign, isFront);
                    boolean backGlowing = BukkitAdapter.ADAPTER.isGlowing(sign, !isFront);
                    boolean isWaxed = BukkitAdapter.ADAPTER.isWaxed(sign);

                    Queue.queueSignText(user, location, SignActions.BREAK, color, colorSecondary, frontGlowing, backGlowing, isWaxed, isFront, line1, line2, line3, line4, line5, line6, line7, line8, 5);
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }

            Database.containerBreakCheck(user, blockType, block, null, block.getLocation());
            Queue.queueBlockBreak(user, blockState, blockType, blockState.getBlockData().getAsString(), 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockExplode(BlockExplodeEvent event) {
        Material eventMaterial = BukkitAdapter.ADAPTER.getExplodedBlock(event);
        World world = event.getBlock().getLocation().getWorld();

        if (!BukkitAdapter.ADAPTER.shouldLogExplosion(event)){
            return;
        }

        String user = "";
        if (!eventMaterial.equals(Material.AIR) && !eventMaterial.equals(Material.CAVE_AIR)) {
            user = eventMaterial.name().toLowerCase(Locale.ROOT);

            if (user.contains("respawn_anchor")) {
                user = "#respawn_anchor";
            }
            else if (user.contains("_bed")) {
                user = "#bed";
            }
        }
        
        if (!user.startsWith("#")) {
            user = "#explosion";
        }

        boolean log = false;
        if (Config.getConfig(world).EXPLOSIONS) {
            log = true;
        }

        if (!event.isCancelled() && log) {
            processBlockExplode(user, world, event.blockList());
        }
    }

}
