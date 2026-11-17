package net.coreprotect.utility;

import org.bukkit.Bukkit;
import org.bukkit.World;

import net.coreprotect.config.ConfigHandler;

public class WorldUtils {

    private WorldUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static int getWorldId(String name) {
        if (ConfigHandler.databaseType.isClickHouse()) {
            return ConfigHandler.resolveIdentifierId(ConfigHandler.CacheType.WORLDS, name, true);
        }
        try {
            return ConfigHandler.resolveIdentifierId(ConfigHandler.CacheType.WORLDS, name, true);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return -1;
        }
    }

    public static String getWorldName(int id) {
        if (ConfigHandler.databaseType.isClickHouse()) {
            String name = ConfigHandler.getIdentifierValue(ConfigHandler.CacheType.WORLDS, id);
            return name == null ? "" : name;
        }
        try {
            String name = ConfigHandler.getIdentifierValue(ConfigHandler.CacheType.WORLDS, id);
            return name == null ? "" : name;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return "";
        }
    }

    public static int matchWorld(String name) {
        if (name == null) {
            return -1;
        }

        int id = -1;
        try {
            // Parse wid:# parameter used internally for /co tp click events
            if (name.startsWith("wid:")) {
                String nameWid = name.substring("wid:".length());
                if (!isAsciiDigits(nameWid)) {
                    return -1;
                }
                int wid;
                try {
                    wid = Integer.parseInt(nameWid);
                }
                catch (NumberFormatException e) {
                    return -1;
                }
                String resolved = getWorldName(wid);
                if (resolved.isEmpty()) {
                    return -1;
                }
                name = resolved;
            }

            // Determine closest match on world name
            String result = "";
            name = name.replaceFirst("#", "").toLowerCase(java.util.Locale.ROOT).trim();
            if (name.isEmpty()) {
                return -1;
            }

            for (World world : Bukkit.getServer().getWorlds()) {
                String worldName = world.getName();
                if (worldName.toLowerCase(java.util.Locale.ROOT).equals(name)) {
                    result = world.getName();
                    break;
                }
                else if (worldName.toLowerCase(java.util.Locale.ROOT).endsWith(name)) {
                    result = world.getName();
                }
                else if (worldName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-zA-Z0-9]", "").endsWith(name)) {
                    result = world.getName();
                }
            }

            if (result.length() > 0) {
                id = getWorldId(result);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return id;
    }

    private static boolean isAsciiDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public static String getWidIndex(String queryTable) {
        String index = "";
        if (ConfigHandler.databaseType.isMySQL()) {
            index = "USE INDEX(wid) ";
        }
        else if (ConfigHandler.databaseType.isSQLite()) {
            switch (queryTable) {
                case "block":
                    index = "INDEXED BY block_index ";
                    break;
                case "container":
                    index = "INDEXED BY container_index ";
                    break;
                case "item":
                    index = "INDEXED BY item_index ";
                    break;
                case "sign":
                    index = "INDEXED BY sign_index ";
                    break;
                case "chat":
                    index = "INDEXED BY chat_wid_index ";
                    break;
                case "command":
                    index = "INDEXED BY command_wid_index ";
                    break;
                case "session":
                    index = "INDEXED BY session_index ";
                    break;
                default:
                    break;
            }
        }

        return index;
    }
}
