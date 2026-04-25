package net.coreprotect.utility;

import java.util.Locale;

import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

import net.coreprotect.bukkit.BukkitAdapter;

public class BlockTypeUtils {

    private static final String NAMESPACE = "minecraft:";

    private BlockTypeUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }

        key = key.toLowerCase(Locale.ROOT).trim();
        if (key.length() > 0 && !key.contains(":")) {
            key = NAMESPACE + key;
        }

        return key;
    }

    public static String getBlockDataKey(String blockData) {
        if (blockData == null) {
            return "";
        }

        String key = blockData;
        int propertyIndex = key.indexOf('[');
        if (propertyIndex > -1) {
            key = key.substring(0, propertyIndex);
        }

        return normalizeKey(key);
    }

    public static String getBlockDataStates(String blockData) {
        if (blockData == null) {
            return null;
        }

        int propertyIndex = blockData.indexOf('[');
        if (propertyIndex == -1 || !blockData.endsWith("]")) {
            return null;
        }

        return blockData.substring(propertyIndex);
    }

    public static boolean hasBlockType(String key) {
        return BukkitAdapter.ADAPTER != null && BukkitAdapter.ADAPTER.hasBlockType(normalizeKey(key));
    }

    public static BlockData createBlockData(String key) {
        if (BukkitAdapter.ADAPTER == null) {
            return null;
        }

        return normalizeBlockData(BukkitAdapter.ADAPTER.createBlockData(normalizeKey(key)));
    }

    public static BlockData createBlockDataFromString(String blockData) {
        if (BukkitAdapter.ADAPTER == null) {
            return null;
        }

        return normalizeBlockData(BukkitAdapter.ADAPTER.createBlockDataFromString(blockData));
    }

    public static boolean isAir(String key) {
        key = normalizeKey(key);
        return "minecraft:air".equals(key) || "minecraft:cave_air".equals(key) || "minecraft:void_air".equals(key);
    }

    private static BlockData normalizeBlockData(BlockData blockData) {
        if (blockData == null) {
            return null;
        }

        if (blockData instanceof Waterlogged) {
            ((Waterlogged) blockData).setWaterlogged(false);
        }

        return blockData;
    }
}
