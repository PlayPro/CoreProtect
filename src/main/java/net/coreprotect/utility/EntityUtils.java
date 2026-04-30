package net.coreprotect.utility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.entity.EntitySerializationFlag;
import net.coreprotect.utility.serialize.EntitySerializer;
import net.coreprotect.utility.serialize.ExtraNBTUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ThrowableProjectile;

public class EntityUtils extends Queue {

    private EntityUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static int getEntityId(EntityType type) {
        if (type == null) {
            return -1;
        }

        return getEntityId(type.name(), true);
    }

    public static int getEntityId(String name, boolean internal) {
        name = name.toLowerCase(Locale.ROOT).trim();

        int id = ConfigHandler.entities.getOrDefault(name, -1);
        if (id == -1 && internal) {
            // Check if another server has already added this entity (multi-server setup)
            id = ConfigHandler.reloadAndGetId(ConfigHandler.CacheType.ENTITIES, name);
            if (id != -1) {
                return id;
            }

            id = ConfigHandler.MAX_ENTITY_ID.incrementAndGet();
            ConfigHandler.entities.put(name, id);
            ConfigHandler.entitiesReversed.put(id, name);
            Queue.queueEntityInsert(id, name);
        }

        return id;
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
        String entityName = "";
        if (ConfigHandler.entitiesReversed.get(id) != null) {
            entityName = ConfigHandler.entitiesReversed.get(id);
        }
        return entityName;
    }

    public static EntityType getEntityType(int id) {
        // Internal ID pulled from DB
        EntityType entitytype = EntityType.UNKNOWN;
        if (ConfigHandler.entitiesReversed.get(id) != null) {
            String name = ConfigHandler.entitiesReversed.get(id);
            if (name.contains(":")) {
                name = name.split(":")[1];
            }
            entitytype = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        }
        return entitytype;
    }

    public static EntityType getEntityType(String name) {
        // Name entered by user
        EntityType type = null;
        name = name.toLowerCase(Locale.ROOT).trim();
        if (name.contains(":")) {
            name = (name.split(":"))[1];
        }

        if (ConfigHandler.entities.containsKey(name)) {
            type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        }

        return type;
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

    private static final Map<String, Tag> REMOVABLE_DEFAULTS = Util.make(new HashMap<>(), map -> {
        map.put("AbsorptionAmount", FloatTag.valueOf(0));
        map.put("Age", IntTag.valueOf(0));
        map.put("AgeLocked", ByteTag.valueOf(false));
        map.put("CanPickUpLoot", ByteTag.valueOf(false));
        map.put("ForcedAge", IntTag.valueOf(0));
        map.put("Health", FloatTag.valueOf(0));
        map.put("PersistenceRequired", ByteTag.valueOf(false));
        map.put("LeftHanded", ByteTag.valueOf(false));
        map.put("Invulnerable", ByteTag.valueOf(false));
        map.put("BatFlags", ByteTag.valueOf(false));
        map.put("IsBaby", ByteTag.valueOf(false));
        map.put("Air", ShortTag.valueOf((short) 300));
        map.put("Bukkit.Aware", ByteTag.valueOf(true));
        map.put("Bukkit.updateLevel", IntTag.valueOf(2));
        map.put("FromBucket", ByteTag.valueOf(false));
        map.put("DrownedConversionTime", IntTag.valueOf(-1));
        map.put("InWaterTime", IntTag.valueOf(-1));
        map.put("CanBreakDoors", ByteTag.valueOf(false));
        map.put("PatrolLeader", ByteTag.valueOf(false));
        map.put("IsChickenJockey", ByteTag.valueOf(false));
    });

    private static final Set<String> SKIP_EMPTY_ELEMENTS = Set.of("ArmorItems", "HandItems", "Brain", "Inventory");

    public static String serializeEntity(Entity entity) {
        final CompoundTag tag = EntitySerializer.serializeEntityAsNBT(entity, EntitySerializationFlag.FORCE);

        // remove things we do not care about
        tag.remove("WorldUUIDLeast");
        tag.remove("WorldUUIDMost");
        tag.remove("Motion");
        tag.remove("UUID");
        tag.remove("HurtTime");
        tag.remove("fall_distance");
        tag.remove("FallDistance");
        tag.remove("HurtByTimestamp");
        tag.remove("FallFlying");
        tag.remove("OnGround");

        if (tag.contains("last_hurt_by_mob") && tag.getStringOr("last_hurt_by_mob", "").equals(tag.getStringOr("last_hurt_by_player", ""))) {
            tag.remove("last_hurt_by_mob");
        }

        tag.remove("last_hurt_by_player_memory_time");
        tag.remove("ticks_since_last_hurt_by_mob");
        tag.remove("PortalCooldown");
        tag.remove("DeathTime");
        tag.remove("Fire");
        tag.remove("InLove"); // what is love?

        tag.getString("Paper.FireOverride").ifPresent(override -> {
            if ("NOT_SET".equals(override)) {
                tag.remove("Paper.FireOverride");
            }
        });

        for (final Map.Entry<String, Tag> entry : REMOVABLE_DEFAULTS.entrySet()) {
            if (entry.getValue().equals(tag.get(entry.getKey()))) {
                tag.remove(entry.getKey());
            }
        }

        for (final String tagName : SKIP_EMPTY_ELEMENTS) {
            if (tag.get(tagName) instanceof Tag maybeEmpty && ExtraNBTUtils.isEmpty(maybeEmpty)) {
                tag.remove(tagName);
            }
        }

        // remove attributes with no modifiers and the default base value
        if (((CraftEntity) entity).getHandle() instanceof net.minecraft.world.entity.LivingEntity livingEntity && tag.get("attributes") instanceof ListTag listTag) {
            final List<CompoundTag> attributesToRemove = new ArrayList<>();

            for (int i = 0; i < listTag.size(); i++) {
                final CompoundTag attribute = listTag.getCompoundOrEmpty(i);
                if (!attribute.getListOrEmpty("modifiers").isEmpty() || !attribute.contains("base")) {
                    continue;
                }

                final String id = attribute.getStringOr("id", "");
                if (id.isEmpty()) {
                    continue;
                }

                final Holder<net.minecraft.world.entity.ai.attributes.Attribute> attributeHolder = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(id)).orElse(null);
                if (attributeHolder == null) {
                    continue;
                }

                final AttributeSupplier supplier = DefaultAttributes.getSupplier((net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>) livingEntity.getType());

                if (attribute.getDoubleOr("base", Double.MIN_VALUE) == supplier.getBaseValue(attributeHolder)) {
                    attributesToRemove.add(attribute);
                }
            }

            for (final CompoundTag attribute : attributesToRemove) {
                listTag.remove(attribute);
            }
        }

        final String snbt = new SnbtPrinterTagVisitor().visit(tag);
        return snbt.replaceAll("[\n ]", "");
    }

    public static Entity deserializeEntity(String entityData, World world) {
        final CompoundTag tag;
        try {
            tag = TagParser.parseCompoundFully(entityData);
        } catch (CommandSyntaxException e) {
            throw new RuntimeException(e);
        }

        final Entity entity = EntitySerializer.deserializeEntityFromNBT(tag, world);

        if (entity instanceof LivingEntity livingEntity && livingEntity.getHealth() <= 0) {
            livingEntity.setHealth(Optional.ofNullable(livingEntity.getAttribute(Attribute.MAX_HEALTH)).map(AttributeInstance::getBaseValue).orElse(20D));
        }

        return entity;
    }
}
