package net.coreprotect.model.entity;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import net.coreprotect.utility.WorldUtils;

public final class EntitySpawnRecord {

    private final int rowId;
    private final int killRowId;
    private final UUID uuid;
    private final int originalWorldId;
    private final double originalX;
    private final double originalY;
    private final double originalZ;
    private final int worldId;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final boolean removed;
    private final List<Object> state;

    public EntitySpawnRecord(int rowId, int killRowId, UUID uuid, int originalWorldId, double originalX, double originalY, double originalZ, int worldId, double x, double y, double z, float yaw, float pitch, boolean removed, List<Object> state) {
        this.rowId = rowId;
        this.killRowId = killRowId;
        this.uuid = uuid;
        this.originalWorldId = originalWorldId;
        this.originalX = originalX;
        this.originalY = originalY;
        this.originalZ = originalZ;
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.removed = removed;
        this.state = state;
    }

    public int getRowId() {
        return rowId;
    }

    public int getKillRowId() {
        return killRowId;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getOriginalWorldId() {
        return originalWorldId;
    }

    public double getOriginalX() {
        return originalX;
    }

    public double getOriginalY() {
        return originalY;
    }

    public double getOriginalZ() {
        return originalZ;
    }

    public int getWorldId() {
        return worldId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public boolean isRemoved() {
        return removed;
    }

    public List<Object> getState() {
        return state;
    }

    public Location getLocation() {
        World world = Bukkit.getWorld(WorldUtils.getWorldName(worldId));
        return world == null ? null : new Location(world, x, y, z, yaw, pitch);
    }

    public Location getOriginalLocation() {
        World world = Bukkit.getWorld(WorldUtils.getWorldName(originalWorldId));
        return world == null ? null : new Location(world, originalX, originalY, originalZ);
    }
}
