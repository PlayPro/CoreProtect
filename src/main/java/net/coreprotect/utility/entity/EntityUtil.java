package net.coreprotect.utility.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockState;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Cat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Horse.Style;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Panda.Gene;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Parrot.Variant;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.Spellcaster.Spell;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.rollback.Rollback;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.spigot.SpigotAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.AttributeUtils;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.WorldUtils;

public class EntityUtil {

    private static final long ENTITY_RESTORE_TIMEOUT_SECONDS = 30L;

    private static final Map<String, Material> WORKSTATIONS = Map.ofEntries(
            Map.entry("armorer", Material.BLAST_FURNACE),
            Map.entry("butcher", Material.SMOKER),
            Map.entry("cartographer", Material.CARTOGRAPHY_TABLE),
            Map.entry("cleric", Material.BREWING_STAND),
            Map.entry("farmer", Material.COMPOSTER),
            Map.entry("fisherman", Material.BARREL),
            Map.entry("fletcher", Material.FLETCHING_TABLE),
            Map.entry("leatherworker", Material.CAULDRON),
            Map.entry("librarian", Material.LECTERN),
            Map.entry("mason", Material.STONECUTTER),
            Map.entry("shepherd", Material.LOOM),
            Map.entry("toolsmith", Material.SMITHING_TABLE),
            Map.entry("weaponsmith", Material.GRINDSTONE));


    /**
     * Position in the entity kill data list of the attribute format marker, followed by the baseline bits. Index 7
     * holds the entity UUID or null, and index 8 the kill location that EntitySpawnTracking stores for placed entities.
     */
    public static final int ATTRIBUTE_FORMAT_INDEX = 9;

    /**
     * Attribute format marker: the attribute list leaves out the attributes whose bits are set in the long that
     * follows the marker (see {@link AttributeUtils#baselineBit}). Rows without the marker list every attribute the
     * entity had.
     */
    public static final int BASELINE_ATTRIBUTES = 1;

    private EntityUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static void spawnEntity(final BlockState block, final EntityType type, final List<Object> list) {
        if (type != null) {
            spawnEntity(block, type, list, type.name());
        }
    }

    public static void spawnEntity(BlockState block, EntityType type, List<Object> data, String cacheTypeKey) {
        if (type != null && type != EntityType.UNKNOWN) {
            scheduleEntitySpawn(block.getLocation(), type, data, cacheTypeKey);
        }
    }

    public static CompletableFuture<Entity> restoreEntity(final Location blockLocation, final EntityType type, final List<Object> list) {
        return scheduleEntitySpawn(blockLocation, type, list, null);
    }

    public static CompletableFuture<Entity> restoreLegacyEntity(Location location, EntityType type, List<Object> data, int storedTypeId, BooleanSupplier allowed) {
        if (type == null || type == EntityType.UNKNOWN) {
            return CompletableFuture.completedFuture(null);
        }
        return scheduleEntitySpawn(location, type, data, Integer.toString(storedTypeId), allowed);
    }

    private static CompletableFuture<Entity> scheduleEntitySpawn(Location blockLocation, EntityType type, List<Object> list, String cacheTypeKey) {
        return scheduleEntitySpawn(blockLocation, type, list, cacheTypeKey, null);
    }

    private static CompletableFuture<Entity> scheduleEntitySpawn(final Location blockLocation, final EntityType type, final List<Object> list, final String cacheTypeKey, BooleanSupplier allowed) {
        CompletableFuture<Entity> completion = new CompletableFuture<>();
        if (type == null) {
            completion.complete(null);
            return completion;
        }
        if (blockLocation == null || blockLocation.getWorld() == null) {
            completion.complete(null);
            return completion;
        }
        boolean legacyTransition = cacheTypeKey != null;
        if (!legacyTransition) {
            completion.orTimeout(ENTITY_RESTORE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        Location restoreLocation = EntitySpawnTracking.isPlacedEntityType(type) ? EntitySpawnTracking.getKillRestoreLocation(blockLocation.getWorld(), list) : null;
        if (restoreLocation == null) {
            restoreLocation = blockLocation.clone();
            restoreLocation.setX(restoreLocation.getX() + 0.50);
            restoreLocation.setZ(restoreLocation.getZ() + 0.50);
        }
        final Location entityLocation = restoreLocation;
        Runnable task = () -> {
            if (completion.isDone()) {
                return;
            }
            Entity entity = null;
            try {
                if (allowed != null && !allowed.getAsBoolean()) {
                    completion.complete(null);
                    return;
                }
                entity = entityLocation.getWorld().spawnEntity(entityLocation, type);
                if (entity == null || !entity.isValid()) {
                    completion.complete(null);
                    return;
                }

                if (list.isEmpty() || completion.isDone()) {
                    completeEntityRestore(completion, entity, legacyTransition);
                    return;
                }
                @SuppressWarnings("unchecked")
                List<Object> age = (List<Object>) list.get(0);
                @SuppressWarnings("unchecked")
                List<Object> tame = (List<Object>) list.get(1);
                @SuppressWarnings("unchecked")
                List<Object> data = (List<Object>) list.get(2);

                if (list.size() >= 5) {
                    entity.setCustomNameVisible((Boolean) list.get(3));
                    entity.setCustomName((String) list.get(4));
                }

                if (entity instanceof Ageable) {
                    restoreAge((Ageable) entity, age);
                }
                if (entity instanceof Tameable) {
                    restoreTameable((Tameable) entity, tame);
                }
                if (entity instanceof Attributable && list.size() >= 6) {
                    Attributable attributable = (Attributable) entity;
                    @SuppressWarnings("unchecked")
                    List<Object> attributes = (List<Object>) list.get(5);
                    Long baselineBits = baselineBits(list);
                    if (baselineBits != null) {
                        AttributeUtils.restoreBaseline(attributable, baselineBits);
                    }
                    restoreAttributes(attributable, attributes);
                }

                if (entity instanceof LivingEntity && list.size() >= 7) {
                    LivingEntity livingEntity = (LivingEntity) entity;
                    @SuppressWarnings("unchecked")
                    List<Object> details = (List<Object>) list.get(6);
                    LivingEntityDetails.restore(livingEntity, details);
                }

                boolean placedEntity = EntitySpawnTracking.isPlacedEntity(entity);
                if (placedEntity) {
                    EntitySpawnTracking.restoreKillState(entity, list);
                }
                int count = 0;
                for (Object value : placedEntity ? Collections.emptyList() : data) {
                    if (entity instanceof Creeper) {
                        Creeper creeper = (Creeper) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            creeper.setPowered(set);
                        }
                    }
                    else if (entity instanceof Enderman) {
                        Enderman enderman = (Enderman) entity;
                        if (count == 1 && value instanceof String) {
                            restoreCarriedBlock(enderman, (String) value);
                        }
                    }
                    else if (entity instanceof IronGolem) {
                        IronGolem irongolem = (IronGolem) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            irongolem.setPlayerCreated(set);
                        }
                    }
                    else if (entity instanceof Cat) {
                        Cat cat = (Cat) entity;
                        if (count == 0) {
                            if (value instanceof String) {
                                Object registryValue = registryValue((String) value, Cat.Type.class);
                                value = registryValue == null ? enumValue(Cat.Type.class, value) : registryValue;
                            }
                            if (value instanceof Cat.Type) {
                                cat.setCatType((Cat.Type) value);
                            }
                        }
                        else if (count == 1) {
                            DyeColor set = enumValue(DyeColor.class, value);
                            if (set != null) {
                                cat.setCollarColor(set);
                            }
                        }
                        else if (count == 2) {
                            boolean set = (Boolean) value;
                            cat.setSitting(set);
                        }
                    }
                    else if (entity instanceof Fox) {
                        Fox fox = (Fox) entity;
                        if (count == 0) {
                            Fox.Type set = enumValue(Fox.Type.class, value);
                            if (set != null) {
                                fox.setFoxType(set);
                            }
                        }
                        else if (count == 1) {
                            boolean set = (Boolean) value;
                            fox.setSitting(set);
                        }
                    }
                    else if (entity instanceof Panda) {
                        Panda panda = (Panda) entity;
                        if (count == 0) {
                            Gene set = enumValue(Gene.class, value);
                            if (set != null) {
                                panda.setMainGene(set);
                            }
                        }
                        else if (count == 1) {
                            Gene set = enumValue(Gene.class, value);
                            if (set != null) {
                                panda.setHiddenGene(set);
                            }
                        }
                    }
                    else if (entity instanceof Pig) {
                        Pig pig = (Pig) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            pig.setSaddle(set);
                        }
                        else {
                            BukkitAdapter.ADAPTER.setEntityMeta(entity, value, count);
                            PaperAdapter.ADAPTER.setEntityMeta(entity, value, count);
                        }
                    }
                    else if (entity instanceof Sheep) {
                        Sheep sheep = (Sheep) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            sheep.setSheared(set);
                        }
                        else if (count == 1) {
                            DyeColor set = enumValue(DyeColor.class, value);
                            if (set != null) {
                                sheep.setColor(set);
                            }
                        }
                    }
                    else if (entity instanceof MushroomCow) {
                        MushroomCow mushroomCow = (MushroomCow) entity;
                        if (count == 0) {
                            MushroomCow.Variant set = enumValue(MushroomCow.Variant.class, value);
                            if (set != null) {
                                mushroomCow.setVariant(set);
                            }
                        }
                    }
                    else if (entity instanceof Slime) {
                        Slime slime = (Slime) entity;
                        if (count == 0) {
                            int set = (Integer) value;
                            slime.setSize(set);
                        }
                    }
                    else if (entity instanceof Parrot) {
                        Parrot parrot = (Parrot) entity;
                        if (count == 0) {
                            Variant set = enumValue(Variant.class, value);
                            if (set != null) {
                                parrot.setVariant(set);
                            }
                        }
                    }
                    else if (entity instanceof TropicalFish) {
                        TropicalFish tropicalFish = (TropicalFish) entity;
                        if (count == 0) {
                            DyeColor set = enumValue(DyeColor.class, value);
                            if (set != null) {
                                tropicalFish.setBodyColor(set);
                            }
                        }
                        else if (count == 1) {
                            TropicalFish.Pattern set = enumValue(TropicalFish.Pattern.class, value);
                            if (set != null) {
                                tropicalFish.setPattern(set);
                            }
                        }
                        else if (count == 2) {
                            DyeColor set = enumValue(DyeColor.class, value);
                            if (set != null) {
                                tropicalFish.setPatternColor(set);
                            }
                        }
                    }
                    else if (entity instanceof Phantom) {
                        Phantom phantom = (Phantom) entity;
                        if (count == 0) {
                            int set = (Integer) value;
                            phantom.setSize(set);
                        }
                    }
                    else if (entity instanceof AbstractVillager) {
                        AbstractVillager abstractVillager = (AbstractVillager) entity;
                        if (count == 0 && abstractVillager instanceof Villager) {
                            Profession profession = villagerVariant(value, Profession.class, key -> Registry.VILLAGER_PROFESSION.get(key));
                            if (profession != null) {
                                ((Villager) abstractVillager).setProfession(profession);
                            }
                        }
                        else if (count == 1 && abstractVillager instanceof Villager) {
                            Villager.Type variant = villagerVariant(value, Villager.Type.class, key -> Registry.VILLAGER_TYPE.get(key));
                            if (variant != null) {
                                ((Villager) abstractVillager).setVillagerType(variant);
                            }
                        }
                        else if (count == 2 && value instanceof List<?>) {
                            restoreRecipes(abstractVillager, (List<?>) value);
                        }
                        else if (abstractVillager instanceof Villager) {
                            Villager villager = (Villager) abstractVillager;

                            if (count == 3) {
                                int set = (int) value;
                                villager.setVillagerLevel(Math.max(1, Math.min(set, 5)));
                            }
                            else if (count == 4) {
                                int set = (int) value;
                                villager.setVillagerExperience(set);
                            }
                            else if (count == 5 && value instanceof List<?>) {
                                restoreVillagerMemories(villager, (List<?>) value);
                            }
                            else if (count == 6 && value instanceof List<?>) {
                                if (!PaperAdapter.ADAPTER.setVillagerReputations(villager, (List<?>) value)) {
                                    SpigotAdapter.ADAPTER.setVillagerReputations(villager, (List<?>) value);
                                }
                            }
                            else if (count == 7) {
                                PaperAdapter.ADAPTER.setVillagerRestocksToday(villager, value);
                            }
                            else if (count == 8) {
                                SpigotAdapter.ADAPTER.setVillagerGossipDecayTime(villager, value);
                            }
                        }
                    }
                    else if (entity instanceof Raider) {
                        Raider raider = (Raider) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            raider.setPatrolLeader(set);
                        }

                        if (entity instanceof Spellcaster && count == 1) {
                            Spellcaster spellcaster = (Spellcaster) entity;
                            Spell set = enumValue(Spell.class, value);
                            if (set != null) {
                                spellcaster.setSpell(set);
                            }
                        }
                    }
                    else if (entity instanceof Wolf) {
                        Wolf wolf = (Wolf) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            wolf.setSitting(set);
                        }
                        else if (count == 1) {
                            DyeColor set = enumValue(DyeColor.class, value);
                            if (set != null) {
                                wolf.setCollarColor(set);
                            }
                        }
                        else if (count == 2) {
                            BukkitAdapter.ADAPTER.setWolfVariant(wolf, value);
                        }
                    }
                    else if (entity instanceof ZombieVillager) {
                        ZombieVillager zombieVillager = (ZombieVillager) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            zombieVillager.setBaby(set);
                        }
                        else if (count == 1) {
                            Profession profession = villagerVariant(value, Profession.class, key -> Registry.VILLAGER_PROFESSION.get(key));
                            if (profession != null) {
                                zombieVillager.setVillagerProfession(profession);
                            }
                        }
                    }
                    else if (entity instanceof Zombie) {
                        Zombie zombie = (Zombie) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            zombie.setBaby(set);
                        }
                    }
                    else if (entity instanceof AbstractHorse) {
                        if (count == 0) {
                            restoreHorse((AbstractHorse) entity, data);
                        }
                    }
                    else if (entity instanceof Bee) {
                        Bee bee = (Bee) entity;
                        if (count == 0) {
                            int set = (int) value;
                            bee.setAnger(set);
                        }
                        else if (count == 1) {
                            boolean set = (Boolean) value;
                            bee.setHasNectar(set);
                        }
                        else if (count == 2) {
                            boolean set = (Boolean) value;
                            bee.setHasStung(set);
                        }
                    }
                    else if (entity instanceof Piglin) {
                        Piglin piglin = (Piglin) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            piglin.setBaby(set);
                        }
                    }
                    else if (entity instanceof Zoglin) {
                        Zoglin zoglin = (Zoglin) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            zoglin.setBaby(set);
                        }
                    }
                    else {
                        BukkitAdapter.ADAPTER.setEntityMeta(entity, value, count);
                        PaperAdapter.ADAPTER.setEntityMeta(entity, value, count);
                    }
                    count++;
                }
                if (entity instanceof Villager) {
                    BukkitAdapter.ADAPTER.refreshVillagerBrain((Villager) entity);
                }
                if (legacyTransition && entity.isValid()) {
                    if (list.size() > 7 && list.get(7) instanceof String) {
                        EntitySpawnTracking.trackRevivedEntity(entity, UUID.fromString((String) list.get(7)));
                    }
                    int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                    int wid = WorldUtils.getWorldId(blockLocation.getWorld().getName());
                    String token = blockLocation.getBlockX() + "." + blockLocation.getBlockY() + "." + blockLocation.getBlockZ() + "." + wid + "." + cacheTypeKey;
                    CacheHandler.entityCache.put(token, new Object[] { unixtimestamp, entity.getEntityId(), entity.getUniqueId(), type });
                }
                completeEntityRestore(completion, entity, legacyTransition);
            }
            catch (Exception e) {
                if (entity != null && (!legacyTransition || allowed != null)) {
                    try {
                        EntitySpawnTracking.removeWithoutRemovalLog(entity);
                        EntitySpawnTracking.forget(entity.getUniqueId());
                    }
                    catch (Exception cleanupError) {
                        e.addSuppressed(cleanupError);
                    }
                }
                if (allowed != null) {
                    completion.completeExceptionally(e);
                }
                else {
                    ErrorReporter.report(e);
                    completion.complete(null);
                }
            }
        };
        try {
            boolean currentThreadOwnsLocation = !legacyTransition && (ConfigHandler.isFolia ?
                PaperAdapter.ADAPTER.isOwnedByCurrentRegion(entityLocation.getWorld(), entityLocation.getBlockX() >> 4, entityLocation.getBlockZ() >> 4) : Bukkit.isPrimaryThread());
            if (currentThreadOwnsLocation) {
                task.run();
            }
            else {
                Scheduler.runTask(CoreProtect.getInstance(), task, entityLocation);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            completion.complete(null);
        }
        return completion;
    }

    private static Object valueAt(List<?> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }

    static void restoreAge(Ageable entity, List<?> values) {
        Object age = valueAt(values, 0);
        Object ageLock = valueAt(values, 1);
        Object adult = valueAt(values, 2);
        Object breed = valueAt(values, 3);
        Object health = valueAt(values, 4);
        if (ageLock instanceof Boolean) {
            entity.setAgeLock((Boolean) ageLock);
        }
        if (!(age instanceof Number) && adult instanceof Boolean) {
            if ((Boolean) adult) {
                entity.setAdult();
            }
            else {
                entity.setBaby();
            }
        }
        if (breed instanceof Boolean) {
            entity.setBreed((Boolean) breed);
        }
        if (age instanceof Number) {
            entity.setAge(((Number) age).intValue());
        }
        if (health instanceof Number) {
            entity.setMaxHealth(((Number) health).doubleValue());
        }
    }

    static void restoreTameable(Tameable entity, List<?> values) {
        Object tamed = valueAt(values, 0);
        if (tamed instanceof Boolean) {
            entity.setTamed((Boolean) tamed);
        }
        Object uuid = valueAt(values, 2);
        if (uuid instanceof String) {
            OfflinePlayer owner = offlineOwner((String) uuid);
            if (owner != null) {
                entity.setOwner(owner);
                return;
            }
        }
        Object name = valueAt(values, 1);
        if (name instanceof String && !((String) name).isBlank()) {
            OfflinePlayer owner = Bukkit.getPlayerExact((String) name);
            if (owner == null) {
                owner = offlineOwner(ConfigHandler.uuidCache.get(((String) name).toLowerCase(Locale.ROOT)));
            }
            if (owner != null) {
                entity.setOwner(owner);
            }
        }
    }

    private static OfflinePlayer offlineOwner(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(value));
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static void restoreCarriedBlock(Enderman entity, String data) {
        try {
            entity.setCarriedBlock(Bukkit.createBlockData(data));
        }
        catch (IllegalArgumentException ignored) {
        }
    }

    static void restoreHorse(AbstractHorse entity, List<?> values) {
        Object max = valueAt(values, 4);
        if (max instanceof Number && ((Number) max).intValue() > 0) {
            entity.setMaxDomestication(((Number) max).intValue());
        }
        Object domestication = valueAt(values, 2);
        if (domestication instanceof Number) {
            entity.setDomestication(((Number) domestication).intValue());
        }
        Object jump = valueAt(values, 3);
        if (jump instanceof Number) {
            entity.setJumpStrength(((Number) jump).doubleValue());
        }
        ItemStack saddle = restoreEquipment(valueAt(values, entity instanceof Horse ? 8 : 6));
        if (saddle != null) {
            entity.getInventory().setSaddle(saddle);
        }
        if (entity instanceof Horse) {
            Horse horse = (Horse) entity;
            Horse.Color color = enumValue(Horse.Color.class, preferredValue(values, 9, 1));
            if (color != null) {
                horse.setColor(color);
            }
            Style style = enumValue(Style.class, preferredValue(values, 10, 5));
            if (style != null) {
                horse.setStyle(style);
            }
            ItemStack armor = restoreEquipment(valueAt(values, 11));
            Object colorData = valueAt(values, 12);
            if (armor != null && colorData instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> legacyColor = (Map<String, Object>) colorData;
                ItemMeta meta = armor.getItemMeta();
                if (meta instanceof LeatherArmorMeta) {
                    ((LeatherArmorMeta) meta).setColor(org.bukkit.Color.deserialize(legacyColor));
                    armor.setItemMeta(meta);
                }
            }
            if (armor != null) {
                horse.getInventory().setArmor(armor);
            }
        }
        else if (entity instanceof ChestedHorse) {
            Object chest = preferredValue(values, 7, 0);
            if (chest instanceof Boolean) {
                ((ChestedHorse) entity).setCarryingChest((Boolean) chest);
            }
            if (entity instanceof Llama) {
                Llama llama = (Llama) entity;
                ItemStack decor = restoreEquipment(valueAt(values, 8));
                if (decor != null) {
                    llama.getInventory().setDecor(decor);
                }
                Llama.Color color = enumValue(Llama.Color.class, valueAt(values, 9));
                if (color != null) {
                    llama.setColor(color);
                }
            }
        }
    }

    private static Object preferredValue(List<?> values, int currentIndex, int legacyIndex) {
        Object current = valueAt(values, currentIndex);
        return current == null ? valueAt(values, legacyIndex) : current;
    }

    private static ItemStack restoreEquipment(Object data) {
        if (!(data instanceof Map<?, ?>) || ((Map<?, ?>) data).isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) data;
        try {
            return item.containsKey("0") ? ItemUtils.unserializeItemStack(item) : ItemStack.deserialize(item);
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static Long baselineBits(List<Object> list) {
        if (list.size() <= ATTRIBUTE_FORMAT_INDEX + 1) {
            return null;
        }
        Object format = list.get(ATTRIBUTE_FORMAT_INDEX);
        Object bits = list.get(ATTRIBUTE_FORMAT_INDEX + 1);
        if (format instanceof Number && ((Number) format).intValue() == BASELINE_ATTRIBUTES && bits instanceof Long) {
            return (Long) bits;
        }
        return null;
    }

    static void restoreAttributes(Attributable attributable, List<Object> attributes) {
        if (attributes == null) {
            return;
        }
        for (Object value : attributes) {
            if (!(value instanceof List<?>)) {
                continue;
            }
            List<?> attributeData = (List<?>) value;
            if (attributeData.size() < 2 || !(attributeData.get(1) instanceof Number)) {
                continue;
            }
            Attribute attribute;
            try {
                attribute = AttributeUtils.resolve(attributeData.get(0));
            }
            catch (IllegalArgumentException exception) {
                continue;
            }

            AttributeInstance entityAttribute = attributable.getAttribute(attribute);
            if (entityAttribute == null) {
                continue;
            }

            entityAttribute.setBaseValue(((Number) attributeData.get(1)).doubleValue());
            for (AttributeModifier modifier : new ArrayList<>(entityAttribute.getModifiers())) {
                entityAttribute.removeModifier(modifier);
            }
            if (attributeData.size() > 2 && attributeData.get(2) instanceof List<?>) {
                for (Object modifier : (List<?>) attributeData.get(2)) {
                    if (!(modifier instanceof Map<?, ?>)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> serializedModifier = (Map<String, Object>) modifier;
                    entityAttribute.addModifier(AttributeModifier.deserialize(serializedModifier));
                }
            }
        }
    }

    private static void completeEntityRestore(CompletableFuture<Entity> completion, Entity entity, boolean legacyTransition) {
        boolean valid = entity.isValid();
        if (!completion.complete(valid ? entity : null) && valid && !legacyTransition) {
            EntitySpawnTracking.removeWithoutRemovalLog(entity);
            EntitySpawnTracking.forget(entity.getUniqueId());
        }
    }

    private static Object registryValue(String key, Class<?> type) {
        Object value = registryValueExact(key, type);
        if (value != null || key.indexOf(':') >= 0) {
            return value;
        }
        return registryValueExact("minecraft:" + key.toLowerCase(Locale.ROOT), type);
    }

    private static Object registryValueExact(String key, Class<?> type) {
        try {
            return BukkitAdapter.ADAPTER.getRegistryValue(key, type);
        }
        catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T> T enumValue(Class<T> type, Object value) {
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (!(value instanceof String) || !type.isEnum()) {
            return null;
        }
        try {
            String name = (String) value;
            int separator = name.indexOf(':');
            if (separator >= 0) {
                name = name.substring(separator + 1).toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
            }
            return (T) Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static <T> T villagerVariant(Object value, Class<T> type, Function<NamespacedKey, T> fallback) {
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (!(value instanceof String)) {
            return null;
        }
        String name = (String) value;
        Object nativeValue = registryValue(name, type);
        if (type.isInstance(nativeValue)) {
            return type.cast(nativeValue);
        }
        NamespacedKey key = namespacedKey(name);
        return key == null ? null : fallback.apply(key);
    }

    static void restoreRecipes(AbstractVillager villager, List<?> storedRecipes) {
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (Object stored : storedRecipes) {
            if (!(stored instanceof List<?>)) {
                continue;
            }
            List<?> recipe = (List<?>) stored;
            if (recipe.size() < 5 || !(recipe.get(1) instanceof Number) || !(recipe.get(2) instanceof Number)
                    || !(recipe.get(3) instanceof Boolean) || !(recipe.get(4) instanceof List<?>)) {
                continue;
            }
            ItemStack result = readRecipeItem(recipe.get(0));
            if (result == null) {
                continue;
            }
            List<?> storedIngredients = (List<?>) recipe.get(4);
            List<ItemStack> ingredients = new ArrayList<>(storedIngredients.size());
            for (Object ingredient : storedIngredients) {
                ItemStack stack = readRecipeItem(ingredient);
                if (stack != null) {
                    ingredients.add(stack);
                }
            }
            if (ingredients.isEmpty() || ingredients.size() != storedIngredients.size()) {
                continue;
            }
            int uses = ((Number) recipe.get(1)).intValue();
            int maxUses = ((Number) recipe.get(2)).intValue();
            boolean experienceReward = (Boolean) recipe.get(3);
            MerchantRecipe restored;
            if (recipe.size() > 6 && recipe.get(5) instanceof Number && recipe.get(6) instanceof Number) {
                restored = new MerchantRecipe(result, uses, maxUses, experienceReward,
                        ((Number) recipe.get(5)).intValue(), ((Number) recipe.get(6)).floatValue());
            }
            else {
                restored = new MerchantRecipe(result, uses, maxUses, experienceReward);
            }
            restored.setIngredients(ingredients);
            BukkitAdapter.ADAPTER.setMerchantRecipeMeta(restored, recipe);
            PaperAdapter.ADAPTER.setMerchantRecipeMeta(restored, recipe);
            recipes.add(restored);
        }
        if (!recipes.isEmpty()) {
            villager.setRecipes(recipes);
        }
    }

    private static ItemStack readRecipeItem(Object stored) {
        if (!(stored instanceof List<?>)) {
            return null;
        }
        List<?> item = (List<?>) stored;
        Object data = valueAt(item, 0);
        try {
            ItemStack stack = restoreEquipment(data);
            Object metadata = valueAt(item, 1);
            return stack != null && metadata instanceof List<?> ? (ItemStack) Rollback.populateItemStack(stack, metadata)[2] : stack;
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void restoreVillagerMemories(Villager villager, List<?> memories) {
        for (Object memoryObject : memories) {
            if (!(memoryObject instanceof List<?>)) {
                continue;
            }

            List<?> memory = (List<?>) memoryObject;
            if (memory.size() < 2 || !(memory.get(0) instanceof String)) {
                continue;
            }

            MemoryKey<?> memoryKey = getMemoryKey((String) memory.get(0));
            if (memoryKey == null) {
                continue;
            }

            Object value = deserializeMemoryValue(memoryKey, memory.get(1));
            if (value == null) {
                continue;
            }

            boolean invalidJobSite = isJobSiteMemory(memoryKey) && value instanceof Location && !isValidJobSiteMemory(villager, memoryKey, (Location) value);
            if (invalidJobSite) {
                setVillagerMemory(villager, memoryKey, null);
                continue;
            }

            setVillagerMemory(villager, memoryKey, value);
        }
    }

    private static MemoryKey<?> getMemoryKey(String value) {
        try {
            NamespacedKey key = namespacedKey(value);
            return key == null ? null : MemoryKey.getByKey(key);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static NamespacedKey namespacedKey(String value) {
        if (value == null) {
            return null;
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1 || value.indexOf(':', separator + 1) >= 0) {
            return null;
        }
        try {
            return new NamespacedKey(value.substring(0, separator), value.substring(separator + 1));
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Object deserializeMemoryValue(MemoryKey<?> memoryKey, Object value) {
        Class<?> memoryClass = memoryKey.getMemoryClass();
        if (memoryClass == Location.class) {
            return deserializeMemoryLocation(value);
        }
        else if (memoryClass == Long.class && value instanceof Number) {
            return ((Number) value).longValue();
        }
        else if (memoryClass == Integer.class && value instanceof Number) {
            return ((Number) value).intValue();
        }
        else if (memoryClass.isInstance(value)) {
            return value;
        }

        return null;
    }

    private static Location deserializeMemoryLocation(Object value) {
        if (!(value instanceof List<?>)) {
            return null;
        }

        List<?> data = (List<?>) value;
        boolean validLocation = data.size() >= 4 && data.get(0) instanceof String && data.get(1) instanceof Number && data.get(2) instanceof Number && data.get(3) instanceof Number;
        if (!validLocation) {
            return null;
        }

        World world = Bukkit.getServer().getWorld((String) data.get(0));
        if (world == null) {
            return null;
        }

        int x = ((Number) data.get(1)).intValue();
        int y = ((Number) data.get(2)).intValue();
        int z = ((Number) data.get(3)).intValue();
        return new Location(world, x, y, z);
    }

    private static boolean isJobSiteMemory(MemoryKey<?> memoryKey) {
        return memoryKey == MemoryKey.JOB_SITE || memoryKey == MemoryKey.POTENTIAL_JOB_SITE;
    }

    private static boolean isValidJobSiteMemory(Villager villager, MemoryKey<?> memoryKey, Location location) {
        World world = location.getWorld();
        if (world == null || (PaperAdapter.ADAPTER != null && !PaperAdapter.ADAPTER.isOwnedByCurrentRegion(world, location.getBlockX() >> 4, location.getBlockZ() >> 4))) {
            return false;
        }

        Material blockType = location.getBlock().getType();
        Material workstation = getWorkstation(villager.getProfession());
        if (workstation != null) {
            return isMatchingWorkstation(blockType, workstation);
        }

        return memoryKey == MemoryKey.POTENTIAL_JOB_SITE && isVillagerWorkstation(blockType);
    }

    private static boolean isMatchingWorkstation(Material blockType, Material workstation) {
        return workstation == Material.CAULDRON ? isCauldron(blockType) : blockType == workstation;
    }

    private static boolean isVillagerWorkstation(Material blockType) {
        return isCauldron(blockType) || WORKSTATIONS.containsValue(blockType);
    }

    private static boolean isCauldron(Material blockType) {
        return blockType == Material.CAULDRON || blockType.name().endsWith("_CAULDRON");
    }

    private static Material getWorkstation(Profession profession) {
        if (profession == null) {
            return null;
        }

        Object key = BukkitAdapter.ADAPTER.getRegistryKey(profession);
        String professionName = key == null ? "" : key.toString().toLowerCase(Locale.ROOT);
        return getWorkstation(professionName);
    }

    private static Material getWorkstation(String professionName) {
        if (professionName.startsWith("minecraft:")) {
            professionName = professionName.substring("minecraft:".length());
        }

        return WORKSTATIONS.get(professionName);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void setVillagerMemory(Villager villager, MemoryKey<?> memoryKey, Object value) {
        villager.setMemory((MemoryKey) memoryKey, value);
    }

}
