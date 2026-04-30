package net.coreprotect.utility;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Painting;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import org.bukkit.NamespacedKey;

public class MaterialUtils extends Queue {

    private MaterialUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static int getBlockId(Material material) {
        if (material == null) {
            material = Material.AIR;
        }
        return getBlockId(material.name(), true);
    }

    public static int getBlockId(String blockData, Material fallback, boolean internal) {
        String name = BlockTypeUtils.getBlockDataKey(blockData);
        if (name.length() == 0 && fallback != null) {
            name = fallback.getKey().toString();
        }

        return name.length() == 0 ? -1 : getBlockId(name, internal);
    }

    public static int getBlockId(String name, boolean internal) {

        name = name.toLowerCase(Locale.ROOT).trim();
        if (!name.contains(":")) {
            name = NamespacedKey.MINECRAFT + ":" + name;
        }

        int id = ConfigHandler.materials.getOrDefault(name, -1);
        if (id == -1 && internal) {
            // Check if another server has already added this material (multi-server setup)
            id = ConfigHandler.reloadAndGetId(ConfigHandler.CacheType.MATERIALS, name);
            if (id != -1) {
                return id;
            }

            id = ConfigHandler.MAX_MATERIAL_ID.incrementAndGet();
            ConfigHandler.materials.put(name, id);
            ConfigHandler.materialsReversed.put(id, name);
            Queue.queueMaterialInsert(id, name);
        }

        return id;
    }

    public static int getBlockdataId(String data, boolean internal) {
        data = data.toLowerCase(Locale.ROOT).trim();

        int id = ConfigHandler.blockdata.getOrDefault(data, -1);
        if (id == -1 && internal) {
            // Check if another server has already added this blockdata (multi-server setup)
            id = ConfigHandler.reloadAndGetId(ConfigHandler.CacheType.BLOCKDATA, data);
            if (id != -1) {
                return id;
            }

            id = ConfigHandler.MAX_BLOCKDATA_ID.incrementAndGet();
            ConfigHandler.blockdata.put(data, id);
            ConfigHandler.blockdataReversed.put(id, data);
            Queue.queueBlockDataInsert(id, data);
        }

        return id;
    }

    public static String getBlockDataString(int id) {
        // Internal ID pulled from DB
        return ConfigHandler.blockdataReversed.getOrDefault(id, "");
    }

    public static String getBlockName(int id) {
        return ConfigHandler.materialsReversed.getOrDefault(id, "");
    }

    public static String getBlockDisplayName(int id, int data) {
        Material material = getType(id);
        if (material != null) {
            return StringUtils.nameFilter(material.name().toLowerCase(Locale.ROOT), data);
        }

        return getBlockName(id);
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

        String name = ConfigHandler.materialsReversed.get(id);
        if (name != null && id > 0) {
            name = name.toUpperCase(Locale.ROOT);
            if (name.contains(":")) {
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
            if (name.contains(":")) {
                name = name.split(":")[1];
            }

            name = net.coreprotect.bukkit.BukkitAdapter.ADAPTER.parseLegacyName(name);
            material = Material.matchMaterial(name);
        }

        return material;
    }

    public static int getArtId(String name, boolean internal) {
        name = name.toLowerCase(Locale.ROOT).trim();

        int id = ConfigHandler.art.getOrDefault(name, -1);
        if (id == -1 && internal) {
            // Check if another server has already added this art (multi-server setup)
            id = ConfigHandler.reloadAndGetId(ConfigHandler.CacheType.ART, name);
            if (id != -1) {
                return id;
            }

            id = ConfigHandler.MAX_ART_ID.incrementAndGet();
            ConfigHandler.art.put(name, id);
            ConfigHandler.artReversed.put(id, name);
            Queue.queueArtInsert(id, name);
        }

        return id;
    }

    public static String getPaintingArtName(Painting painting) {
        return net.coreprotect.bukkit.BukkitAdapter.ADAPTER.getPaintingArtKey(painting);
    }

    public static String getArtName(int id) {
        // Internal ID pulled from DB
        return ConfigHandler.artReversed.getOrDefault(id, "");
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
