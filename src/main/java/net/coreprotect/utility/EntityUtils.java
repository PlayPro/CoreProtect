package net.coreprotect.utility;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import org.bukkit.entity.ThrowableProjectile;

public class EntityUtils {

    private static final String NAMESPACE = "minecraft:";

    private EntityUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String getEntityUser(Entity entity) {
        entity = getEntitySource(entity);
        if (entity instanceof Player) {
            return entity.getName();
        }
        if (entity == null) {
            return null;
        }

        EntityType type = entity.getType();
        return type == null ? null : "#" + type.name().toLowerCase(Locale.ROOT);
    }

    public static Entity getEntitySource(Entity entity) {
        if (entity instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) entity).getShooter();
            if (shooter instanceof Entity) {
                return (Entity) shooter;
            }
        }
        return entity;
    }

    public static String getBreakUser(Entity entity, String fallback) {
        String user = getEntityUser(entity);
        if (user == null) {
            return fallback;
        }
        switch (user) {
            case "#primed_tnt":
            case "#tnt_minecart":
            case "#minecart_tnt":
                return "#tnt";
            case "#ender_dragon":
            case "#ender_dragon_part":
                return "#enderdragon";
            case "#wither_skull":
                return "#wither";
            default:
                return user;
        }
    }

    public static int getEntityId(EntityType type) {
        if (type == null) {
            return -1;
        }

        return getEntityId(type.name(), true);
    }

    public static int getEntityId(String name, boolean internal) {
        name = name.toLowerCase(Locale.ROOT).trim();
        return ConfigHandler.resolveIdentifierId(ConfigHandler.CacheType.ENTITIES, name, internal);
    }

    public static Material getEntityMaterial(final Entity entity) {
        if (entity instanceof ThrowableProjectile) {
            return ((ThrowableProjectile) entity).getItem().getType();
        }

        return getEntityMaterial(entity.getType());
    }

    public static Material getEntityMaterial(EntityType type) {
        switch (type.name()) {
            case "ARMOR_STAND":
                return Material.ARMOR_STAND;
            case "ITEM_FRAME":
                return Material.ITEM_FRAME;
            case "END_CRYSTAL":
            case "ENDER_CRYSTAL":
                return Material.END_CRYSTAL;
            case "ENDER_PEARL":
                return Material.ENDER_PEARL;
            case "POTION":
            case "SPLASH_POTION":
                return Material.SPLASH_POTION;
            case "EXPERIENCE_BOTTLE":
            case "THROWN_EXP_BOTTLE":
                return Material.EXPERIENCE_BOTTLE;
            case "TRIDENT":
                return Material.TRIDENT;
            case "FIREWORK_ROCKET":
            case "FIREWORK":
                return Material.FIREWORK_ROCKET;
            case "EGG":
                return Material.EGG;
            case "SNOWBALL":
                return Material.SNOWBALL;
            case "WIND_CHARGE":
                return Material.valueOf("WIND_CHARGE");
            default:
                return BukkitAdapter.ADAPTER.getFrameType(type);
        }
    }

    public static String getEntityName(int id) {
        // Internal ID pulled from DB
        String entityName = ConfigHandler.getIdentifierValue(ConfigHandler.CacheType.ENTITIES, id);
        return entityName == null ? "" : entityName;
    }

    public static EntityType getEntityType(int id) {
        String name = getStoredEntityTypeName(id);
        return name.isEmpty() ? EntityType.UNKNOWN : EntityType.valueOf(name);
    }

    public static EntityType getEntityType(int id, String boatVariant) {
        String name = getStoredEntityTypeName(id);
        switch (name) {
            case "": return EntityType.UNKNOWN;
            case "BOAT": return BukkitAdapter.ADAPTER.resolveStoredBoatType(false, boatVariant);
            case "CHEST_BOAT": return BukkitAdapter.ADAPTER.resolveStoredBoatType(true, boatVariant);
            default: return EntityType.valueOf(name);
        }
    }

    private static String getStoredEntityTypeName(int id) {
        String name = getEntityName(id);
        return (name.startsWith(NAMESPACE) ? name.substring(NAMESPACE.length()) : name).toUpperCase(Locale.ROOT);
    }

    public static EntityType getEntityType(String name) {
        // Name entered by user
        if (name == null) {
            return null;
        }
        name = name.toLowerCase(Locale.ROOT).trim();
        if (name.startsWith(NAMESPACE)) {
            name = name.substring(NAMESPACE.length());
        }
        if (name.isEmpty() || name.indexOf(':') >= 0 || getEntityId(name, false) == -1) {
            return null;
        }

        return EntityType.valueOf(name.toUpperCase(Locale.ROOT));
    }

    public static int getSpawnerType(EntityType type) {
        int result = getEntityId(type);
        if (result == -1) {
            result = 0; // default to pig
        }

        return result;
    }

    public static EntityType getSpawnerType(int type) {
        EntityType result = getEntityType(type);
        if (result == null) {
            result = EntityType.PIG;
        }

        return result;
    }
}
