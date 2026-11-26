package net.coreprotect.worldedit;

import java.util.Objects;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.RegionFunction;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;

public class CoreProtectLogger extends AbstractDelegateExtent {
    private final Actor eventActor;
    private final org.bukkit.World world;

    protected CoreProtectLogger(Actor actor, World world, Extent extent) {
        super(extent);
        this.eventActor = actor;
        this.world = BukkitAdapter.adapt(world);
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block) throws WorldEditException {
        Extent eventExtent = getExtent();
        Config config = Config.getConfig(world);
        if (!ConfigHandler.serverRunning || !config.WORLDEDIT) {
            return eventExtent.setBlock(position, block);
        }

        BlockState oldBlock = eventExtent.getBlock(position);
        BlockState newBlock = block.toImmutableState();
        CompoundTag newTile = block instanceof BaseBlock ? WorldEditTileData.read((BaseBlock) block) : null;
        if (oldBlock == newBlock && newTile == null) {
            return eventExtent.setBlock(position, block);
        }

        BlockStateConversion oldConversion = BlockStateConversion.of(oldBlock);
        BlockStateConversion newConversion = BlockStateConversion.of(newBlock);
        Material oldType = oldConversion.getMaterial();
        BlockData oldBlockData = oldConversion.getBlockData();
        Location location = new Location(world, position.getBlockX(), position.getBlockY(), position.getBlockZ());
        CompoundTag oldTile = newTile != null || WorldEditLogger.needsBaseBlock(oldType, config) ? WorldEditTileData.read(eventExtent.getFullBlock(position)) : null;
        org.bukkit.block.BlockState otherHalf = oldType != newConversion.getMaterial() || WorldEditLogger.isLoggedTopHalf(oldType, oldBlockData)
                ? WorldEditLogger.getOtherHalf(eventExtent, position, location, oldBlockData) : null;

        if (eventExtent.setBlock(position, block)) {
            WorldEditLogger.postProcess(eventActor, location, oldConversion, oldTile, newConversion, newTile, config, true, otherHalf);
            return true;
        }

        return false;
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(int x, int y, int z, T block) throws WorldEditException {
        return this.setBlock(BlockVector3.at(x, y, z), block);
    }

    @Override
    public int replaceBlocks(final Region region, final Mask mask, final Pattern pattern) throws MaxChangedBlocksException {
        Extent eventExtent = getExtent();
        if (!ConfigHandler.serverRunning || !Config.getConfig(world).WORLDEDIT) {
            return eventExtent.replaceBlocks(region, mask, pattern);
        }

        BlockReplace replace = new BlockReplace(this, pattern);
        Objects.requireNonNull(mask, "mask");
        return processBlocks(region, position -> mask.test(position) && replace.apply(position));
    }

    @Override
    public <B extends BlockStateHolder<B>> int setBlocks(Region region, B block) throws MaxChangedBlocksException {
        return this.setBlocks(region, (Pattern) block);
    }

    @Override
    public int setBlocks(Region region, Pattern pattern) throws MaxChangedBlocksException {
        Extent eventExtent = getExtent();
        if (!ConfigHandler.serverRunning || !Config.getConfig(world).WORLDEDIT) {
            return eventExtent.setBlocks(region, pattern);
        }

        return processBlocks(region, new BlockReplace(this, pattern));
    }

    @Override
    public int setBlocks(Set<BlockVector3> vset, Pattern pattern) {
        Extent eventExtent = getExtent();
        if (!ConfigHandler.serverRunning || !Config.getConfig(world).WORLDEDIT) {
            return eventExtent.setBlocks(vset, pattern);
        }

        int count = 0;
        for (BlockVector3 position : vset) {
            if (pattern.apply(this, position, position)) {
                count++;
            }
        }
        return count;
    }

    private int processBlocks(Region region, RegionFunction function) {
        RegionVisitor visitor = new RegionVisitor(region, function);
        Operations.completeLegacy(visitor);
        return visitor.getAffected();
    }
}
