package net.coreprotect.listener.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
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
import org.bukkit.entity.AbstractArrow;
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
import org.bukkit.entity.EntityType;
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
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.projectiles.ProjectileSource;

import com.google.common.collect.Lists;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.serialize.ItemMetaHandler;

public final class EntityDeathListener extends Queue implements Listener {

    public static void parseEntityKills(String message) {
        message = message.trim().toLowerCase(Locale.ROOT);
        if (!message.contains(" ")) {
            return;
        }

        String[] args = message.split(" ");
        if (args.length < 2 || !args[0].replaceFirst("/", "").equals("kill") || !args[1].startsWith("@e")) {
            return;
        }

        List<LivingEntity> entityList = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            List<LivingEntity> livingEntities = world.getLivingEntities();
            for (LivingEntity entity : livingEntities) {
                if (entity instanceof Player) {
                    continue;
                }

                if (entity.isValid()) {
                    entityList.add(entity);
                }
            }
        }

        for (LivingEntity entity : entityList) {
            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                if (entity != null && entity.isDead()) {
                    logEntityDeath(entity, "#command");
                }
            }, entity);
        }
    }

    protected static void logEntityDeath(LivingEntity entity, String e) {
        if (!Config.getConfig(entity.getWorld()).ENTITY_KILLS) {
            return;
        }

        EntityDamageEvent damage = entity.getLastDamageCause();
        if (damage == null) {
            return;
        }

        boolean isCommand = (damage.getCause() == DamageCause.VOID && entity.getLocation().getBlockY() >= BukkitAdapter.ADAPTER.getMinHeight(entity.getWorld()));
        if (e == null) {
            e = isCommand ? "#command" : "";
        }

        if (entity.getType().name().equals("GLOW_SQUID") && damage.getCause() == DamageCause.DROWNING) {
            return;
        }

        List<DamageCause> validDamageCauses = Arrays.asList(DamageCause.SUICIDE, DamageCause.POISON, DamageCause.THORNS, DamageCause.MAGIC, DamageCause.WITHER);

        boolean skip = true;
        EntityDamageEvent.DamageCause cause = damage.getCause();
        if (!Config.getConfig(entity.getWorld()).SKIP_GENERIC_DATA || (!(entity instanceof Zombie) && !(entity instanceof Skeleton)) || (validDamageCauses.contains(cause) || cause.name().equals("KILL"))) {
            skip = false;
        }

        if (damage instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent attack = (EntityDamageByEntityEvent) damage;
            Entity attacker = attack.getDamager();

            if (attacker instanceof Player) {
                Player player = (Player) attacker;
                e = player.getName();
            }
            else if (attacker instanceof AbstractArrow) {
                AbstractArrow arrow = (AbstractArrow) attacker;
                ProjectileSource shooter = arrow.getShooter();

                if (shooter instanceof Player) {
                    Player player = (Player) shooter;
                    e = player.getName();
                }
                else if (shooter instanceof LivingEntity) {
                    EntityType entityType = ((LivingEntity) shooter).getType();
                    if (entityType != null) { // Check for MyPet plugin
                        String name = entityType.name().toLowerCase(Locale.ROOT);
                        e = "#" + name;
                    }
                }
            }
            else if (attacker instanceof ThrownPotion) {
                ThrownPotion potion = (ThrownPotion) attacker;
                ProjectileSource shooter = potion.getShooter();

                if (shooter instanceof Player) {
                    Player player = (Player) shooter;
                    e = player.getName();
                }
                else if (shooter instanceof LivingEntity) {
                    EntityType entityType = ((LivingEntity) shooter).getType();
                    if (entityType != null) { // Check for MyPet plugin
                        String name = entityType.name().toLowerCase(Locale.ROOT);
                        e = "#" + name;
                    }
                }
            }
            else if (attacker.getType().name() != null) {
                e = "#" + attacker.getType().name().toLowerCase(Locale.ROOT);
            }
        }
        else {
            if (cause.equals(EntityDamageEvent.DamageCause.FIRE)) {
                e = "#fire";
            }
            else if (cause.equals(EntityDamageEvent.DamageCause.FIRE_TICK)) {
                if (!skip) {
                    e = "#fire";
                }
            }
            else if (cause.equals(EntityDamageEvent.DamageCause.LAVA)) {
                e = "#lava";
            }
            else if (cause.equals(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
                e = "#explosion";
            }
            else if (cause.equals(EntityDamageEvent.DamageCause.MAGIC)) {
                e = "#magic";
            }
            else if (cause.equals(EntityDamageEvent.DamageCause.WITHER)) {
                e = "#wither_effect";
            }
            else if (!cause.name().contains("_")) {
                e = "#" + cause.name().toLowerCase(Locale.ROOT);
            }
        }

        if (entity instanceof ArmorStand) {
            Location entityLocation = entity.getLocation();
            if (!Config.getConfig(entityLocation.getWorld()).ITEM_TRANSACTIONS) {
                entityLocation.setY(entityLocation.getY() + 0.99);
                Block block = entityLocation.getBlock();
                Queue.queueBlockBreak(e, block.getState(), Material.ARMOR_STAND, null, (int) entityLocation.getYaw());
            }
            /*
            else if (isCommand) {
                entityLocation.setY(entityLocation.getY() + 0.99);
                Block block = entityLocation.getBlock();
                Database.containerBreakCheck(e, Material.ARMOR_STAND, entity, null, block.getLocation());
                Queue.queueBlockBreak(e, block.getState(), Material.ARMOR_STAND, null, (int) entityLocation.getYaw());
            }
            */
            return;
        }

        EntityType entity_type = entity.getType();
        if (e.length() == 0) {
            // assume killed self
            if (!skip) {
                if (!(entity instanceof Player) && entity_type.name() != null) {
                    // Player player = (Player)entity;
                    // e = player.getName();
                    e = "#" + entity_type.name().toLowerCase(Locale.ROOT);
                }
                else if (entity instanceof Player) {
                    e = entity.getName();
                }
            }
        }

        if (e.startsWith("#wither") && !e.equals("#wither_effect")) {
            e = "#wither";
        }

        if (e.startsWith("#enderdragon")) {
            e = "#enderdragon";
        }

        if (e.startsWith("#primedtnt") || e.startsWith("#tnt")) {
            e = "#tnt";
        }

        if (e.startsWith("#lightning")) {
            e = "#lightning";
        }

        if (e.length() > 0) {
            List<Object> data = new ArrayList<>();
            List<Object> age = new ArrayList<>();
            List<Object> tame = new ArrayList<>();
            List<Object> attributes = new ArrayList<>();
            List<Object> details = new ArrayList<>();
            List<Object> info = new ArrayList<>();
            EntityType type = entity_type;

            // Basic LivingEntity attributes
            details.add(entity.getRemoveWhenFarAway());
            details.add(entity.getCanPickupItems());

            if (entity instanceof Ageable) {
                Ageable ageable = (Ageable) entity;
                age.add(ageable.getAge());
                age.add(ageable.getAgeLock());
                age.add(ageable.isAdult());
                age.add(ageable.canBreed());
                age.add(null);
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

            if (entity instanceof Attributable) {
                Attributable attributable = entity;
                for (Attribute attribute : Lists.newArrayList(Registry.ATTRIBUTE)) {
                    AttributeInstance attributeInstance = attributable.getAttribute(attribute);
                    if (attributeInstance != null) {
                        List<Object> attributeData = new ArrayList<>();
                        List<Object> attributeModifiers = new ArrayList<>();
                        attributeData.add(BukkitAdapter.ADAPTER.getRegistryKey(attributeInstance.getAttribute()));
                        attributeData.add(attributeInstance.getBaseValue());

                        for (AttributeModifier modifier : attributeInstance.getModifiers()) {
                            attributeModifiers.add(modifier.serialize());
                        }

                        attributeData.add(attributeModifiers);
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
                    item.setItemMeta(null);
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
                        item.setItemMeta(null);
                        itemMap.add(item.serialize());
                        itemMap.add(metadata);
                        ingredients.add(itemMap);
                    }

                    recipe.add(ingredients);
                    recipe.add(merchantRecipe.getVillagerExperience());
                    recipe.add(merchantRecipe.getPriceMultiplier());
                    recipes.add(recipe);
                }

                if (abstractVillager instanceof Villager) {
                    Villager villager = (Villager) abstractVillager;
                    info.add(BukkitAdapter.ADAPTER.getRegistryKey(villager.getProfession()));
                    info.add(BukkitAdapter.ADAPTER.getRegistryKey(villager.getVillagerType()));
                    info.add(recipes);
                    info.add(villager.getVillagerLevel());
                    info.add(villager.getVillagerExperience());
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
            }

            data.add(age);
            data.add(tame);
            data.add(info);
            data.add(entity.isCustomNameVisible());
            data.add(entity.getCustomName());
            data.add(attributes);
            data.add(details);

            if (!(entity instanceof Player)) {
                Queue.queueEntityKill(e, entity.getLocation(), data, type);
            }
            else {
                Queue.queuePlayerKill(e, entity.getLocation(), entity.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        /*
        System.out.println("ENTITY DEATH - " + event.getEntity().getName());
        if (event.getEntity().getKiller() != null) {
            System.out.println("^ (killer): " + event.getEntity().getKiller().getName());
        }
        else if (event.getEntity().getLastDamageCause() != null) {
            System.out.println("^ (damage cause): " + event.getEntity().getLastDamageCause().getEntity().getName());
        }
        */

        LivingEntity entity = event.getEntity();
        if (entity == null) {
            return;
        }

        logEntityDeath(entity, null);
    }
}
