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

    // (dx, dy, dz) in BlockBreakListener's scan order.
    private static final int[] SCAN_OFFSETS = { 1, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, -1, 0, 1, 0, 0, -1, 0 };

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

                for (int offset = 0; offset < SCAN_OFFSETS.length; offset += 3) {
                    int scan = offset / 3 + 1;
                    int scanY = y + SCAN_OFFSETS[offset + 1];
                    if (scanY < worldMinHeight || scanY >= worldMaxHeight) {
                        continue;
                    }

                    int scanX = x + SCAN_OFFSETS[offset];
                    int scanZ = z + SCAN_OFFSETS[offset + 2];
                    long key = positionKey(scanX, scanY, scanZ);
                    if (blockMap.containsKey(key)) {
                        continue;
                    }

                    Block scanBlock = world.getBlockAt(scanX, scanY, scanZ);
                    Material scanType = scanBlock.getType();
                    if (scanType == Material.SCAFFOLDING) {
                        if (scan == BlockUtil.TOP || (scan < BlockUtil.TOP && block.getType() == Material.SCAFFOLDING)) {
                            BlockFadeListener.trackScaffoldingBreak(user, world, scanX, scanY, scanZ);
                        }
                        continue;
                    }
                    if (!tracksAttachment(scanType, scan)) {
                        continue;
                    }

                    BlockData blockData = scanBlock.getBlockData();
                    if (!BlockBreakListener.isAttached(block, scanBlock, blockData, scan)) {
                        continue;
                    }
                    blockMap.put(key, scanBlock);

                    if (blockData instanceof Bisected && !BukkitAdapter.ADAPTER.isSingleBlockPitcherCrop(blockData)) {
                        Half half = ((Bisected) blockData).getHalf();
                        int bisectY = half == Half.TOP ? scanY - 1 : scanY + 1;
                        long bisectKey = positionKey(scanX, bisectY, scanZ);
                        if (bisectY >= worldMinHeight && bisectY < worldMaxHeight && !blockMap.containsKey(bisectKey)) {
                            Block partner = world.getBlockAt(scanX, bisectY, scanZ);
                            BlockData partnerData = partner.getType() == scanType ? partner.getBlockData() : null;
                            if (partnerData instanceof Bisected && ((Bisected) partnerData).getHalf() != half) {
                                blockMap.put(bisectKey, partner);
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

    private static boolean tracksAttachment(Material type, int scan) {
        if (BlockGroup.TRACK_ANY.contains(type)) {
            return true;
        }
        switch (scan) {
            case BlockUtil.TOP:
                return BlockGroup.TRACK_TOP.contains(type) || BlockGroup.TRACK_TOP_BOTTOM.contains(type);
            case BlockUtil.BOTTOM:
                return BlockGroup.TRACK_BOTTOM.contains(type) || BlockGroup.TRACK_TOP_BOTTOM.contains(type);
            default:
                return BlockGroup.TRACK_SIDE.contains(type);
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
