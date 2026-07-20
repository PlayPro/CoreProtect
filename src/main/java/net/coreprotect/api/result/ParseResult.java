package net.coreprotect.api.result;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

import net.coreprotect.api.SessionLookup;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.action.SessionActions;
import net.coreprotect.model.item.InventorySources;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.BlockTypeUtils;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;

public class ParseResult implements CoreProtectResult {
    private final String[] parse;

    public ParseResult(String[] data) {
        parse = data;
    }

    public int getActionId() {
        return Integer.parseInt(parse[7]);
    }

    public String getActionString() {
        int actionID = Integer.parseInt(parse[7]);
        if (parse.length < 13 && Integer.parseInt(parse[6]) == SessionLookup.ID) {
            return SessionActions.getActionString(actionID);
        }

        return LookupActions.getActionString(actionID);
    }

    @Deprecated
    public int getData() {
        return Integer.parseInt(parse[6]);
    }

    public String getPlayer() {
        return parse[1];
    }

    @Deprecated
    public int getTime() {
        return Integer.parseInt(parse[0]);
    }

    public long getTimestamp() {
        return Long.parseLong(parse[0]) * 1000L;
    }

    public Material getType() {
        if (parse.length < 13) {
            return null;
        }

        int actionID = this.getActionId();
        if (isEntityAction(actionID)) {
            return null;
        }

        int type = Integer.parseInt(parse[5]);
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
        if (blockData == null || blockData.length() == 0) {
            Material type = getType();
            if (type != null) {
                return type.createBlockData();
            }

            return BlockUtils.createBlockData(Integer.parseInt(parse[5]));
        }

        BlockData result = BlockTypeUtils.createBlockDataFromString(blockData);
        return result != null ? result : Bukkit.getServer().createBlockData(blockData);
    }

    public EntityType getEntityType() {
        if (parse.length < 13 || !isEntityAction(this.getActionId())) {
            return null;
        }

        int type = Integer.parseInt(parse[5]);
        if (this.getActionId() == LookupActions.ENTITY_KILL && type == 0) {
            return EntityType.PLAYER;
        }

        return EntityUtils.getEntityType(type);
    }

    public int getX() {
        return Integer.parseInt(parse[2]);
    }

    public int getY() {
        return Integer.parseInt(parse[3]);
    }

    public int getZ() {
        return Integer.parseInt(parse[4]);
    }

    public boolean isRolledBack() {
        if (parse.length < 13) {
            return false;
        }

        return (Integer.parseInt(parse[8]) == 1 || Integer.parseInt(parse[8]) == 3);
    }

    public String worldName() {
        return WorldUtils.getWorldName(Integer.parseInt(parse.length < 13 ? parse[5] : parse[9]));
    }

    private boolean isEntityAction(int actionId) {
        if (actionId == LookupActions.ENTITY_KILL || actionId == LookupActions.ENTITY_SPAWN) {
            return true;
        }
        return actionId == LookupActions.INTERACTION && parse.length > 14 && parse[13] != null && Integer.parseInt(parse[13]) == InventorySources.ENTITY_INTERACTION;
    }
}
