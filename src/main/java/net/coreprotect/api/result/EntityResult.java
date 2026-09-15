package net.coreprotect.api.result;

import org.bukkit.entity.EntityType;

import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.EntityUtils;

/**
 * Represents a logged entity spawn or kill at its original event location.
 */
public class EntityResult implements CoreProtectResult {
    private final long time;
    private final String username;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int type;
    private final int actionId;
    private final int rolledBack;

    public EntityResult(long time, String username, String world, int x, int y, int z, int type, int actionId, int rolledBack) {
        this.time = time;
        this.username = username;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.actionId = actionId;
        this.rolledBack = rolledBack;
    }

    public int getActionId() {
        return actionId;
    }

    public String getActionString() {
        return LookupActions.getActionString(actionId);
    }

    public String getPlayer() {
        return username;
    }

    public long getTimestamp() {
        return time * 1000L;
    }

    public EntityType getEntityType() {
        if (actionId == LookupActions.ENTITY_KILL && type == 0) {
            return EntityType.PLAYER;
        }
        return EntityUtils.getEntityType(type);
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
