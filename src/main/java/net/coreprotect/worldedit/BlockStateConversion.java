package net.coreprotect.worldedit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.block.BlockState;

/**
 * The Bukkit conversions of one WorldEdit block state: its Material, a BlockData template and the BlockData string.
 * Converting a state builds a new BlockData each time and serializing it builds a new string, while an edit usually
 * repeats a few states many times, so the loggers convert each state once.
 *
 * All fields are final, so an instance is fully visible to any thread that reads a reference to it, however that
 * reference was published. The block state registry is fixed once the server is running, so the cache is bounded by
 * the number of block states.
 */
final class BlockStateConversion {

    private static final Map<BlockState, BlockStateConversion> CACHE = new ConcurrentHashMap<>();

    private final Material material;
    private final BlockData template;
    private final String dataString;

    private BlockStateConversion(BlockState state) {
        this.material = BukkitAdapter.adapt(state.getBlockType());
        this.template = BukkitAdapter.adapt(state);
        this.dataString = template.getAsString();
    }

    /**
     * WorldEdit keeps one instance per block state, so lookups normally match by identity.
     */
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

    Material getMaterial() {
        return material;
    }

    /**
     * The shared template, for reading only.
     */
    BlockData getTemplate() {
        return template;
    }

    /**
     * A copy of the template that the caller owns, since logging may modify it.
     */
    BlockData createBlockData() {
        return template.clone();
    }

    String getDataString() {
        return dataString;
    }
}
