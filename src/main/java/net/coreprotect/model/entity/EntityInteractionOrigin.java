package net.coreprotect.model.entity;

public final class EntityInteractionOrigin {

    private final int worldId;
    private final double x;
    private final double y;
    private final double z;

    public EntityInteractionOrigin(int worldId, double x, double y, double z) {
        if (worldId < 0) {
            throw new IllegalArgumentException("Invalid entity interaction origin");
        }
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
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
}
