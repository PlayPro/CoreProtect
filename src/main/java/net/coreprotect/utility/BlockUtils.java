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
import org.bukkit.block.Jukebox;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
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

    public static void prepareTypeAndData(Map<Location, BlockData> map, Block block, Material type, BlockData blockData, boolean update) {
        if (blockData == null) {
            blockData = createBlockData(type);
        }
        if (blockData == null) {
            return;
        }

        if (!update) {
            setTypeAndData(block, type, blockData, update);
            map.remove(block.getLocation());
        }
        else {
            map.put(block.getLocation(), blockData);
        }
    }

    public static void setTypeAndData(Block block, Material type, BlockData blockData, boolean update) {
        if (blockData == null && type != null) {
            blockData = createBlockData(type);
        }

        if (blockData != null) {
            block.setBlockData(blockData, update);
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
                e.printStackTrace();
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
            e.printStackTrace();
        }
        return inventory;
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
            e.printStackTrace();
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
