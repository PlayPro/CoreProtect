package net.coreprotect.utility;

import org.bukkit.Bukkit;
import org.bukkit.World;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;

public class WorldUtils extends Queue {

    private WorldUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static int getWorldId(String name) {
        int id = -1;
        try {
            if (ConfigHandler.worlds.get(name) == null) {
                int wid = ConfigHandler.worldId + 1;
                ConfigHandler.worlds.put(name, wid);
                ConfigHandler.worldsReversed.put(wid, name);
                ConfigHandler.worldId = wid;
                Queue.queueWorldInsert(wid, name);
            }
            id = ConfigHandler.worlds.get(name);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return id;
    }

    public static String getWorldName(int id) {
        String name = "";
        try {
            if (ConfigHandler.worldsReversed.get(id) != null) {
                name = ConfigHandler.worldsReversed.get(id);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return name;
    }

    public static int matchWorld(String name) {
        int id = -1;
        try {
            // Parse wid:# parameter used internally for /co tp click events
            if (name.startsWith("wid:")) {
                String nameWid = name.replaceFirst("wid:", "");
                if (nameWid.length() > 0 && nameWid.equals(nameWid.replaceAll("[^0-9]", ""))) {
                    nameWid = getWorldName(Integer.parseInt(nameWid));
                    if (nameWid.length() > 0) {
                        name = nameWid;
                    }
                }
            }

            // Determine closest match on world name
            String result = "";
            name = name.replaceFirst("#", "").toLowerCase(java.util.Locale.ROOT).trim();
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
            e.printStackTrace();
        }

        return id;
    }
    
    public static String getWidIndex(String queryTable) {
        String index = "";
        boolean isMySQL = net.coreprotect.config.Config.getGlobal().MYSQL;
        if (isMySQL) {
            index = "USE INDEX(wid) ";
        }
        else {
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