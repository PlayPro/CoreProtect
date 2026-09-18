package net.coreprotect.utility;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.Jukebox;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.model.PendingBlockChange;
import net.coreprotect.thread.Scheduler;

public class BlockUtils {

    private static final int BLOCK_DATA_CACHE_LIMIT = 4096;

    private BlockUtils() {
        throw new IllegalStateException("Utility class");
    }

    private static volatile BlockDataCache blockDataCache;
    private static volatile Set<Material> singleChestMaterials;

    private static Set<Material> singleChestMaterials() {
        Set<Material> cached = singleChestMaterials;
        if (cached == null) {
            cached = EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST);
            cached.addAll(BukkitAdapter.ADAPTER.copperChestMaterials());
            singleChestMaterials = cached;
        }

        return cached;
    }

    /**
     * Replaces String.matches("\\d+"), which compiled a fresh Pattern on every lookup row.
     */
    private static boolean isDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }

        return true;
    }

    public static byte[] stringToByteData(String string, int type) {
        if (string == null) {
            return null;
        }

        // ClickHouse uncaches identifiers when a batch is discarded, so cached ids could point at unpublished rows
        Map<String, CachedBlockData> cache = ConfigHandler.databaseType.isClickHouse() ? null : blockDataCache();
        if (cache != null) {
            CachedBlockData cached = cache.get(string);
            if (cached != null && cached.type == type) {
                return cached.data;
            }
        }

        Material material = MaterialUtils.getType(type);
        String blockKey = MaterialUtils.getBlockName(type);
        if ((blockKey == null || blockKey.length() == 0) && material != null) {
            blockKey = material.getKey().toString();
        }
        if (blockKey == null || blockKey.length() == 0) {
            return null;
        }

        byte[] result = null;
        boolean resolved = true;
        BlockData defaultBlockData = createBlockData(type);
        if (defaultBlockData != null && !defaultBlockData.getAsString().equals(string) && string.startsWith(blockKey + "[") && string.endsWith("]")) {
            String substring = string.substring(blockKey.length() + 1, string.length() - 1);
            String[] blockDataSplit = substring.split(",");
            ArrayList<String> blockDataArray = new ArrayList<>();
            for (String data : blockDataSplit) {
                int id = MaterialUtils.getBlockdataId(data, true);
                if (id > -1) {
                    blockDataArray.add(Integer.toString(id));
                }
                else {
                    resolved = false;
                }
            }
            result = String.join(",", blockDataArray).getBytes(StandardCharsets.UTF_8);
        }
        else if (material != null && !string.contains(":") && (material == Material.PAINTING || BukkitAdapter.ADAPTER.isItemFrame(material))) {
            int id = MaterialUtils.getBlockdataId(string, true);
            if (id > -1) {
                result = Integer.toString(id).getBytes(StandardCharsets.UTF_8);
            }
            else {
                resolved = false;
            }
        }

        if (cache != null && resolved) {
            if (cache.size() >= BLOCK_DATA_CACHE_LIMIT) {
                cache.clear();
            }
            cache.put(string, new CachedBlockData(type, result));
        }
        return result;
    }

    /**
     * The encoded bytes depend on the material and blockdata id maps. Loading those maps replaces the map instances,
     * so a changed instance starts a new cache.
     */
    private static Map<String, CachedBlockData> blockDataCache() {
        BlockDataCache cache = blockDataCache;
        Map<Integer, String> materials = ConfigHandler.materialsReversed;
        Map<String, Integer> blockdata = ConfigHandler.blockdata;
        if (cache == null || cache.materials != materials || cache.blockdata != blockdata) {
            cache = new BlockDataCache(materials, blockdata);
            blockDataCache = cache;
        }
        return cache.entries;
    }

    private static final class BlockDataCache {

        private final Map<Integer, String> materials;
        private final Map<String, Integer> blockdata;
        private final Map<String, CachedBlockData> entries = new ConcurrentHashMap<>();

        private BlockDataCache(Map<Integer, String> materials, Map<String, Integer> blockdata) {
            this.materials = materials;
            this.blockdata = blockdata;
        }
    }

    private static final class CachedBlockData {

        private final int type;
        private final byte[] data;

        private CachedBlockData(int type, byte[] data) {
            this.type = type;
            this.data = data;
        }
    }

    public static String byteDataToString(byte[] data, int type) {
        String result = "";
        if (data != null) {
            Material material = MaterialUtils.getType(type);
            String blockKey = MaterialUtils.getBlockName(type);
            if ((blockKey == null || blockKey.length() == 0) && material != null) {
                blockKey = material.getKey().toString();
            }
            if (blockKey == null || blockKey.length() == 0) {
                return result;
            }

            result = new String(data, StandardCharsets.UTF_8);
            if (result.length() > 0) {
                if (isDigits(result)) {
                    result = result + ",";
                }
                if (result.contains(",")) {
                    String[] blockDataSplit = result.split(",");
                    ArrayList<String> blockDataArray = new ArrayList<>();
                    for (String blockData : blockDataSplit) {
                        String block = MaterialUtils.getBlockDataString(Integer.parseInt(blockData));
                        if (block.length() > 0) {
                            blockDataArray.add(block);
                        }
                    }

                    if (material != null && (material == Material.PAINTING || BukkitAdapter.ADAPTER.isItemFrame(material))) {
                        result = String.join(",", blockDataArray);
                    }
                    else {
                        result = blockKey + "[" + String.join(",", blockDataArray) + "]";
                    }
                }
                else {
                    result = "";
                }
            }
        }

        return result;
    }

    public static Waterlogged checkWaterlogged(BlockData blockData, BlockState blockReplacedState) {
        if (blockReplacedState.getType().equals(Material.WATER) && blockData instanceof Waterlogged) {
            if (blockReplacedState.getBlockData().equals(Material.WATER.createBlockData())) {
                Waterlogged waterlogged = (Waterlogged) blockData;
                waterlogged.setWaterlogged(true);
                return waterlogged;
            }
        }
        return null;
    }

    public static boolean isAir(Material type) {
        return (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR);
    }

    public static boolean solidBlock(Material type) {
        return type.isSolid();
    }

    public static boolean passableBlock(Block block) {
        return block.isPassable();
    }

    public static Material getType(Block block) {
        // Temp code
        return block.getType();
    }

    public static boolean iceBreakCheck(BlockState block, String user, Material type) {
        if (type.equals(Material.ICE)) { // Ice block
            int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
            int wid = WorldUtils.getWorldId(block.getWorld().getName());
            net.coreprotect.thread.CacheHandler.lookupCache.put("" + block.getX() + "." + block.getY() + "." + block.getZ() + "." + wid + "", new Object[] { unixtimestamp, user, Material.WATER });
            return true;
        }
        return false;
    }

    public static BlockData createBlockData(Material material) {
        try {
            BlockData result = material.createBlockData();
            if (result instanceof Waterlogged) {
                ((Waterlogged) result).setWaterlogged(false);
            }
            return result;
        }
        catch (Exception e) {
            return null;
        }
    }

    public static BlockData createBlockData(int type) {
        Material material = MaterialUtils.getType(type);
        if (material != null && material.isBlock()) {
            return createBlockData(material);
        }

        return BlockTypeUtils.createBlockData(MaterialUtils.getBlockName(type));
    }

    public static void prepareTypeAndData(Map<Block, PendingBlockChange> map, Block block, Material type, BlockData blockData, boolean update) {
        if (blockData == null) {
            blockData = createBlockData(type);
        }
        if (blockData == null) {
            return;
        }

        if (!update) {
            setTypeAndData(block, type, blockData, update);
            map.remove(block);
        }
        else {
            map.put(block, new PendingBlockChange(blockData, true));
        }
    }

    public static void queueTypeAndData(Map<Block, PendingBlockChange> map, Block block, Material type, BlockData blockData, boolean applyPhysics) {
        if (blockData == null) {
            blockData = createBlockData(type);
        }
        if (blockData != null) {
            map.put(block, new PendingBlockChange(blockData, applyPhysics));
        }
    }

    public static void setTypeAndData(Block block, Material type, BlockData blockData, boolean update) {
        if (blockData == null && type != null) {
            blockData = createBlockData(type);
        }

        if (blockData != null) {
            try {
                block.setBlockData(blockData, update);
            }
            catch (RuntimeException e) {
                if (!update) {
                    throw e;
                }

                try {
                    block.setBlockData(blockData, false);
                }
                catch (RuntimeException retryException) {
                    e.addSuppressed(retryException);
                    throw e;
                }
            }
        }
    }

    public static void updateBlock(final BlockState block) {
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            try {
                if (block.getBlockData() instanceof Waterlogged) {
                    Block currentBlock = block.getBlock();
                    if (currentBlock.getType().equals(block.getType())) {
                        block.setBlockData(currentBlock.getBlockData());
                    }
                }
                block.update();
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }
        }, block.getLocation());
    }

    public static Inventory getContainerInventory(BlockState blockState, boolean singleBlock) {
        Inventory inventory = null;
        try {
            if (blockState instanceof BlockInventoryHolder) {
                if (singleBlock && singleChestMaterials().contains(blockState.getType())) {
                    inventory = ((org.bukkit.block.Chest) blockState).getBlockInventory();
                }
                if (inventory == null) {
                    inventory = ((BlockInventoryHolder) blockState).getInventory();
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        return inventory;
    }

    public static List<Object> processMeta(BlockState block) {
        List<Object> meta = new ArrayList<>();
        try {
            if (block instanceof CommandBlock) {
                CommandBlock commandBlock = (CommandBlock) block;
                String command = commandBlock.getCommand();
                if (command.length() > 0) {
                    meta.add(command);
                }
            }
            else if (block instanceof Banner) {
                Banner banner = (Banner) block;
                meta.add(banner.getBaseColor());
                List<Pattern> patterns = banner.getPatterns();
                for (Pattern pattern : patterns) {
                    meta.add(pattern.serialize());
                }
            }
            else if (block instanceof ShulkerBox) {
                ShulkerBox shulkerBox = (ShulkerBox) block;
                ItemStack[] inventory = shulkerBox.getSnapshotInventory().getStorageContents();
                int slot = 0;
                for (ItemStack itemStack : inventory) {
                    Map<Integer, Object> itemMap = ItemUtils.serializeItemStackLegacy(itemStack, null, slot);
                    if (itemMap.size() > 0) {
                        meta.add(itemMap);
                    }
                    slot++;
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        if (meta.isEmpty()) {
            meta = null;
        }
        return meta;
    }

    public static ItemStack[] getJukeboxItem(Jukebox blockState) {
        ItemStack[] contents = null;
        try {
            contents = new ItemStack[] { blockState.getRecord() };
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        return contents;
    }

    public static int getSignData(boolean frontGlowing, boolean backGlowing) {
        if (frontGlowing && backGlowing) {
            return 3;
        }
        else if (backGlowing) {
            return 2;
        }
        else if (frontGlowing) {
            return 1;
        }

        return 0;
    }

    public static boolean isSideGlowing(boolean isFront, int data) {
        return ((isFront && (data == 1 || data == 3)) || (!isFront && (data == 2 || data == 3)));
    }
}
