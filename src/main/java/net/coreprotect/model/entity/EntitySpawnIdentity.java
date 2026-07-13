package net.coreprotect.model.entity;

import java.util.UUID;

public final class EntitySpawnIdentity {

    private final int rowId;
    private final UUID uuid;
    private final int originalWorldId;
    private final int originalX;
    private final int originalY;
    private final int originalZ;

    public EntitySpawnIdentity(int rowId, UUID uuid, int originalWorldId, double originalX, double originalY, double originalZ) {
        this.rowId = rowId;
        this.uuid = uuid;
        this.originalWorldId = originalWorldId;
        this.originalX = (int) Math.floor(originalX);
        this.originalY = (int) Math.floor(originalY);
        this.originalZ = (int) Math.floor(originalZ);
    }

    public int getRowId() {
        return rowId;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getOriginalWorldId() {
        return originalWorldId;
    }

    public int getOriginalX() {
        return originalX;
    }

    public int getOriginalY() {
        return originalY;
    }

    public int getOriginalZ() {
        return originalZ;
    }
}
