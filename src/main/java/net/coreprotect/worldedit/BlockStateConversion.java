package net.coreprotect.worldedit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.block.BlockState;

final class BlockStateConversion {

    private static final Map<BlockState, BlockStateConversion> CACHE = new ConcurrentHashMap<>();

    private final BlockState state;
    private final Material material;
    private final BlockData blockData;
    private final String dataString;

    private BlockStateConversion(BlockState state) {
        this.state = state;
        this.blockData = BukkitAdapter.adapt(state);
        this.material = blockData.getMaterial();
        this.dataString = blockData.getAsString();
    }

    static BlockStateConversion of(BlockState state) {
        BlockStateConversion conversion = CACHE.get(state);
        if (conversion == null) {
            conversion = new BlockStateConversion(state);
            BlockStateConversion existing = CACHE.putIfAbsent(state, conversion);
            if (existing != null) {
                conversion = existing;
            }
        }
        return conversion;
    }

    BlockState getState() {
        return state;
    }

    Material getMaterial() {
        return material;
    }

    BlockData getBlockData() {
        return blockData;
    }

    String getDataString() {
        return dataString;
    }
}
