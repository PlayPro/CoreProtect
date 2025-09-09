package net.coreprotect.api.result;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import net.coreprotect.api.SessionLookup;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;

public class ParseResult {
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
            switch (actionID) {
                case 0:
                    return "logout";
                case 1:
                    return "login";
                default:
                    return "unknown";
            }
        }

        String result = "unknown";
        if (actionID == 0) {
            result = "break";
        }
        else if (actionID == 1) {
            result = "place";
        }
        else if (actionID == 2) {
            result = "click";
        }
        else if (actionID == 3) {
            result = "kill";
        }

        return result;
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
        int type = Integer.parseInt(parse[5]);
        String typeName;

        if (actionID == 3) {
            typeName = EntityUtils.getEntityType(type).name();
        }
        else {
            typeName = MaterialUtils.getType(type).name().toLowerCase(Locale.ROOT);
            typeName = StringUtils.nameFilter(typeName, this.getData());
        }

        return MaterialUtils.getType(typeName);
    }

    public BlockData getBlockData() {
        if (parse.length < 13) {
            return null;
        }

        String blockData = parse[12];
        if (blockData == null || blockData.length() == 0) {
            return getType().createBlockData();
        }
        return Bukkit.getServer().createBlockData(blockData);
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
}
