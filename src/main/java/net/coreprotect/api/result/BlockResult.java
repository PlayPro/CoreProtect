package net.coreprotect.api.result;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

import net.coreprotect.model.action.LookupActions;

/**
 * Represents a logged block action.
 */
public class BlockResult implements CoreProtectResult {
    private final long time;
    private final String username;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int type;
    private final int data;
    private final String blockData;
    private final int actionId;
    private final int rolledBack;

    public BlockResult(long time, String username, String world, int x, int y, int z, int type, int data, String blockData, int actionId, int rolledBack) {
        this.time = time;
        this.username = username == null ? "" : username;
        this.world = world == null ? "" : world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.data = data;
        this.blockData = blockData;
        this.actionId = actionId;
        this.rolledBack = rolledBack;
    }

    public int getActionId() {
        return actionId;
    }

    public String getActionString() {
        return LookupActions.getActionString(actionId);
    }

    @Deprecated
    public int getData() {
        return data;
    }

    public String getPlayer() {
        return username;
    }

    public long getTimestamp() {
        return time * 1000L;
    }

    public Material getType() {
        if (isEntityAction()) {
            return null;
        }

        return BlockResultValues.material(type, data);
    }

    public EntityType getEntityType() {
        if (!isEntityAction()) {
            return null;
        }

        return BlockResultValues.entityType(type, actionId);
    }

    public BlockData getBlockData() {
        if (isEntityAction()) {
            return null;
        }

        return BlockResultValues.blockData(type, blockData, this::getType);
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
        return BlockResultValues.isRolledBack(rolledBack);
    }

    private boolean isEntityAction() {
        return actionId == LookupActions.ENTITY_KILL || actionId == LookupActions.ENTITY_SPAWN;
    }

    public String worldName() {
        return world;
    }
}
