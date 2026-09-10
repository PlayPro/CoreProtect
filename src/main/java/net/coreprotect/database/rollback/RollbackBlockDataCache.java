package net.coreprotect.database.rollback;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import net.coreprotect.utility.BlockTypeUtils;
import net.coreprotect.utility.BlockUtils;

public final class RollbackBlockDataCache {

    private final Map<String, BlockData> parsedBlockData = new HashMap<>();
    private final Map<Integer, BlockData> defaultBlockData = new HashMap<>();

    BlockData getParsedBlockData(String blockDataString) {
        if (blockDataString == null || !blockDataString.contains(":")) {
            return null;
        }

        if (!parsedBlockData.containsKey(blockDataString)) {
            BlockData blockData = null;
            try {
                blockData = BlockTypeUtils.createBlockDataFromString(blockDataString);
                if (blockData == null) {
                    blockData = Bukkit.getServer().createBlockData(blockDataString);
                }
            }
            catch (Exception e) {
                // corrupt BlockData, let the server automatically set the BlockData instead
            }
            parsedBlockData.put(blockDataString, blockData);
        }

        return cloneBlockData(parsedBlockData.get(blockDataString));
    }

    BlockData getDefaultBlockData(int rowTypeRaw) {
        if (!defaultBlockData.containsKey(rowTypeRaw)) {
            defaultBlockData.put(rowTypeRaw, BlockUtils.createBlockData(rowTypeRaw));
        }

        return cloneBlockData(defaultBlockData.get(rowTypeRaw));
    }

    private static BlockData cloneBlockData(BlockData blockData) {
        return blockData != null ? blockData.clone() : null;
    }
}
