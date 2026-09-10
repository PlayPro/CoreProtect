package net.coreprotect.api.result;

import org.bukkit.Material;

import net.coreprotect.model.item.ItemTransactionActions;
import net.coreprotect.utility.MaterialUtils;

/**
 * Represents a logged world item transaction.
 */
public class ItemResult implements CoreProtectResult {
    private final long time;
    private final String username;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int type;
    private final int amount;
    private final byte[] metadata;
    private final int actionId;
    private final int rolledBack;

    public ItemResult(long time, String username, String world, int x, int y, int z, int type, int amount, byte[] metadata, int actionId, int rolledBack) {
        this.time = time;
        this.username = username;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.amount = amount;
        this.metadata = metadata != null ? metadata.clone() : null;
        this.actionId = actionId;
        this.rolledBack = rolledBack;
    }

    public int getActionId() {
        return actionId;
    }

    public String getActionString() {
        return ItemTransactionActions.getActionString(actionId);
    }

    public int getAmount() {
        return amount;
    }

    public byte[] getMetadata() {
        return metadata != null ? metadata.clone() : null;
    }

    public String getPlayer() {
        return username;
    }

    public long getTimestamp() {
        return time * 1000L;
    }

    public Material getType() {
        return MaterialUtils.getType(type);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public boolean isRolledBack() {
        return rolledBack == 1 || rolledBack == 3;
    }

    public String worldName() {
        return world;
    }
}
