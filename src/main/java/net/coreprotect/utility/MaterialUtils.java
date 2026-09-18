package net.coreprotect.utility;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Painting;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;

public class MaterialUtils extends Queue {

    private static final String NAMESPACE = "minecraft:";
    private static final String NAMESPACE_UPPER = "MINECRAFT:";

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
        int id = -1;

        name = name.toLowerCase(Locale.ROOT).trim();
        if (!name.contains(":")) {
            name = NAMESPACE + name;
        }

        if (ConfigHandler.databaseType.isClickHouse()) {
            return ConfigHandler.resolveIdentifierId(ConfigHandler.CacheType.MATERIALS, name, internal);
        }
        Integer existing = ConfigHandler.materials.get(name);
        if (existing != null) {
            id = existing;
        }
        else if (internal) {
            synchronized (ConfigHandler.IDENTIFIER_ALLOCATION_LOCK) {
                Integer allocated = ConfigHandler.materials.get(name);
                if (allocated != null) {
                    return allocated;
                }

                // Check if another server has already added this material (multi-server setup)
                id = ConfigHandler.reloadAndGetId(ConfigHandler.CacheType.MATERIALS, name);
                if (id != -1) {
                    return id;
                }

                int mid = ConfigHandler.materialId + 1;
                ConfigHandler.materials.put(name, mid);
                ConfigHandler.materialsReversed.put(mid, name);
                ConfigHandler.materialId = mid;
                Queue.queueMaterialInsert(mid, name);
                id = mid;
            }
        }

        return id;
    }

    public static int getBlockdataId(String data, boolean internal) {
        int id = -1;
        data = data.toLowerCase(Locale.ROOT).trim();

        if (ConfigHandler.databaseType.isClickHouse()) {
            return ConfigHandler.resolveIdentifierId(ConfigHandler.CacheType.BLOCKDATA, data, internal);
        }
        Integer existingBlockdata = ConfigHandler.blockdata.get(data);
        if (existingBlockdata != null) {
            id = existingBlockdata;
        }
        else if (internal) {
            synchronized (ConfigHandler.IDENTIFIER_ALLOCATION_LOCK) {
                Integer allocated = ConfigHandler.blockdata.get(data);
                if (allocated != null) {
                    return allocated;
                }

                // Check if another server has already added this blockdata (multi-server setup)
                id = ConfigHandler.reloadAndGetId(ConfigHandler.CacheType.BLOCKDATA, data);
                if (id != -1) {
                    return id;
                }

                int bid = ConfigHandler.blockdataId + 1;
                ConfigHandler.blockdata.put(data, bid);
                ConfigHandler.blockdataReversed.put(bid, data);
                ConfigHandler.blockdataId = bid;
                Queue.queueBlockDataInsert(bid, data);
                id = bid;
            }
        }

        return id;
    }

    public static String getBlockDataString(int id) {
        // Internal ID pulled from DB
        if (ConfigHandler.databaseType.isClickHouse()) {
            String blockdata = ConfigHandler.getIdentifierValue(ConfigHandler.CacheType.BLOCKDATA, id);
            return blockdata == null ? "" : blockdata;
        }
        String blockdata = "";
        String cachedBlockdata = ConfigHandler.blockdataReversed.get(id);
        if (cachedBlockdata != null) {
            blockdata = cachedBlockdata;
        }
        return blockdata;
    }

    public static String getBlockName(int id) {
        if (ConfigHandler.databaseType.isClickHouse()) {
            String name = ConfigHandler.getIdentifierValue(ConfigHandler.CacheType.MATERIALS, id);
            return name == null ? "" : name;
        }
        String name = "";
        String cachedName = ConfigHandler.materialsReversed.get(id);
        if (cachedName != null) {
            name = cachedName;
        }
        return name;
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
        return id > 0 ? getTypeFromStoredName(getBlockName(id)) : null;
    }

    public static Material getTypeFromStoredName(String blockName) {
        Material material = null;
        if (!blockName.isEmpty()) {
            String name = stripNamespace(blockName.toUpperCase(Locale.ROOT));
            name = net.coreprotect.bukkit.BukkitAdapter.ADAPTER.parseLegacyName(name);
            material = Material.getMaterial(name);

            if (material == null && Material.getMaterial(Material.LEGACY_PREFIX + name) != null) {
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
            name = net.coreprotect.bukkit.BukkitAdapter.ADAPTER.parseLegacyName(stripNamespace(name));
            material = isEnumName(name) ? Material.getMaterial(name) : Material.matchMaterial(name);
        }

        return material;
    }

    /**
     * matchMaterial only strips a lowercase namespace, uppercases, turns whitespace into underscores and drops
     * non-word characters before calling getMaterial. None of that changes a name made of A-Z, 0-9 and underscores,
     * so getMaterial returns the same result without the two regex replacements.
     */
    private static boolean isEnumName(String name) {
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if ((character < 'A' || character > 'Z') && (character < '0' || character > '9') && character != '_') {
                return false;
            }
        }

        return true;
    }

    /**
     * Drops a leading "MINECRAFT:" from an already-uppercased name. Avoids the split() array and
     * the uppercase copy of the namespace constant the previous form rebuilt on every call.
     */
    private static String stripNamespace(String name) {
        if (!name.startsWith(NAMESPACE_UPPER)) {
            return name;
        }

        return name.substring(NAMESPACE_UPPER.length());
    }

    public static int getArtId(String name, boolean internal) {
        int id = -1;
        name = name.toLowerCase(Locale.ROOT).trim();

        if (ConfigHandler.databaseType.isClickHouse()) {
            return ConfigHandler.resolveIdentifierId(ConfigHandler.CacheType.ART, name, internal);
        }
        Integer existingArt = ConfigHandler.art.get(name);
        if (existingArt != null) {
            id = existingArt;
        }
        else if (internal) {
            synchronized (ConfigHandler.IDENTIFIER_ALLOCATION_LOCK) {
                Integer allocated = ConfigHandler.art.get(name);
                if (allocated != null) {
                    return allocated;
                }

                // Check if another server has already added this art (multi-server setup)
                id = ConfigHandler.reloadAndGetId(ConfigHandler.CacheType.ART, name);
                if (id != -1) {
                    return id;
                }

                int artID = ConfigHandler.artId + 1;
                ConfigHandler.art.put(name, artID);
                ConfigHandler.artReversed.put(artID, name);
                ConfigHandler.artId = artID;
                Queue.queueArtInsert(artID, name);
                id = artID;
            }
        }

        return id;
    }

    public static String getPaintingArtName(Painting painting) {
        return net.coreprotect.bukkit.BukkitAdapter.ADAPTER.getPaintingArtKey(painting);
    }

    public static String getArtName(int id) {
        // Internal ID pulled from DB
        if (ConfigHandler.databaseType.isClickHouse()) {
            String artName = ConfigHandler.getIdentifierValue(ConfigHandler.CacheType.ART, id);
            return artName == null ? "" : artName;
        }
        String artname = "";
        String cachedName = ConfigHandler.artReversed.get(id);
        if (cachedName != null) {
            artname = cachedName;
        }
        return artname;
    }

    public static int getMaterialId(Material material) {
        return getBlockId(material.name(), true);
    }

    public static boolean listContains(Set<Material> list, Material value) {
        return list.contains(value);
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
