package net.coreprotect.worldedit;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.fastasyncworldedit.core.extent.processor.ProcessorScope;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypesCache;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;

final class FastAsyncWorldEditLogger implements IBatchProcessor {
    private static final int ORDINAL_CACHE_SIZE = Character.MAX_VALUE + 1;
    private static final ProcessorScope SCOPE = resolveScope();
    private static volatile BlockStateConversion[] conversions;
    private final Actor actor;
    private final World eventWorld;
    private final org.bukkit.World world;

    FastAsyncWorldEditLogger(Actor actor, World world) {
        this.actor = actor;
        this.eventWorld = world;
        this.world = BukkitAdapter.adapt(world);
    }

    static Extent wrap(Actor actor, World world, Extent extent) {
        return extent.addProcessor(new FastAsyncWorldEditLogger(actor, world));
    }

    @Override
    public IChunkSet processSet(IChunk chunk, IChunkGet get, IChunkSet set) {
        Config config = Config.getConfig(world);
        if (!ConfigHandler.serverRunning || !config.WORLDEDIT) {
            return set;
        }

        BlockStateConversion[] cached = conversions();
        Map<BlockVector3, CompoundTag> tiles = set.getTiles();
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;
        for (int layer = get.getMinSectionPosition(); layer <= get.getMaxSectionPosition(); layer++) {
            char[] changed = set.loadIfPresent(layer);
            if (changed == null) {
                continue;
            }

            char[] original = get.load(layer);
            for (int index = 0; index < changed.length; index++) {
                int newOrdinal = changed[index];
                if (newOrdinal == BlockTypesCache.ReservedIDs.__RESERVED__) {
                    continue;
                }

                int oldOrdinal = originalOrdinal(original, index);
                if (oldOrdinal == newOrdinal) {
                    continue;
                }

                int x = index & 15;
                int y = (layer << 4) + (index >> 8);
                int z = (index >> 4) & 15;
                CompoundTag tile = tiles.isEmpty() ? null : tiles.get(BlockVector3.at(x, y, z));
                logChange(get, set, cached, chunkX, chunkZ, x, y, z, oldOrdinal, newOrdinal, tile, config);
            }
        }
        for (Map.Entry<BlockVector3, CompoundTag> entry : tiles.entrySet()) {
            BlockVector3 position = entry.getKey();
            int x = position.getBlockX();
            int y = position.getBlockY();
            int z = position.getBlockZ();
            int layer = y >> 4;
            if (layer < get.getMinSectionPosition() || layer > get.getMaxSectionPosition() || entry.getValue() == null) {
                continue;
            }
            int index = ((y & 15) << 8) | (z << 4) | x;
            int oldOrdinal = originalOrdinal(get.load(layer), index);
            char[] changed = set.loadIfPresent(layer);
            if ((changed == null || changed[index] == BlockTypesCache.ReservedIDs.__RESERVED__ || changed[index] == oldOrdinal)
                    && WorldEditLogger.needsBaseBlock(conversion(cached, oldOrdinal).getMaterial(), config)) {
                logChange(get, set, cached, chunkX, chunkZ, x, y, z, oldOrdinal, oldOrdinal, entry.getValue(), config);
            }
        }
        return set;
    }

    private void logChange(IChunkGet get, IChunkSet set, BlockStateConversion[] cached, int chunkX, int chunkZ, int x, int y, int z, int oldOrdinal, int newOrdinal, CompoundTag newTile, Config config) {
        BlockStateConversion oldConversion = conversion(cached, oldOrdinal);
        BlockStateConversion newConversion = conversion(cached, newOrdinal);
        Material oldType = oldConversion.getMaterial();
        BlockData oldBlockData = oldConversion.getBlockData();
        Location location = new Location(world, chunkX + x, y, chunkZ + z);
        CompoundTag oldTile = WorldEditLogger.needsBaseBlock(oldType, config) ? WorldEditTileData.read(get.getFullBlock(x, y, z)) : null;
        org.bukkit.block.BlockState lowerHalf = WorldEditLogger.isLoggedTopHalf(oldType, oldBlockData) ? getLowerHalf(get, set, cached, x, y, z, chunkX + x, chunkZ + z, oldBlockData) : null;
        WorldEditLogger.postProcess(actor, location, oldConversion, oldTile, newConversion, newTile, config, false, lowerHalf);
    }

    private static int originalOrdinal(char[] original, int index) {
        int ordinal = original == null ? BlockTypesCache.ReservedIDs.AIR : original[index];
        return ordinal == BlockTypesCache.ReservedIDs.__RESERVED__ ? BlockTypesCache.ReservedIDs.AIR : ordinal;
    }

    /**
     * The matching lower half of a top half as this edit leaves it, read from the chunk data the processor already holds instead of the world.
     * Null when the block below is not that lower half, or when the edit changes it too, since its own row then covers the double block.
     */
    private org.bukkit.block.BlockState getLowerHalf(IChunkGet get, IChunkSet set, BlockStateConversion[] cached, int x, int y, int z, int worldX, int worldZ, BlockData topHalf) {
        int lowerY = y - 1;
        int layer = lowerY >> 4;
        if (layer < get.getMinSectionPosition()) {
            return null;
        }

        int index = ((lowerY & 15) << 8) | (z << 4) | x;
        int ordinal = originalOrdinal(get.load(layer), index);
        char[] changed = set.loadIfPresent(layer);
        if (changed != null && changed[index] != BlockTypesCache.ReservedIDs.__RESERVED__ && changed[index] != ordinal) {
            return null;
        }

        BlockStateConversion conversion = conversion(cached, ordinal);
        if (!WorldEditLogger.isOtherHalf(topHalf, conversion.getBlockData())) {
            return null;
        }

        return new WorldEditBlockState(new Location(world, worldX, lowerY, worldZ), conversion.getMaterial(), conversion.getBlockData());
    }

    private static BlockStateConversion[] conversions() {
        BlockStateConversion[] cached = conversions;
        if (cached == null) {
            cached = new BlockStateConversion[ORDINAL_CACHE_SIZE];
            conversions = cached;
        }
        return cached;
    }

    private static BlockStateConversion conversion(BlockStateConversion[] cached, int ordinal) {
        BlockState state = BlockState.getFromOrdinal(ordinal);
        BlockStateConversion conversion = cached[ordinal];
        if (conversion == null || conversion.getState() != state) {
            conversion = BlockStateConversion.of(state);
            cached[ordinal] = conversion;
        }
        return conversion;
    }

    @Override
    public Extent construct(Extent child) {
        return new CoreProtectLogger(actor, eventWorld, child);
    }

    @Override
    public ProcessorScope getScope() {
        return SCOPE;
    }

    private static ProcessorScope resolveScope() {
        try {
            return ProcessorScope.valueOf("READING_BLOCKS");
        }
        catch (IllegalArgumentException exception) {
            return ProcessorScope.valueOf("READING_SET_BLOCKS");
        }
    }
}
