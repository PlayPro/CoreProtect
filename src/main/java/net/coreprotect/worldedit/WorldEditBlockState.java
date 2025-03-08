package net.coreprotect.worldedit;

import java.util.Collection;
import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

public final class WorldEditBlockState implements BlockState {

    protected Location location;
    protected Material material;
    protected BlockData blockData;

    public WorldEditBlockState(Location loc) {
        location = loc;
    }

    public WorldEditBlockState(Location loc, Material type, BlockData data) {
        location = loc;
        material = type;
        blockData = data;
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {

    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        return null;
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return false;
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {

    }

    @Override
    public Block getBlock() {
        return null;
    }

    @Override
    public MaterialData getData() {
        return null;
    }

    @Override
    public BlockData getBlockData() {
        return blockData;
    }

    @Override
    public Material getType() {
        return material;
    }

    @Override
    public byte getLightLevel() {
        return 0;
    }

    @Override
    public World getWorld() {
        return location.getWorld();
    }

    @Override
    public int getX() {
        return location.getBlockX();
    }

    @Override
    public int getY() {
        return location.getBlockY();
    }

    @Override
    public int getZ() {
        return location.getBlockZ();
    }

    @Override
    public Location getLocation() {
        return location;
    }

    @Override
    public Location getLocation(Location loc) {
        if (loc != null) {
            loc.setWorld(location.getWorld());
            loc.setX(location.getX());
            loc.setY(location.getY());
            loc.setZ(location.getZ());
            loc.setYaw(location.getYaw());
            loc.setPitch(location.getPitch());
        }

        return loc;
    }

    @Override
    public Chunk getChunk() {
        return null;
    }

    @Override
    public void setData(MaterialData data) {

    }

    @Override
    public void setBlockData(BlockData data) {
        blockData = data;
    }

    @Override
    public void setType(Material type) {
        material = type;
    }

    @Override
    public boolean update() {
        return false;
    }

    @Override
    public boolean update(boolean force) {
        return false;
    }

    @Override
    public boolean update(boolean force, boolean applyPhysics) {
        return false;
    }

    @Override
    public byte getRawData() {
        return 0;
    }

    @Override
    public void setRawData(byte data) {

    }

    @Override
    public boolean isPlaced() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public Collection<ItemStack> getDrops() {
        return null;
    }

    @Override
    public Collection<ItemStack> getDrops(ItemStack tool) {
        return null;
    }

    @Override
    public Collection<ItemStack> getDrops(ItemStack tool, Entity entity) {
        return null;
    }

    @Override
    public BlockState copy() {
        return null;
    }

    @Override
    public BlockState copy(Location location) {
        return null;
    }

}
