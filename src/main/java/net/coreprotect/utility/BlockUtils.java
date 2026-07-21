package net.coreprotect.utility;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.coreprotect.utility.serialize.BannerData;
import net.coreprotect.utility.serialize.JsonSerialization;
import net.coreprotect.utility.serialize.SerializedBlockMeta;
import net.coreprotect.utility.serialize.SerializedItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Jukebox;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.PendingBlockChange;
import net.coreprotect.thread.Scheduler;

public class BlockUtils {

    private BlockUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static byte[] stringToByteData(String string, int type) {
        final String data = stringToStringData(string, type);
        return data == null ? null : data.getBytes(StandardCharsets.UTF_8);
    }

    public static @org.jspecify.annotations.Nullable String stringToStringData(String string, int type) {
        String result = null;
        if (string != null) {
            Material material = MaterialUtils.getType(type);
            String blockKey = MaterialUtils.getBlockName(type);
            if ((blockKey == null || blockKey.length() == 0) && material != null) {
                blockKey = material.getKey().toString();
            }
            if (blockKey == null || blockKey.length() == 0) {
                return result;
            }

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
                }
                string = String.join(",", blockDataArray);
            }
            else if (material != null && !string.contains(":") && (material == Material.PAINTING || BukkitAdapter.ADAPTER.isItemFrame(material))) {
                int id = MaterialUtils.getBlockdataId(string, true);
                if (id > -1) {
                    string = Integer.toString(id);
                }
                else {
                    return result;
                }
            }
            else {
                return result;
            }

            return string;
        }

        return result;
    }

    public static String unpackBlockData(String data, int type) {
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

            result = data;
            if (!result.isEmpty()) {
                if (result.matches("\\d+")) {
                    result = result + ",";
                }
                if (result.contains(",")) {
                    String[] blockDataSplit = result.split(",");
                    ArrayList<String> blockDataArray = new ArrayList<>();
                    for (String blockData : blockDataSplit) {
                        String block = MaterialUtils.getBlockDataString(Integer.parseInt(blockData));
                        if (!block.isEmpty()) {
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
        return type.isAir();
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
                if (singleBlock) {
                    List<Material> chests = new java.util.ArrayList<>(java.util.Arrays.asList(Material.CHEST, Material.TRAPPED_CHEST));
                    chests.addAll(BukkitAdapter.ADAPTER.copperChestMaterials());
                    Material type = blockState.getType();
                    if (chests.contains(type)) {
                        inventory = ((org.bukkit.block.Chest) blockState).getBlockInventory();
                    }
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

    public static Location getCanonicalContainerLocation(Location location, Inventory inventory) {
        Location fallback = toBlockLocation(location);
        try {
            if (inventory != null) {
                Location holderLocation = getInventoryHolderLocation(inventory.getHolder(), fallback);
                if (holderLocation != null) {
                    return holderLocation;
                }

                Location inventoryLocation = toBlockLocation(inventory.getLocation());
                if (inventoryLocation != null) {
                    return inventoryLocation;
                }
            }

            if (location != null && location.getWorld() != null) {
                BlockState state = location.getBlock().getState();
                if (state instanceof InventoryHolder) {
                    Inventory stateInventory = ((InventoryHolder) state).getInventory();
                    Location holderLocation = getInventoryHolderLocation(stateInventory.getHolder(), fallback);
                    if (holderLocation != null) {
                        return holderLocation;
                    }
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return fallback;
    }

    public static Location getCanonicalContainerLocation(Location location) {
        return getCanonicalContainerLocation(location, null);
    }

    public static ItemStack[] normalizeDoubleChestBreakContents(Location location, ItemStack[] contents) {
        if (location == null || location.getWorld() == null || contents == null || contents.length != 27) {
            return contents;
        }

        try {
            Block block = location.getBlock();
            BlockState state = block.getState();
            if (!(state instanceof org.bukkit.block.Chest)) {
                return contents;
            }

            Inventory inventory = ((org.bukkit.block.Chest) state).getInventory();
            if (!(inventory.getHolder() instanceof DoubleChest)) {
                return contents;
            }

            int offset = getDoubleChestSlotOffset(block, inventory);
            if (offset <= 0) {
                return contents;
            }

            ItemStack[] normalized = new ItemStack[54];
            for (int i = 0; i < contents.length; i++) {
                normalized[offset + i] = contents[i];
            }
            return normalized;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return contents;
    }

    public static int getDoubleChestSlotOffset(Block block, Inventory inventory) {
        if (block == null || inventory == null || !(inventory.getHolder() instanceof DoubleChest)) {
            return 0;
        }

        DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
        Location rightLocation = getInventoryHolderLocation(doubleChest.getRightSide(), block.getLocation());
        if (sameBlock(block.getLocation(), rightLocation)) {
            return 27;
        }

        return 0;
    }

    public static Block getRollbackContainerBlock(Block block) {
        if (block == null) {
            return null;
        }
        if (BlockGroup.CONTAINERS.contains(block.getType())) {
            return block;
        }
        if (!isChestLike(block.getType())) {
            for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[] { org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST }) {
                Block relative = block.getRelative(face);
                if (isChestLike(relative.getType())) {
                    return relative;
                }
            }
        }
        return null;
    }

    private static Location getInventoryHolderLocation(InventoryHolder holder, Location fallback) {
        if (holder instanceof DoubleChest) {
            return getCanonicalDoubleChestLocation((DoubleChest) holder, fallback);
        }
        if (holder instanceof BlockInventoryHolder) {
            return toBlockLocation(((BlockInventoryHolder) holder).getBlock().getLocation());
        }
        if (holder instanceof BlockState) {
            return toBlockLocation(((BlockState) holder).getLocation());
        }
        return null;
    }

    private static Location getCanonicalDoubleChestLocation(DoubleChest doubleChest, Location fallback) {
        Location left = getInventoryHolderLocation(doubleChest.getLeftSide(), fallback);
        Location right = getInventoryHolderLocation(doubleChest.getRightSide(), fallback);
        Location canonical = minBlockLocation(left, right);
        if (canonical != null) {
            return canonical;
        }
        Location doubleChestLocation = toBlockLocation(doubleChest.getLocation());
        return doubleChestLocation != null ? doubleChestLocation : toBlockLocation(fallback);
    }

    private static Location minBlockLocation(Location first, Location second) {
        if (first == null) {
            return second == null ? null : second.clone();
        }
        if (second == null) {
            return first.clone();
        }
        if (first.getWorld() == null || second.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            return first.clone();
        }
        if (second.getBlockX() < first.getBlockX()) {
            return second.clone();
        }
        if (second.getBlockX() > first.getBlockX()) {
            return first.clone();
        }
        if (second.getBlockZ() < first.getBlockZ()) {
            return second.clone();
        }
        if (second.getBlockZ() > first.getBlockZ()) {
            return first.clone();
        }
        return second.getBlockY() < first.getBlockY() ? second.clone() : first.clone();
    }

    private static Location toBlockLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static boolean sameBlock(Location first, Location second) {
        return first != null && second != null && first.getWorld() != null && first.getWorld().equals(second.getWorld()) && first.getBlockX() == second.getBlockX() && first.getBlockY() == second.getBlockY() && first.getBlockZ() == second.getBlockZ();
    }

    private static boolean isChestLike(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || BukkitAdapter.ADAPTER.isCopperChest(type);
    }

    public static SerializedBlockMeta processMeta(BlockState block) {
        if (block instanceof CommandBlock commandBlock) {
            String command = commandBlock.getCommand();
            if (!command.isEmpty()) {
                return new SerializedBlockMeta(command, null, null);
            }
        }
        else if (block instanceof Banner banner) {
            return new SerializedBlockMeta(null, null, new BannerData(banner.getBaseColor(), banner.getPatterns()));
        }
        else if (block instanceof ShulkerBox shulkerBox) {
            ItemStack[] inventory = shulkerBox.getSnapshotInventory().getStorageContents();
            List<SerializedItem> items = new ArrayList<>();

            int slot = 0;
            for (ItemStack itemStack : inventory) {
                if (itemStack != null && !itemStack.isEmpty()) {
                    items.add(new SerializedItem(itemStack, slot, null));
                }
                slot++;
            }

            return new SerializedBlockMeta(null, items, null);
        }

        return null;
    }

    public static SerializedBlockMeta deserializeMeta(String metaJson) {
        return JsonSerialization.GSON.fromJson(metaJson, SerializedBlockMeta.class);
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
