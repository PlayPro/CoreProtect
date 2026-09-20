package net.coreprotect.database.rollback;

import net.coreprotect.utility.BlockTypeUtils;
import net.coreprotect.utility.BlockUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RollbackBlockDataCache {

    private final Map<String, Optional<BlockData>> parsedBlockData = new ConcurrentHashMap<>();
    private final Map<Integer, Optional<BlockData>> defaultBlockData = new ConcurrentHashMap<>();

    BlockData getParsedBlockData(String blockDataString) {
        if (blockDataString == null || !blockDataString.contains(":")) {
            return null;
        }

        return cloneBlockData(parsedBlockData.computeIfAbsent(blockDataString, RollbackBlockDataCache::parseBlockData).orElse(null));
    }

    BlockData getDefaultBlockData(int rowTypeRaw) {
        return cloneBlockData(defaultBlockData.computeIfAbsent(rowTypeRaw, type -> Optional.ofNullable(BlockUtils.createBlockData(type))).orElse(null));
    }

    private static Optional<BlockData> parseBlockData(String blockDataString) {
        BlockData blockData = null;
        try {
            blockData = BlockTypeUtils.createBlockDataFromString(blockDataString);
            if (blockData == null) {
                blockData = Bukkit.getServer().createBlockData(blockDataString);
            }
        } catch (Exception e) {
            // corrupt BlockData, let the server automatically set the BlockData instead
        }
        return Optional.ofNullable(blockData);
    }

    private static BlockData cloneBlockData(BlockData blockData) {
        return blockData != null ? blockData.clone() : null;
    }
}
