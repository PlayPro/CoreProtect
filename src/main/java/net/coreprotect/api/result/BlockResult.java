package net.coreprotect.api.result;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.BlockTypeUtils;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;

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
        this.username = username;
        this.world = world;
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
        if (actionId == LookupActions.ENTITY_KILL) {
            return null;
        }

        Material material = MaterialUtils.getType(type);
        if (material == null) {
            return null;
        }

        String typeName = material.name().toLowerCase(Locale.ROOT);
        return MaterialUtils.getType(StringUtils.nameFilter(typeName, data));
    }

    public EntityType getEntityType() {
        if (actionId != LookupActions.ENTITY_KILL) {
            return null;
        }

        if (type == 0) {
            return EntityType.PLAYER;
        }

        return EntityUtils.getEntityType(type);
    }

    public BlockData getBlockData() {
        if (actionId == LookupActions.ENTITY_KILL) {
            return null;
        }

        if (blockData == null || blockData.isEmpty()) {
            Material material = getType();
            if (material != null) {
                return material.createBlockData();
            }

            return BlockUtils.createBlockData(type);
        }

        BlockData result = BlockTypeUtils.createBlockDataFromString(blockData);
        return result != null ? result : Bukkit.getServer().createBlockData(blockData);
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
