package net.coreprotect.api.results;

import net.coreprotect.utility.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Locale;

public abstract class ParseResult {

    protected final String[] parse;

    protected ParseResult(String[] data) {
        parse = data;
    }

    public int getActionId() {
        return Integer.parseInt(parse[7]);
    }

    public String getActionString() {
        String res;
        switch(getActionId()) {
            case 0:
                res = "break";
                break;
            case 1:
                res = "place";
                break;
            case 2:
                res = "click";
                break;
            case 3:
                res = "kill";
                break;
            default:
                res = "unknown";
        }
        return res;
    }

    public int getData() {
        return Integer.parseInt(parse[6]);
    }

    @Deprecated
    public String getPlayer() {
        return getEntity();
    }

    public String getEntity() {
        return parse[1];
    }

    @Deprecated
    public int getTime() {
        return Integer.parseInt(parse[0]);
    }

    public long getTimeLong() {
        return Long.parseLong(parse[0]);
    }

    public long getTimestamp() {
        return getTimeLong() * 1000L;
    }

    public Material getType() {
        int actionID = this.getActionId();
        int type = Integer.parseInt(parse[5]);
        String typeName;

        if (actionID == 3) {
            typeName = Util.getEntityType(type).name();
        } else {
            typeName = Util.getType(type).name().toLowerCase(Locale.ROOT);
            typeName = Util.nameFilter(typeName, this.getData());
        }

        return Util.getType(typeName);
    }

    public Block getBlock() {
        return getLocation().getBlock();
    }

    public boolean isRolledBack() {
        return Integer.parseInt(parse[8]) == 1;
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

    public Location getLocation() {
        return new Location(getWorld(), getX(), getY(), getZ());
    }

    public String getWorldName() {
        return Util.getWorldName(Integer.parseInt(parse[9]));
    }

    public World getWorld() {
        return Bukkit.getWorld(getWorldName());
    }

}
