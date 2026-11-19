package net.coreprotect.utility;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
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
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.worldedit.WorldEditBlockState;

public class BlockUtils {

    private static final int BLOCK_DATA_CACHE_LIMIT = 4096;
    private static volatile BlockDataCache blockDataCache;

    private BlockUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static byte[] stringToByteData(String string, int type) {
        if (string == null) {
            return null;
        }

        // ClickHouse uncaches identifiers when a batch is discarded, so a cached encoding could point at an unpublished id
        Map<BlockDataCacheKey, CachedBlockData> cache = ConfigHandler.databaseType.isClickHouse() ? null : blockDataCache();
        BlockDataCacheKey cacheKey = cache != null ? new BlockDataCacheKey(type, string) : null;
        if (cache != null) {
            CachedBlockData cached = cache.get(cacheKey);
            if (cached != null) {
                return cached.data;
            }
        }

        byte[] result = null;
        boolean resolved = true;
        Material material = MaterialUtils.getType(type);
        String blockKey = MaterialUtils.getBlockName(type);
        if ((blockKey == null || blockKey.length() == 0) && material != null) {
            blockKey = material.getKey().toString();
        }
        if (blockKey == null || blockKey.length() == 0) {
            return null;
        }

        BlockData defaultBlockData = createBlockData(type);
        if (defaultBlockData != null && !defaultBlockData.getAsString().equals(string) && string.startsWith(blockKey + "[") && string.endsWith("]")) {
            String substring = string.substring(blockKey.length() + 1, string.length() - 1);
            if (!substring.isEmpty()) {
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
                if (!blockDataArray.isEmpty()) {
                    result = String.join(",", blockDataArray).getBytes(StandardCharsets.UTF_8);
                }
            }
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
            synchronized (cache) {
                if (cache.size() >= BLOCK_DATA_CACHE_LIMIT) {
                    cache.clear();
                }
                cache.put(cacheKey, new CachedBlockData(result));
            }
        }
        return result;
    }

    // Loading the material or blockdata maps assigns new map instances, which starts a new cache
    private static Map<BlockDataCacheKey, CachedBlockData> blockDataCache() {
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
        private final Map<BlockDataCacheKey, CachedBlockData> entries = new ConcurrentHashMap<>();

        private BlockDataCache(Map<Integer, String> materials, Map<String, Integer> blockdata) {
            this.materials = materials;
            this.blockdata = blockdata;
        }
    }

    private static final class BlockDataCacheKey {

        private final int type;
        private final String data;

        private BlockDataCacheKey(int type, String data) {
            this.type = type;
            this.data = data;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BlockDataCacheKey)) {
                return false;
            }
            BlockDataCacheKey that = (BlockDataCacheKey) other;
            return this.type == that.type && this.data.equals(that.data);
        }

        @Override
        public int hashCode() {
            return 31 * this.type + this.data.hashCode();
        }
    }

    private static final class CachedBlockData {

        private final byte[] data;

        private CachedBlockData(byte[] data) {
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
                if (isAsciiDigits(result)) {
                    result = result + ",";
                }
                if (result.contains(",")) {
                    String[] blockDataSplit = result.split(",");
                    ArrayList<String> blockDataArray = new ArrayList<>();
                    for (String blockData : blockDataSplit) {
                        if (!isAsciiDigits(blockData)) {
                            continue;
                        }
                        int blockDataId;
                        try {
                            blockDataId = Integer.parseInt(blockData);
                        }
                        catch (NumberFormatException e) {
                            continue;
                        }
                        String block = MaterialUtils.getBlockDataString(blockDataId);
                        if (block.length() > 0) {
                            blockDataArray.add(block);
                        }
                    }

                    if (blockDataArray.isEmpty()) {
                        result = "";
                    }
                    else if (material != null && (material == Material.PAINTING || BukkitAdapter.ADAPTER.isItemFrame(material))) {
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

    private static boolean isAsciiDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public static BlockData parseBlockDataFromString(String blockDataString) {
        if (blockDataString == null || blockDataString.isEmpty()) {
            return null;
        }

        BlockData blockData = BlockTypeUtils.createBlockDataFromString(blockDataString);
        if (blockData != null) {
            return blockData;
        }

        Server server = Bukkit.getServer();
        if (server != null) {
            try {
                return server.createBlockData(blockDataString);
            }
            catch (IllegalArgumentException ignored) {
            }
        }

        String states = BlockTypeUtils.getBlockDataStates(blockDataString);
        if (states == null || states.length() <= 2) {
            return null;
        }

        String key = BlockTypeUtils.getBlockDataKey(blockDataString);
        if (key.isEmpty()) {
            return null;
        }

        BlockData baseData = BlockTypeUtils.createBlockData(key);
        if (baseData == null && server != null) {
            try {
                baseData = server.createBlockData(key);
            }
            catch (IllegalArgumentException ignored) {
            }
        }
        if (baseData == null) {
            return null;
        }

        String properties = states.substring(1, states.length() - 1);
        for (String property : properties.split(",")) {
            String trimmedProperty = property.trim();
            if (trimmedProperty.isEmpty()) {
                continue;
            }
            BlockData propertyData = createPropertyBlockData(server, key + "[" + trimmedProperty + "]");
            if (propertyData != null) {
                try {
                    baseData = baseData.merge(propertyData);
                }
                catch (IllegalArgumentException ignored) {
                }
            }
        }
        return baseData;
    }

    private static BlockData createPropertyBlockData(Server server, String propertyBlockData) {
        if (BukkitAdapter.ADAPTER != null) {
            BlockData adapterData = BukkitAdapter.ADAPTER.createBlockDataFromString(propertyBlockData);
            if (adapterData != null) {
                return adapterData;
            }
        }
        if (server != null) {
            try {
                return server.createBlockData(propertyBlockData);
            }
            catch (IllegalArgumentException ignored) {
            }
        }
        return null;
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

    public static boolean isSkull(Material type) {
        if (type == null) {
            return false;
        }
        switch (type.name()) {
            case "SKELETON_SKULL":
            case "SKELETON_WALL_SKULL":
            case "WITHER_SKELETON_SKULL":
            case "WITHER_SKELETON_WALL_SKULL":
            case "ZOMBIE_HEAD":
            case "ZOMBIE_WALL_HEAD":
            case "PLAYER_HEAD":
            case "PLAYER_WALL_HEAD":
            case "CREEPER_HEAD":
            case "CREEPER_WALL_HEAD":
            case "DRAGON_HEAD":
            case "DRAGON_WALL_HEAD":
            case "PIGLIN_HEAD":
            case "PIGLIN_WALL_HEAD":
                return true;
            default:
                return false;
        }
    }

    public static boolean solidBlock(Material type) {
        return type.isSolid();
    }

    public static boolean passableBlock(Block block) {
        return block.isPassable();
    }

    public static boolean iceBreakCheck(BlockState block, String user, Material type) {
        if (type.equals(Material.ICE)) { // Ice block
            CacheHandler.recordBlockSource(block.getWorld(), block.getX(), block.getY(), block.getZ(), user, Material.WATER);
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
                if (singleBlock && blockState instanceof org.bukkit.block.Chest) {
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
        if (block instanceof WorldEditBlockState) {
            return ((WorldEditBlockState) block).getCapturedMetadata();
        }

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
