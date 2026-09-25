package net.coreprotect.worldedit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.fastasyncworldedit.core.extent.processor.ProcessorScope;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypesCache;

import net.coreprotect.config.Config;
import net.coreprotect.model.BlockGroup;

final class FastAsyncWorldEditLogger implements IBatchProcessor {
    private static final ProcessorScope SCOPE = resolveScope();
    private final Actor actor;
    private final World eventWorld;
    private final Extent extent;
    private final org.bukkit.World world;

    FastAsyncWorldEditLogger(Actor actor, World world, Extent extent) {
        this.actor = actor;
        this.eventWorld = world;
        this.extent = extent;
        this.world = BukkitAdapter.adapt(world);
    }

    static Extent wrap(Actor actor, World world, Extent extent) {
        return extent.addProcessor(new FastAsyncWorldEditLogger(actor, world, extent));
    }

    @Override
    public IChunkSet processSet(IChunk chunk, IChunkGet get, IChunkSet set) {
        Config config = Config.getConfig(world);
        if (!config.WORLDEDIT) {
            return set;
        }

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

                int oldOrdinal = original == null ? BlockTypesCache.ReservedIDs.AIR : original[index];
                if (oldOrdinal == BlockTypesCache.ReservedIDs.__RESERVED__) {
                    oldOrdinal = BlockTypesCache.ReservedIDs.AIR;
                }
                if (oldOrdinal == newOrdinal) {
                    continue;
                }

                int x = index & 15;
                int y = (layer << 4) + (index >> 8);
                int z = (index >> 4) & 15;
                BlockState oldBlock = BlockState.getFromOrdinal(oldOrdinal);
                BlockState newBlock = BlockState.getFromOrdinal(newOrdinal);
                Material oldType = BukkitAdapter.adapt(oldBlock.getBlockType());
                BlockVector3 position = BlockVector3.at(chunkX + x, y, chunkZ + z);
                Location location = new Location(world, chunkX + x, y, chunkZ + z);
                BaseBlock baseBlock = WorldEditLogger.needsBaseBlock(oldType, config) ? get.getFullBlock(x, y, z) : null;
                org.bukkit.block.BlockState lowerHalf = BlockGroup.LOGGED_BY_LOWER_HALF.contains(oldType) ? getLowerHalf(get, set, x, y, z, chunkX + x, chunkZ + z) : null;
                WorldEditLogger.postProcess(extent, actor, position, location, newBlock, baseBlock, oldType, oldBlock, null, false, lowerHalf);
            }
        }
        return set;
    }

    /**
     * The block below as this edit leaves it, read from the chunk data the processor already holds instead of the world.
     * Null when the edit changes that block too, since its own row then covers the double block.
     */
    private org.bukkit.block.BlockState getLowerHalf(IChunkGet get, IChunkSet set, int x, int y, int z, int worldX, int worldZ) {
        int lowerY = y - 1;
        int layer = lowerY >> 4;
        if (layer < get.getMinSectionPosition()) {
            return null;
        }

        int index = ((lowerY & 15) << 8) | (z << 4) | x;
        char[] original = get.load(layer);
        int ordinal = original == null ? BlockTypesCache.ReservedIDs.AIR : original[index];
        if (ordinal == BlockTypesCache.ReservedIDs.__RESERVED__) {
            ordinal = BlockTypesCache.ReservedIDs.AIR;
        }
        char[] changed = set.loadIfPresent(layer);
        if (changed != null && changed[index] != BlockTypesCache.ReservedIDs.__RESERVED__ && changed[index] != ordinal) {
            return null;
        }

        BlockData blockData = BukkitAdapter.adapt(BlockState.getFromOrdinal(ordinal));
        return new WorldEditBlockState(new Location(world, worldX, lowerY, worldZ), blockData.getMaterial(), blockData);
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
