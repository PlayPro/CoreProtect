package net.coreprotect.utility;

import org.bukkit.Location;

import java.util.UUID;

public final class TransactionId {

    private final long worldMost;
    private final long worldLeast;
    private final int x;
    private final int y;
    private final int z;
    private final int hash;

    private TransactionId(long worldMost, long worldLeast, int x, int y, int z) {
        this.worldMost = worldMost;
        this.worldLeast = worldLeast;
        this.x = x;
        this.y = y;
        this.z = z;

        int result = Long.hashCode(worldMost);
        result = 31 * result + Long.hashCode(worldLeast);
        result = 31 * result + x;
        result = 31 * result + y;
        result = 31 * result + z;
        this.hash = result;
    }

    public static TransactionId of(Location location) {
        UUID world = location.getWorld().getUID();
        return new TransactionId(world.getMostSignificantBits(), world.getLeastSignificantBits(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TransactionId)) {
            return false;
        }

        TransactionId other = (TransactionId) object;
        return x == other.x && y == other.y && z == other.z && worldMost == other.worldMost && worldLeast == other.worldLeast;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return new UUID(worldMost, worldLeast) + "." + x + "." + y + "." + z;
    }
}
