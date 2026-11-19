package net.coreprotect.api.result;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

import net.coreprotect.api.SessionLookup;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.action.SessionActions;
import net.coreprotect.model.item.InventorySources;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;

public class ParseResult implements CoreProtectResult {
    private final String[] parse;

    public ParseResult(String[] data) {
        parse = data == null ? new String[0] : data.clone();
    }

    public int getActionId() {
        return isUsernameRow() ? LookupActions.USERNAME : getInt(7, -1);
    }

    public String getActionString() {
        int actionID = getActionId();
        if (parse.length < 13 && actionID >= 0 && getInt(6, -1) == SessionLookup.ID) {
            return SessionActions.getActionString(actionID);
        }

        return LookupActions.getActionString(actionID);
    }

    @Deprecated
    public int getData() {
        return getInt(6, 0);
    }

    public String getPlayer() {
        String player = getValue(isUsernameRow() ? 2 : 1);
        return player == null ? "" : player;
    }

    @Deprecated
    public int getTime() {
        return getInt(0, 0);
    }

    public long getTimestamp() {
        try {
            return Math.multiplyExact(Long.parseLong(getValue(0)), 1000L);
        }
        catch (NumberFormatException | ArithmeticException ignored) {
            return 0L;
        }
    }

    public Material getType() {
        if (parse.length < 13) {
            return null;
        }

        int actionID = this.getActionId();
        if (isEntityAction(actionID)) {
            return null;
        }

        int type = getInt(5, -1);
        Material material = MaterialUtils.getType(type);
        if (material == null) {
            return null;
        }

        String typeName = material.name().toLowerCase(Locale.ROOT);
        typeName = StringUtils.nameFilter(typeName, this.getData());
        return MaterialUtils.getType(typeName);
    }

    public BlockData getBlockData() {
        if (parse.length < 13) {
            return null;
        }

        if (isEntityAction(this.getActionId())) {
            return null;
        }

        String blockData = parse[12];
        BlockData resolved = BlockUtils.parseBlockDataFromString(blockData);
        if (resolved != null) {
            return resolved;
        }

        Material type = getType();
        return type != null ? BlockUtils.createBlockData(type) : BlockUtils.createBlockData(getInt(5, -1));
    }

    public EntityType getEntityType() {
        if (parse.length < 13 || !isEntityAction(this.getActionId())) {
            return null;
        }

        int type = getInt(5, -1);
        if (this.getActionId() == LookupActions.ENTITY_KILL && type == 0) {
            return EntityType.PLAYER;
        }

        return EntityUtils.getEntityType(type);
    }

    public int getX() {
        return parse.length >= 8 ? getInt(2, 0) : 0;
    }

    public int getY() {
        return parse.length >= 8 ? getInt(3, 0) : 0;
    }

    public int getZ() {
        return parse.length >= 8 ? getInt(4, 0) : 0;
    }

    public boolean isRolledBack() {
        if (parse.length < 13) {
            return false;
        }

        int rolledBack = getInt(8, 0);
        return rolledBack == 1 || rolledBack == 3;
    }

    public String worldName() {
        int world = parse.length >= 8 ? getInt(parse.length < 13 ? 5 : 9, -1) : -1;
        return world < 0 ? "" : WorldUtils.getWorldName(world);
    }

    private boolean isEntityAction(int actionId) {
        if (actionId == LookupActions.ENTITY_KILL || actionId == LookupActions.ENTITY_SPAWN) {
            return true;
        }
        return actionId == LookupActions.INTERACTION && parse.length > 14 && getInt(13, -1) == InventorySources.ENTITY_INTERACTION;
    }

    private boolean isUsernameRow() {
        if (parse.length != 3 || parse[1] == null || parse[1].length() != 36) {
            return false;
        }
        try {
            UUID.fromString(parse[1]);
            return true;
        }
        catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String getValue(int index) {
        return index < parse.length ? parse[index] : null;
    }

    private int getInt(int index, int fallback) {
        String value = getValue(index);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            }
            catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
