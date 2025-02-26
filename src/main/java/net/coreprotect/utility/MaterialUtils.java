package net.coreprotect.utility;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;

public class MaterialUtils extends Queue {

    private static final String NAMESPACE = "minecraft:";

    private MaterialUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static int getBlockId(Material material) {
        if (material == null) {
            material = Material.AIR;
        }
        return getBlockId(material.name(), true);
    }

    public static int getBlockId(String name, boolean internal) {
        int id = -1;

        name = name.toLowerCase(Locale.ROOT).trim();
        if (!name.contains(":")) {
            name = NAMESPACE + name;
        }

        if (ConfigHandler.materials.get(name) != null) {
            id = ConfigHandler.materials.get(name);
        }
        else if (internal) {
            int mid = ConfigHandler.materialId + 1;
            ConfigHandler.materials.put(name, mid);
            ConfigHandler.materialsReversed.put(mid, name);
            ConfigHandler.materialId = mid;
            Queue.queueMaterialInsert(mid, name);
            id = ConfigHandler.materials.get(name);
        }

        return id;
    }

    public static int getBlockdataId(String data, boolean internal) {
        int id = -1;
        data = data.toLowerCase(Locale.ROOT).trim();

        if (ConfigHandler.blockdata.get(data) != null) {
            id = ConfigHandler.blockdata.get(data);
        }
        else if (internal) {
            int bid = ConfigHandler.blockdataId + 1;
            ConfigHandler.blockdata.put(data, bid);
            ConfigHandler.blockdataReversed.put(bid, data);
            ConfigHandler.blockdataId = bid;
            Queue.queueBlockDataInsert(bid, data);
            id = ConfigHandler.blockdata.get(data);
        }

        return id;
    }

    public static String getBlockDataString(int id) {
        // Internal ID pulled from DB
        String blockdata = "";
        if (ConfigHandler.blockdataReversed.get(id) != null) {
            blockdata = ConfigHandler.blockdataReversed.get(id);
        }
        return blockdata;
    }

    public static String getBlockName(int id) {
        String name = "";
        if (ConfigHandler.materialsReversed.get(id) != null) {
            name = ConfigHandler.materialsReversed.get(id);
        }
        return name;
    }

    public static String getBlockNameShort(int id) {
        String name = getBlockName(id);
        if (name.contains(":")) {
            name = name.split(":")[1];
        }

        return name;
    }

    public static Material getType(int id) {
        // Internal ID pulled from DB
        Material material = null;
        if (ConfigHandler.materialsReversed.get(id) != null && id > 0) {
            String name = ConfigHandler.materialsReversed.get(id).toUpperCase(Locale.ROOT);
            if (name.contains(NAMESPACE.toUpperCase(Locale.ROOT))) {
                name = name.split(":")[1];
            }

            name = net.coreprotect.bukkit.BukkitAdapter.ADAPTER.parseLegacyName(name);
            material = Material.getMaterial(name);

            if (material == null) {
                material = Material.getMaterial(name, true);
            }
        }

        return material;
    }

    public static Material getType(String name) {
        // Name entered by user
        Material material = null;
        name = name.toUpperCase(Locale.ROOT).trim();
        if (!name.startsWith("#")) {
            if (name.contains(NAMESPACE.toUpperCase(Locale.ROOT))) {
                name = name.split(":")[1];
            }

            name = net.coreprotect.bukkit.BukkitAdapter.ADAPTER.parseLegacyName(name);
            material = Material.matchMaterial(name);
        }

        return material;
    }

    public static int getArtId(String name, boolean internal) {
        int id = -1;
        name = name.toLowerCase(Locale.ROOT).trim();

        if (ConfigHandler.art.get(name) != null) {
            id = ConfigHandler.art.get(name);
        }
        else if (internal) {
            int artID = ConfigHandler.artId + 1;
            ConfigHandler.art.put(name, artID);
            ConfigHandler.artReversed.put(artID, name);
            ConfigHandler.artId = artID;
            Queue.queueArtInsert(artID, name);
            id = ConfigHandler.art.get(name);
        }

        return id;
    }

    public static String getArtName(int id) {
        // Internal ID pulled from DB
        String artname = "";
        if (ConfigHandler.artReversed.get(id) != null) {
            artname = ConfigHandler.artReversed.get(id);
        }
        return artname;
    }

    public static int getMaterialId(Material material) {
        return getBlockId(material.name(), true);
    }

    public static boolean listContains(Set<Material> list, Material value) {
        boolean result = false;
        for (Material list_value : list) {
            if (list_value.equals(value)) {
                result = true;
                break;
            }
        }
        return result;
    }

    public static int rolledBack(int rolledBack, boolean isInventory) {
        switch (rolledBack) {
            case 1: // just block rolled back
                return isInventory ? 0 : 1;
            case 2: // just inventory rolled back
                return isInventory ? 1 : 0;
            case 3: // block and inventory rolled back
                return 1;
            default: // no rollbacks
                return 0;
        }
    }

    public static int toggleRolledBack(int rolledBack, boolean isInventory) {
        switch (rolledBack) {
            case 1: // just block rolled back
                return isInventory ? 3 : 0;
            case 2: // just inventory rolled back
                return isInventory ? 0 : 3;
            case 3: // block and inventory rolled back
                return isInventory ? 1 : 2;
            default: // no rollbacks
                return isInventory ? 2 : 1;
        }
    }
}
