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
        Integer id = ConfigHandler.worlds.get(name);

        if (id == null) {
            // Check if another server has already added this world (multi-server setup)
            id = ConfigHandler.reloadAndGetId(ConfigHandler.CacheType.WORLDS, name);
            if (id != -1) {
                return id;
            }

            id = ConfigHandler.MAX_WORLD_ID.incrementAndGet();

            ConfigHandler.worlds.put(name, id);
            ConfigHandler.worldsReversed.put(id, name);
            Queue.queueWorldInsert(id, name);
        }

        return id;
    }

    public static String getWorldName(int id) {
        return ConfigHandler.worldsReversed.getOrDefault(id, "");
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
            ErrorReporter.report(e);
        }

        return id;
    }

    public static String getWidIndex(String queryTable) {
        String index = "";
        boolean isMySQL = net.coreprotect.config.Config.getGlobal().MYSQL;
        if (isMySQL) {
            if (true) return ""; // CH - no indexes
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
