package net.coreprotect.listener.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Cat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Horse;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.listener.player.EntityInteractionListener;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.spigot.SpigotAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.AttributeUtils;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.entity.EntityUtil;
import net.coreprotect.utility.entity.LivingEntityDetails;
import net.coreprotect.utility.serialize.ItemMetaHandler;

public final class EntityDeathListener implements Listener {

    private static final int ENTITY_KILL_DUPLICATE_THRESHOLD = 256;
    private static final int ENTITY_KILL_DUPLICATE_WINDOW_SECONDS = 900;
    private static final MemoryKey<?>[] VILLAGER_MEMORY_KEYS = {
        MemoryKey.JOB_SITE,
        MemoryKey.POTENTIAL_JOB_SITE,
        MemoryKey.HOME,
        MemoryKey.MEETING_POINT,
        MemoryKey.LAST_WORKED_AT_POI
    };
    private static final Map<UUID, ArmorStandBreak> pendingArmorStandBreaks = new ConcurrentHashMap<>();

    private static final class ArmorStandBreak {
        private final EntityDamageEvent damage;
        private final String user;
        private final ItemStack[] contents;

        private ArmorStandBreak(EntityDamageEvent damage, String user, ItemStack[] contents) {
            this.damage = damage;
            this.user = user;
            this.contents = contents;
        }
    }

    // Armor stand contents can't be read on EntityDeathEvent (in survival mode), so the damage listeners capture them here
    protected static void trackArmorStandBreak(Entity armorStand, EntityDamageEvent damage, String user, ItemStack[] contents) {
        UUID uuid = armorStand.getUniqueId();
        ArmorStandBreak pendingBreak = new ArmorStandBreak(damage, user, contents);
        pendingArmorStandBreaks.put(uuid, pendingBreak);

        // onEntityDeath logs the break if the hit was lethal. Otherwise, discard the capture on the next tick, or when Folia retires
        // the stand's scheduler (unload or any other removal). Neither callback touches the world.
        Runnable discard = () -> pendingArmorStandBreaks.remove(uuid, pendingBreak);
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), discard, discard, armorStand, 0);
    }

    private static void logArmorStandBreak(ArmorStand armorStand, String user, Config config) {
        ArmorStandBreak pendingBreak = pendingArmorStandBreaks.remove(armorStand.getUniqueId());
        if (!config.BLOCK_BREAK) {
            return;
        }

        EntityDamageEvent damage = user == null ? armorStand.getLastDamageCause() : null;
        boolean captured = pendingBreak != null && pendingBreak.damage == damage;
        if (user == null) {
            user = captured ? pendingBreak.user : getDamageUser(armorStand, damage, config);
            if (user == null) {
                user = "#entity";
            }
        }

        Location entityLocation = armorStand.getLocation();
        Block block = entityLocation.getBlock();
        if (config.ITEM_TRANSACTIONS) {
            Database.containerBreakCheck(user, Material.ARMOR_STAND, armorStand, captured ? pendingBreak.contents : null, block.getLocation());
        }
        Queue.queueBlockBreak(user, block.getState(), Material.ARMOR_STAND, null, (int) entityLocation.getYaw());
    }

    protected static void logEntityDeath(LivingEntity entity, String user) {
        Config config = Config.getConfig(entity.getWorld());
        if (entity instanceof ArmorStand) {
            logArmorStandBreak((ArmorStand) entity, user, config);
            return;
        }
        if (!config.ENTITY_KILLS) {
            return;
        }

        DamageCause cause = null;
        if (user == null) {
            EntityDamageEvent damage = entity.getLastDamageCause();
            cause = damage == null ? null : damage.getCause();
            if (cause == DamageCause.DROWNING && entity.getType().name().equals("GLOW_SQUID")) {
                return;
            }
            user = getDamageUser(entity, damage, config);
        }

        if (user == null || (config.DUPLICATE_SUPPRESSION && shouldSuppressEntityKill(user, entity, cause))) {
            return;
        }

        if (!user.isEmpty()) {
            if (entity instanceof Player) {
                Queue.queuePlayerKill(user, entity.getLocation(), entity.getName());
                return;
            }

            List<Object> data = new ArrayList<>();
            List<Object> age = new ArrayList<>();
            List<Object> tame = new ArrayList<>();
            List<Object> attributes = new ArrayList<>();
            List<Object> details = LivingEntityDetails.serialize(entity);
            List<Object> info = new ArrayList<>();

            if (entity instanceof Ageable) {
                Ageable ageable = (Ageable) entity;
                age.add(ageable.getAge());
                age.add(ageable.getAgeLock());
                age.add(ageable.isAdult());
                age.add(ageable.canBreed());
            }

            if (entity instanceof Tameable) {
                Tameable tameable = (Tameable) entity;
                tame.add(tameable.isTamed());
                if (tameable.isTamed()) {
                    if (tameable.getOwner() != null) {
                        tame.add(tameable.getOwner().getName());
                    }
                }
            }

            long baselineBits = 0;
            if (entity instanceof Attributable) {
                Attributable attributable = entity;
                for (Attribute attribute : Registry.ATTRIBUTE) {
                    AttributeInstance attributeInstance = attributable.getAttribute(attribute);
                    if (attributeInstance == null) {
                        continue;
                    }
                    int baselineBit = AttributeUtils.baselineBit(attributeInstance);
                    if (baselineBit >= 0) {
                        baselineBits |= 1L << baselineBit;
                    }
                    else {
                        List<Object> attributeData = new ArrayList<>();
                        List<Object> attributeModifiers = new ArrayList<>();
                        attributeData.add(BukkitAdapter.ADAPTER.getRegistryKey(attributeInstance.getAttribute()));
                        attributeData.add(attributeInstance.getBaseValue());

                        for (AttributeModifier modifier : attributeInstance.getModifiers()) {
                            attributeModifiers.add(modifier.serialize());
                        }

                        if (!attributeModifiers.isEmpty()) {
                            attributeData.add(attributeModifiers);
                        }
                        attributes.add(attributeData);
                    }
                }
            }

            if (entity instanceof Creeper) {
                Creeper creeper = (Creeper) entity;
                info.add(creeper.isPowered());
            }
            else if (entity instanceof Enderman) {
                Enderman enderman = (Enderman) entity;
                info.add(null);

                try {
                    info.add(enderman.getCarriedBlock().getAsString());
                }
                catch (Exception endermanException) {
                }
            }
            else if (entity instanceof IronGolem) {
                IronGolem irongolem = (IronGolem) entity;
                info.add(irongolem.isPlayerCreated());
            }
            else if (entity instanceof Cat) {
                Cat cat = (Cat) entity;
                info.add(BukkitAdapter.ADAPTER.getRegistryKey(cat.getCatType()));
                info.add(cat.getCollarColor());
                info.add(cat.isSitting());
            }
            else if (entity instanceof Fox) {
                Fox fox = (Fox) entity;
                info.add(fox.getFoxType());
                info.add(fox.isSitting());
            }
            else if (entity instanceof Panda) {
                Panda panda = (Panda) entity;
                info.add(panda.getMainGene());
                info.add(panda.getHiddenGene());
            }
            else if (entity instanceof Pig) {
                Pig pig = (Pig) entity;
                info.add(pig.hasSaddle());
                BukkitAdapter.ADAPTER.getEntityMeta(entity, info);
                PaperAdapter.ADAPTER.getEntityMeta(entity, info);
            }
            else if (entity instanceof Sheep) {
                Sheep sheep = (Sheep) entity;
                info.add(sheep.isSheared());
                info.add(sheep.getColor());
            }
            else if (entity instanceof MushroomCow) {
                MushroomCow mushroomCow = (MushroomCow) entity;
                info.add(mushroomCow.getVariant());
            }
            else if (entity instanceof Skeleton) {
                info.add(null);
            }
            else if (entity instanceof Slime) {
                Slime slime = (Slime) entity;
                info.add(slime.getSize());
            }
            else if (entity instanceof Parrot) {
                Parrot parrot = (Parrot) entity;
                info.add(parrot.getVariant());
            }
            else if (entity instanceof TropicalFish) {
                TropicalFish tropicalFish = (TropicalFish) entity;
                info.add(tropicalFish.getBodyColor());
                info.add(tropicalFish.getPattern());
                info.add(tropicalFish.getPatternColor());
            }
            else if (entity instanceof Phantom) {
                Phantom phantom = (Phantom) entity;
                info.add(phantom.getSize());
            }
            else if (entity instanceof AbstractVillager) {
                AbstractVillager abstractVillager = (AbstractVillager) entity;
                List<Object> recipes = new ArrayList<>();
                for (MerchantRecipe merchantRecipe : abstractVillager.getRecipes()) {
                    List<Object> recipe = new ArrayList<>();
                    List<Object> ingredients = new ArrayList<>();
                    List<Object> itemMap = new ArrayList<>();
                    ItemStack item = merchantRecipe.getResult().clone();
                    List<List<Map<String, Object>>> metadata = ItemMetaHandler.serialize(item, item.getType(), null, 0);
                    try {
                        if (item.hasItemMeta() && item.getItemMeta() != null) {
                            item.setItemMeta(null);
                        }
                    }
                    catch (Exception ignored) {
                    }
                    itemMap.add(item.serialize());
                    itemMap.add(metadata);
                    recipe.add(itemMap);
                    recipe.add(merchantRecipe.getUses());
                    recipe.add(merchantRecipe.getMaxUses());
                    recipe.add(merchantRecipe.hasExperienceReward());

                    for (ItemStack ingredient : merchantRecipe.getIngredients()) {
                        itemMap = new ArrayList<>();
                        item = ingredient.clone();
                        metadata = ItemMetaHandler.serialize(item, item.getType(), null, 0);
                        try {
                            if (item.hasItemMeta() && item.getItemMeta() != null) {
                                item.setItemMeta(null);
                            }
                        }
                        catch (Exception ignored) {
                        }
                        itemMap.add(item.serialize());
                        itemMap.add(metadata);
                        ingredients.add(itemMap);
                    }

                    recipe.add(ingredients);
                    recipe.add(merchantRecipe.getVillagerExperience());
                    recipe.add(merchantRecipe.getPriceMultiplier());
                    BukkitAdapter.ADAPTER.addMerchantRecipeMeta(merchantRecipe, recipe);
                    PaperAdapter.ADAPTER.addMerchantRecipeMeta(merchantRecipe, recipe);
                    recipes.add(recipe);
                }

                if (abstractVillager instanceof Villager) {
                    Villager villager = (Villager) abstractVillager;
                    info.add(BukkitAdapter.ADAPTER.getRegistryKey(villager.getProfession()));
                    info.add(BukkitAdapter.ADAPTER.getRegistryKey(villager.getVillagerType()));
                    info.add(recipes);
                    info.add(villager.getVillagerLevel());
                    info.add(villager.getVillagerExperience());
                    info.add(serializeVillagerMemories(villager));
                    info.add(PaperAdapter.ADAPTER.getVillagerReputations(villager));
                    info.add(PaperAdapter.ADAPTER.getVillagerRestocksToday(villager));
                    info.add(SpigotAdapter.ADAPTER.getVillagerGossipDecayTime(villager));
                }
                else {
                    info.add(null);
                    info.add(null);
                    info.add(recipes);
                }
            }
            else if (entity instanceof Raider) {
                Raider raider = (Raider) entity;
                info.add(raider.isPatrolLeader());

                if (entity instanceof Spellcaster) {
                    Spellcaster spellcaster = (Spellcaster) entity;
                    info.add(spellcaster.getSpell());
                }
            }
            else if (entity instanceof Wolf) {
                Wolf wolf = (Wolf) entity;
                info.add(wolf.isSitting());
                info.add(wolf.getCollarColor());
                BukkitAdapter.ADAPTER.getWolfVariant(wolf, info);
            }
            else if (entity instanceof ZombieVillager) {
                ZombieVillager zombieVillager = (ZombieVillager) entity;
                info.add(zombieVillager.isBaby());
                info.add(BukkitAdapter.ADAPTER.getRegistryKey(zombieVillager.getVillagerProfession()));
            }
            else if (entity instanceof Zombie) {
                Zombie zombie = (Zombie) entity;
                info.add(zombie.isBaby());
                info.add(null);
                info.add(null);
            }
            else if (entity instanceof AbstractHorse) {
                AbstractHorse abstractHorse = (AbstractHorse) entity;
                info.add(null);
                info.add(null);
                info.add(abstractHorse.getDomestication());
                info.add(abstractHorse.getJumpStrength());
                info.add(abstractHorse.getMaxDomestication());
                info.add(null);
                info.add(null);

                if (entity instanceof Horse) {
                    Horse horse = (Horse) entity;
                    info.add(null);

                    ItemStack saddle = horse.getInventory().getSaddle();
                    if (saddle != null) {
                        info.add(saddle.serialize());
                    }
                    else {
                        info.add(null);
                    }

                    info.add(horse.getColor());
                    info.add(horse.getStyle());

                    ItemStack horseArmor = horse.getInventory().getArmor();
                    if (horseArmor != null) {
                        ItemStack armor = horseArmor.clone();
                        ItemMeta itemMeta = armor.getItemMeta();
                        Color color = null;
                        if (itemMeta instanceof LeatherArmorMeta) {
                            LeatherArmorMeta meta = (LeatherArmorMeta) itemMeta;
                            color = meta.getColor();
                            meta.setColor(null);
                            armor.setItemMeta(meta);
                        }
                        info.add(armor.serialize());
                        if (color != null) {
                            info.add(color.serialize());
                        }
                        else {
                            info.add(null);
                        }
                    }
                    else {
                        info.add(null);
                        info.add(null);
                    }
                }
                else if (entity instanceof ChestedHorse) {
                    ChestedHorse chestedHorse = (ChestedHorse) entity;
                    info.add(chestedHorse.isCarryingChest());

                    if (entity instanceof Llama) {
                        Llama llama = (Llama) entity;
                        ItemStack decor = llama.getInventory().getDecor();
                        if (decor != null) {
                            info.add(decor.serialize());
                        }
                        else {
                            info.add(null);
                        }
                        info.add(llama.getColor());
                    }
                }
            }
            else if (entity instanceof Bee) {
                Bee bee = (Bee) entity;
                info.add(bee.getAnger());
                info.add(bee.hasNectar());
                info.add(bee.hasStung());
            }
            else if (entity instanceof Piglin) {
                Piglin piglin = (Piglin) entity;
                info.add(piglin.isBaby());
            }
            else if (entity instanceof Zoglin) {
                Zoglin zoglin = (Zoglin) entity;
                info.add(zoglin.isBaby());
            }
            else {
                BukkitAdapter.ADAPTER.getEntityMeta(entity, info);
                PaperAdapter.ADAPTER.getEntityMeta(entity, info);
            }

            LivingEntityDetails.trimTrailingNulls(info);
            data.add(age);
            data.add(tame);
            data.add(info);
            data.add(entity.isCustomNameVisible());
            data.add(entity.getCustomName());
            data.add(attributes);
            data.add(details);
            data.add(EntitySpawnTracking.isTracked(entity) ? entity.getUniqueId().toString() : null);
            data.add(null); // kill location, only stored for placed entities
            data.add(EntityUtil.BASELINE_ATTRIBUTES);
            data.add(baselineBits);

            Queue.queueEntityKill(user, entity.getLocation(), data, entity.getType());
        }
    }

    private static String getDamageUser(LivingEntity entity, EntityDamageEvent damage, Config config) {
        String user = EntityUtils.getBreakUser(EntityUtils.getDamager(damage), null);
        if (user != null) {
            return "#lightning_bolt".equals(user) ? "#lightning" : user;
        }

        DamageCause cause = damage == null ? null : damage.getCause();
        if (cause == null) {
            return null;
        }

        boolean skipGeneric = config.SKIP_GENERIC_DATA && (entity instanceof Zombie || entity instanceof Skeleton);
        switch (cause) {
            case FIRE:
                return "#fire";
            case FIRE_TICK:
                return skipGeneric ? null : "#fire";
            case BLOCK_EXPLOSION:
                return "#explosion";
            case WITHER:
                return "#wither_effect";
            case VOID:
                return entity.getLocation().getBlockY() >= BukkitAdapter.ADAPTER.getMinHeight(entity.getWorld()) ? "#command" : "#void";
            default:
                String causeName = cause.name();
                return skipGeneric && causeName.contains("_") ? null : "#" + causeName.toLowerCase(Locale.ROOT);
        }
    }

    private static List<Object> serializeVillagerMemories(Villager villager) {
        List<Object> memories = new ArrayList<>();
        for (MemoryKey<?> key : VILLAGER_MEMORY_KEYS) {
            addVillagerMemory(villager, memories, key);
        }

        return memories;
    }

    private static <T> void addVillagerMemory(Villager villager, List<Object> memories, MemoryKey<T> key) {
        T value = villager.getMemory(key);
        if (value == null) {
            return;
        }

        List<Object> memory = new ArrayList<>();
        memory.add(key.getKey().toString());
        if (value instanceof Location) {
            memory.add(serializeMemoryLocation((Location) value));
        }
        else {
            memory.add(value);
        }
        memories.add(memory);
    }

    private static List<Object> serializeMemoryLocation(Location location) {
        List<Object> data = new ArrayList<>();
        World world = location.getWorld();
        data.add(world == null ? "" : world.getName());
        data.add(location.getBlockX());
        data.add(location.getBlockY());
        data.add(location.getBlockZ());
        return data;
    }

    private static boolean shouldSuppressEntityKill(String user, LivingEntity entity, DamageCause cause) {
        if (user == null || !user.startsWith("#") || "#command".equals(user)) {
            return false;
        }

        Location location = entity.getLocation();
        String causeName = cause == null ? "UNKNOWN" : cause.name();
        String customName = entity.getCustomName();
        String signature = location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ() + "." + user + "." + entity.getType().name() + "." + causeName + "." + (customName == null ? "-" : customName);
        return CacheHandler.shouldSuppressRepeat(CacheHandler.entityKillDuplicateCache, signature, ENTITY_KILL_DUPLICATE_THRESHOLD, ENTITY_KILL_DUPLICATE_WINDOW_SECONDS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event instanceof Cancellable && ((Cancellable) event).isCancelled()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity == null) {
            return;
        }

        if (EntitySpawnTracking.isTrackedOrPendingIdentity(entity)) {
            EntityInteractionListener.flushPendingInteractions(entity);
            Queue.queueEntitySpawnRemoved(entity);
            EntitySpawnTracking.clearTracking(entity.getUniqueId());
        }

        logEntityDeath(entity, null);
    }
}
