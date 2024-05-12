package net.coreprotect.worldedit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import net.coreprotect.config.Config;
import net.coreprotect.utility.Util;

public class CoreProtectLogger extends AbstractDelegateExtent {
    private final Actor eventActor;
    private final World eventWorld;
    private final Extent eventExtent;

    protected CoreProtectLogger(Actor actor, World world, Extent extent) {
        super(extent);
        this.eventActor = actor;
        this.eventWorld = world;
        this.eventExtent = extent;
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block) throws WorldEditException {
        org.bukkit.World world = BukkitAdapter.adapt(eventWorld);
        if (!Config.getConfig(world).WORLDEDIT) {
            if (CoreProtectEditSessionEvent.isFAWE()) {
                return eventExtent.setBlock(position.getX(), position.getY(), position.getZ(), block);
            }
            else {
                return eventExtent.setBlock(position, block);
            }
        }

        BlockState oldBlock = eventExtent.getBlock(position);
        Material oldType = BukkitAdapter.adapt(oldBlock.getBlockType());
        Location location = new Location(world, position.getBlockX(), position.getBlockY(), position.getBlockZ());
        BaseBlock baseBlock = WorldEditLogger.getBaseBlock(eventExtent, position, location, oldType, oldBlock);

        // No clear way to get container content data from within the WorldEdit API
        // Data may be available by converting oldBlock.toBaseBlock().getNbtData()
        // e.g. BaseBlock block = eventWorld.getBlock(position);
        ItemStack[] containerData = CoreProtectEditSessionEvent.isFAWE() ? null : Util.getContainerContents(oldType, null, location);

        if (CoreProtectEditSessionEvent.isFAWE()) {
            if (eventExtent.setBlock(position.getX(), position.getY(), position.getZ(), block)) {
                WorldEditLogger.postProcess(eventExtent, eventActor, position, location, block, baseBlock, oldType, oldBlock, containerData);
                return true;
            }
        }
        else {
            if (eventExtent.setBlock(position, block)) {
                WorldEditLogger.postProcess(eventExtent, eventActor, position, location, block, baseBlock, oldType, oldBlock, containerData);
                return true;
            }
        }

        return false;
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(int x, int y, int z, T block) throws WorldEditException {
        return this.setBlock(BlockVector3.at(x, y, z), block);
    }

    @Override
    public int replaceBlocks(final Region region, final Mask mask, final Pattern pattern) throws MaxChangedBlocksException {
        org.bukkit.World world = BukkitAdapter.adapt(eventWorld);
        if (!Config.getConfig(world).WORLDEDIT) {
            return eventExtent.replaceBlocks(region, mask, pattern);
        }
        processPatternToBlocks(world, region, pattern);
        return eventExtent.replaceBlocks(region, mask, pattern);
    }

    @Override
    public int setBlocks(Region region, Pattern pattern) throws MaxChangedBlocksException {
        org.bukkit.World world = BukkitAdapter.adapt(eventWorld);
        if (!Config.getConfig(world).WORLDEDIT) {
            return eventExtent.setBlocks(region, pattern);
        }
        processPatternToBlocks(world, region, pattern);
        return eventExtent.setBlocks(region, pattern);
    }

    private void processPatternToBlocks(org.bukkit.World world, Region region, Pattern pattern) {
        for (BlockVector3 position : region.clone()) {
            BlockState oldBlock = eventExtent.getBlock(position);
            Material oldType = BukkitAdapter.adapt(oldBlock.getBlockType());
            Location location = new Location(world, position.getBlockX(), position.getBlockY(), position.getBlockZ());
            BaseBlock baseBlock = WorldEditLogger.getBaseBlock(eventExtent, position, location, oldType, oldBlock);

            // No clear way to get container content data from within the WorldEdit API
            // Data may be available by converting oldBlock.toBaseBlock().getNbtData()
            // e.g. BaseBlock block = eventWorld.getBlock(position);
            ItemStack[] containerData = CoreProtectEditSessionEvent.isFAWE() ? null : Util.getContainerContents(oldType, null, location);
            WorldEditLogger.postProcess(eventExtent, eventActor, position, location, pattern.applyBlock(position), baseBlock, oldType, oldBlock, containerData);
        }
    }
}
