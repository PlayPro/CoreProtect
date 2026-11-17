package net.coreprotect.utility;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Painting;

import net.coreprotect.config.ConfigHandler;

public class MaterialUtils {

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
            name = NAMESPACE + name;
        }
        return ConfigHandler.resolveIdentifierId(ConfigHandler.CacheType.MATERIALS, name, internal);
    }

    public static int getBlockdataId(String data, boolean internal) {
        data = data.toLowerCase(Locale.ROOT).trim();
        return ConfigHandler.resolveIdentifierId(ConfigHandler.CacheType.BLOCKDATA, data, internal);
    }

    public static String getBlockDataString(int id) {
        // Internal ID pulled from DB
        String blockdata = ConfigHandler.getIdentifierValue(ConfigHandler.CacheType.BLOCKDATA, id);
        return blockdata == null ? "" : blockdata;
    }

    public static String getBlockName(int id) {
        String name = ConfigHandler.getIdentifierValue(ConfigHandler.CacheType.MATERIALS, id);
        return name == null ? "" : name;
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
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    public static Material getType(int id) {
        // Internal ID pulled from DB
        return id > 0 ? getTypeFromStoredName(getBlockName(id)) : null;
    }

    public static Material getTypeFromStoredName(String blockName) {
        Material material = null;
        if (blockName != null && !blockName.isEmpty()) {
            String name = blockName.toUpperCase(Locale.ROOT);
            if (name.startsWith("MINECRAFT:")) {
                name = name.substring("MINECRAFT:".length());
            }
            else if (name.indexOf(':') >= 0) {
                return null;
            }

            if (name.isEmpty()) {
                return null;
            }

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
        if (name == null) {
            return null;
        }

        name = name.toUpperCase(Locale.ROOT).trim();
        if (!name.isEmpty() && !name.startsWith("#")) {
            if (name.startsWith("MINECRAFT:")) {
                name = name.substring("MINECRAFT:".length());
            }
            else if (name.indexOf(':') >= 0) {
                return null;
            }

            if (name.isEmpty()) {
                return null;
            }

            name = net.coreprotect.bukkit.BukkitAdapter.ADAPTER.parseLegacyName(name);
            material = isEnumName(name) ? Material.getMaterial(name) : Material.matchMaterial(name);
        }

        return material;
    }

    // matchMaterial only uppercases and strips whitespace and non-word characters before getMaterial, none of which changes an A-Z, 0-9 and underscore name
    private static boolean isEnumName(String name) {
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if ((character < 'A' || character > 'Z') && (character < '0' || character > '9') && character != '_') {
                return false;
            }
        }

        return !name.isEmpty();
    }

    public static int getArtId(String name, boolean internal) {
        name = name.toLowerCase(Locale.ROOT).trim();
        return ConfigHandler.resolveIdentifierId(ConfigHandler.CacheType.ART, name, internal);
    }

    public static String getPaintingArtName(Painting painting) {
        return net.coreprotect.bukkit.BukkitAdapter.ADAPTER.getPaintingArtKey(painting);
    }

    public static String getArtName(int id) {
        // Internal ID pulled from DB
        String artName = ConfigHandler.getIdentifierValue(ConfigHandler.CacheType.ART, id);
        return artName == null ? "" : artName;
    }

    public static boolean listContains(Set<Material> list, Material value) {
        return list != null && value != null && list.contains(value);
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
