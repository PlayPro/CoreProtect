package net.coreprotect.database.clickhouse;

import java.util.Objects;
import java.util.UUID;

public final class ClickHouseEntityState {

    private final ClickHouseEventPointer pointer;
    private final Long blockRowId;
    private final Integer killRowId;
    private final UUID uuid;
    private final int worldId;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final byte[] data;
    private final boolean removed;

    public ClickHouseEntityState(ClickHouseEventPointer pointer, Long blockRowId, Integer killRowId, UUID uuid, int worldId, double x, double y, double z, float yaw, float pitch, byte[] data, boolean removed) {
        this.pointer = Objects.requireNonNull(pointer, "pointer");
        if (pointer.getFamily() != ClickHouseFamily.ENTITY_SPAWN) {
            throw new IllegalArgumentException("ClickHouse entity state must target an entity_spawn event");
        }
        this.blockRowId = blockRowId;
        this.killRowId = killRowId;
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.data = data == null ? null : data.clone();
        this.removed = removed;
    }

    public ClickHouseEventPointer getPointer() {
        return pointer;
    }

    public Long getBlockRowId() {
        return blockRowId;
    }

    public Integer getKillRowId() {
        return killRowId;
    }

    public UUID getUuid() {
        return uuid;
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

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public byte[] getData() {
        return data == null ? null : data.clone();
    }

    public boolean isRemoved() {
        return removed;
    }

    ClickHouseEntityState withBlockRowId(Long value) {
        return copy(value, killRowId, uuid, worldId, x, y, z, yaw, pitch, data, removed);
    }

    ClickHouseEntityState withKillRowId(Integer value) {
        return copy(blockRowId, value, uuid, worldId, x, y, z, yaw, pitch, data, removed);
    }

    ClickHouseEntityState withUuid(UUID value) {
        return copy(blockRowId, killRowId, value, worldId, x, y, z, yaw, pitch, data, removed);
    }

    ClickHouseEntityState withLocation(int nextWorldId, double nextX, double nextY, double nextZ, float nextYaw, float nextPitch) {
        return copy(blockRowId, killRowId, uuid, nextWorldId, nextX, nextY, nextZ, nextYaw, nextPitch, data, removed);
    }

    ClickHouseEntityState withData(byte[] value) {
        return copy(blockRowId, killRowId, uuid, worldId, x, y, z, yaw, pitch, value, removed);
    }

    ClickHouseEntityState withRemoved(boolean value) {
        return copy(blockRowId, killRowId, uuid, worldId, x, y, z, yaw, pitch, data, value);
    }

    private ClickHouseEntityState copy(Long nextBlockRowId, Integer nextKillRowId, UUID nextUuid, int nextWorldId, double nextX, double nextY, double nextZ, float nextYaw, float nextPitch, byte[] nextData, boolean nextRemoved) {
        return new ClickHouseEntityState(pointer, nextBlockRowId, nextKillRowId, nextUuid, nextWorldId, nextX, nextY, nextZ, nextYaw, nextPitch, nextData, nextRemoved);
    }

}
