package net.coreprotect.utility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;

public class EntityUtils {

    private static final String MINECRAFT_NAMESPACE = "minecraft:";
    private static final Map<String, EntityType> ENTITY_TYPES = entityTypes();

    private EntityUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String getEntityUser(Entity entity) {
        return getDirectEntityUser(getEntitySource(entity));
    }

    public static String getDirectEntityUser(Entity entity) {
        if (entity instanceof Player) {
            return entity.getName();
        }
        return getEntityTypeUser(entity);
    }

    public static String getEntityTypeUser(Entity entity) {
        if (entity == null) {
            return null;
        }

        EntityType type = entity.getType();
        return type == null ? null : "#" + type.name().toLowerCase(Locale.ROOT);
    }

    public static String getLivingProjectileShooterUser(Projectile projectile) {
        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof LivingEntity ? getDirectEntityUser((LivingEntity) shooter) : null;
    }

    public static Entity getDamager(EntityDamageEvent damage) {
        return damage instanceof EntityDamageByEntityEvent ? ((EntityDamageByEntityEvent) damage).getDamager() : null;
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
            case "#wind_charge":
                return "#windcharge";
            case "#breeze_wind_charge":
                return "#breezewindcharge";
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
        return getEntityType(id, null);
    }

    public static EntityType getEntityType(int id, String boatVariant) {
        String name = entityKey(getEntityName(id));
        if (BukkitAdapter.ADAPTER != null && ("boat".equals(name) || "chest_boat".equals(name))) {
            return BukkitAdapter.ADAPTER.resolveStoredBoatType("chest_boat".equals(name), boatVariant);
        }
        return ENTITY_TYPES.getOrDefault(name, EntityType.UNKNOWN);
    }

    public static EntityType getEntityType(String name) {
        return ENTITY_TYPES.get(entityKey(name));
    }

    public static List<Integer> getEntityIds(EntityType type) {
        if (type == null) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        Map<String, Integer> entities = ConfigHandler.entities;
        synchronized (entities) {
            for (Map.Entry<String, Integer> entry : entities.entrySet()) {
                if (getEntityType(entry.getKey()) == type) {
                    ids.add(entry.getValue());
                }
            }
        }
        return ids;
    }

    private static String entityKey(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return key.startsWith(MINECRAFT_NAMESPACE) ? key.substring(MINECRAFT_NAMESPACE.length()) : key;
    }

    private static Map<String, EntityType> entityTypes() {
        Map<String, EntityType> types = new HashMap<>();
        for (EntityType type : EntityType.values()) {
            if (type != EntityType.UNKNOWN) {
                types.put(entityKey(type.name()), type);
                types.put(entityKey(type.getKey().toString()), type);
            }
        }
        String[][] aliases = {
            { "mooshroom", "mushroom_cow" },
            { "snow_golem", "snowman" },
            { "end_crystal", "ender_crystal" },
            { "tnt", "primed_tnt" },
            { "leash_knot", "leash_hitch" },
            { "item", "dropped_item" },
            { "firework_rocket", "firework" },
            { "experience_bottle", "thrown_exp_bottle" },
            { "splash_potion", "potion" },
            { "fishing_bobber", "fishing_hook" },
            { "lightning_bolt", "lightning" },
            { "eye_of_ender", "ender_signal" },
            { "zombified_piglin", "zombie_pigman", "pig_zombie" },
            { "chest_minecart", "minecart_chest" },
            { "hopper_minecart", "minecart_hopper" },
            { "furnace_minecart", "minecart_furnace" },
            { "tnt_minecart", "minecart_tnt" },
            { "spawner_minecart", "minecart_mob_spawner" },
            { "command_block_minecart", "minecart_command" }
        };
        for (String[] names : aliases) {
            for (String name : names) {
                EntityType type = types.get(name);
                if (type != null) {
                    for (String alias : names) {
                        types.putIfAbsent(alias, type);
                    }
                    break;
                }
            }
        }
        return Map.copyOf(types);
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
        return result == null || result == EntityType.UNKNOWN ? EntityType.PIG : result;
    }
}
