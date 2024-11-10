package net.coreprotect.utility.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
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
import org.bukkit.entity.Player;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.database.rollback.Rollback;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Util;

public class EntityUtil {

    private EntityUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static void spawnEntity(final BlockState block, final EntityType type, final List<Object> list) {
        if (type == null) {
            return;
        }
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            try {
                Location location = block.getLocation();
                location.setX(location.getX() + 0.50);
                location.setZ(location.getZ() + 0.50);
                Entity entity = block.getLocation().getWorld().spawnEntity(location, type);

                if (list.isEmpty()) {
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

                int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                int wid = Util.getWorldId(block.getWorld().getName());
                String token = "" + block.getX() + "." + block.getY() + "." + block.getZ() + "." + wid + "." + type.name() + "";
                CacheHandler.entityCache.put(token, new Object[] { unixtimestamp, entity.getEntityId() });

                if (entity instanceof Ageable) {
                    int count = 0;
                    Ageable ageable = (Ageable) entity;
                    for (Object value : age) {
                        if (count == 0) {
                            int set = (Integer) value;
                            ageable.setAge(set);
                        }
                        else if (count == 1) {
                            boolean set = (Boolean) value;
                            ageable.setAgeLock(set);
                        }
                        else if (count == 2) {
                            boolean set = (Boolean) value;
                            if (set) {
                                ageable.setAdult();
                            }
                            else {
                                ageable.setBaby();
                            }
                        }
                        else if (count == 3) {
                            boolean set = (Boolean) value;
                            ageable.setBreed(set);
                        }
                        else if (count == 4 && value != null) {
                            // deprecated
                            double set = (Double) value;
                            ageable.setMaxHealth(set);
                        }
                        count++;
                    }
                }
                if (entity instanceof Tameable) {
                    int count = 0;
                    Tameable tameable = (Tameable) entity;
                    for (Object value : tame) {
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            tameable.setTamed(set);
                        }
                        else if (count == 1) {
                            String set = (String) value;
                            if (set.length() > 0) {
                                Player owner = Bukkit.getServer().getPlayer(set);
                                if (owner == null) {
                                    OfflinePlayer offlinePlayer = Bukkit.getServer().getOfflinePlayer(set);
                                    if (offlinePlayer != null) {
                                        tameable.setOwner(offlinePlayer);
                                    }
                                }
                                else {
                                    tameable.setOwner(owner);
                                }
                            }
                        }
                        count++;
                    }
                }
                if (entity instanceof Attributable && list.size() >= 6) {
                    Attributable attributable = (Attributable) entity;
                    @SuppressWarnings("unchecked")
                    List<Object> attributes = (List<Object>) list.get(5);
                    for (Object value : attributes) {
                        @SuppressWarnings("unchecked")
                        List<Object> attributeData = (List<Object>) value;
                        Attribute attribute = null;
                        if (attributeData.get(0) instanceof Attribute) {
                            attribute = (Attribute) attributeData.get(0);
                        }
                        else {
                            attribute = (Attribute) BukkitAdapter.ADAPTER.getRegistryValue((String) attributeData.get(0), Attribute.class);
                        }
                        Double baseValue = (Double) attributeData.get(1);
                        @SuppressWarnings("unchecked")
                        List<Object> attributeModifiers = (List<Object>) attributeData.get(2);

                        AttributeInstance entityAttribute = attributable.getAttribute(attribute);
                        if (entityAttribute != null) {
                            entityAttribute.setBaseValue(baseValue);
                            for (AttributeModifier modifier : entityAttribute.getModifiers()) {
                                entityAttribute.removeModifier(modifier);
                            }
                            for (Object modifier : attributeModifiers) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> serializedModifier = (Map<String, Object>) modifier;
                                entityAttribute.addModifier(AttributeModifier.deserialize(serializedModifier));
                            }
                        }
                    }
                }

                if (entity instanceof LivingEntity && list.size() >= 7) {
                    LivingEntity livingEntity = (LivingEntity) entity;
                    @SuppressWarnings("unchecked")
                    List<Object> details = (List<Object>) list.get(6);
                    int count = 0;
                    for (Object value : details) {
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            livingEntity.setRemoveWhenFarAway(set);
                        }
                        else if (count == 1) {
                            boolean set = (Boolean) value;
                            livingEntity.setCanPickupItems(set);
                        }
                        count++;
                    }
                }

                int count = 0;
                for (Object value : data) {
                    if (entity instanceof Creeper) {
                        Creeper creeper = (Creeper) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            creeper.setPowered(set);
                        }
                    }
                    else if (entity instanceof Enderman) {
                        Enderman enderman = (Enderman) entity;
                        if (count == 1) {
                            String blockDataString = (String) value;
                            BlockData blockData = Bukkit.getServer().createBlockData(blockDataString);
                            enderman.setCarriedBlock(blockData);
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
                                value = BukkitAdapter.ADAPTER.getRegistryValue((String) value, Cat.Type.class);
                            }
                            Cat.Type set = (Cat.Type) value;
                            cat.setCatType(set);
                        }
                        else if (count == 1) {
                            DyeColor set = (DyeColor) value;
                            cat.setCollarColor(set);
                        }
                    }
                    else if (entity instanceof Fox) {
                        Fox fox = (Fox) entity;
                        if (count == 0) {
                            Fox.Type set = (Fox.Type) value;
                            fox.setFoxType(set);
                        }
                        else if (count == 1) {
                            boolean set = (Boolean) value;
                            fox.setSitting(set);
                        }
                    }
                    else if (entity instanceof Panda) {
                        Panda panda = (Panda) entity;
                        if (count == 0) {
                            Gene set = (Gene) value;
                            panda.setMainGene(set);
                        }
                        else if (count == 1) {
                            Gene set = (Gene) value;
                            panda.setHiddenGene(set);
                        }
                    }
                    else if (entity instanceof Pig) {
                        Pig pig = (Pig) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            pig.setSaddle(set);
                        }
                    }
                    else if (entity instanceof Sheep) {
                        Sheep sheep = (Sheep) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            sheep.setSheared(set);
                        }
                        else if (count == 1) {
                            DyeColor set = (DyeColor) value;
                            sheep.setColor(set);
                        }
                    }
                    else if (entity instanceof MushroomCow) {
                        MushroomCow mushroomCow = (MushroomCow) entity;
                        if (count == 0) {
                            MushroomCow.Variant set = (MushroomCow.Variant) value;
                            mushroomCow.setVariant(set);
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
                            Variant set = (Variant) value;
                            parrot.setVariant(set);
                        }
                    }
                    else if (entity instanceof TropicalFish) {
                        TropicalFish tropicalFish = (TropicalFish) entity;
                        if (count == 0) {
                            DyeColor set = (DyeColor) value;
                            tropicalFish.setBodyColor(set);
                        }
                        else if (count == 1) {
                            TropicalFish.Pattern set = (TropicalFish.Pattern) value;
                            tropicalFish.setPattern(set);
                        }
                        else if (count == 2) {
                            DyeColor set = (DyeColor) value;
                            tropicalFish.setPatternColor(set);
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
                        if (count == 0) {
                            if (abstractVillager instanceof Villager) {
                                Villager villager = (Villager) abstractVillager;
                                if (value instanceof String) {
                                    value = BukkitAdapter.ADAPTER.getRegistryValue((String) value, Profession.class);
                                }
                                Profession set = (Profession) value;
                                villager.setProfession(set);
                            }
                        }
                        else if (count == 1) {
                            if (abstractVillager instanceof Villager && (value instanceof Villager.Type || value instanceof String)) {
                                Villager villager = (Villager) abstractVillager;
                                if (value instanceof String) {
                                    value = BukkitAdapter.ADAPTER.getRegistryValue((String) value, Villager.Type.class);
                                }
                                Villager.Type set = (Villager.Type) value;
                                villager.setVillagerType(set);
                            }
                        }
                        else if (count == 2) {
                            List<MerchantRecipe> merchantRecipes = new ArrayList<>();
                            @SuppressWarnings("unchecked")
                            List<Object> set = (List<Object>) value;
                            for (Object recipes : set) {
                                @SuppressWarnings("unchecked")
                                List<Object> recipe = (List<Object>) recipes;
                                @SuppressWarnings("unchecked")
                                List<Object> itemMap = (List<Object>) recipe.get(0);
                                @SuppressWarnings("unchecked")
                                ItemStack result = ItemStack.deserialize((Map<String, Object>) itemMap.get(0));
                                @SuppressWarnings("unchecked")
                                List<List<Map<String, Object>>> metadata = (List<List<Map<String, Object>>>) itemMap.get(1);
                                Object[] populatedStack = Rollback.populateItemStack(result, metadata);
                                result = (ItemStack) populatedStack[2];
                                int uses = (int) recipe.get(1);
                                int maxUses = (int) recipe.get(2);
                                boolean experienceReward = (boolean) recipe.get(3);
                                List<ItemStack> merchantIngredients = new ArrayList<>();
                                @SuppressWarnings("unchecked")
                                List<Object> ingredients = (List<Object>) recipe.get(4);
                                for (Object ingredient : ingredients) {
                                    @SuppressWarnings("unchecked")
                                    List<Object> ingredientMap = (List<Object>) ingredient;
                                    @SuppressWarnings("unchecked")
                                    ItemStack item = ItemStack.deserialize((Map<String, Object>) ingredientMap.get(0));
                                    @SuppressWarnings("unchecked")
                                    List<List<Map<String, Object>>> itemMetaData = (List<List<Map<String, Object>>>) ingredientMap.get(1);
                                    populatedStack = Rollback.populateItemStack(item, itemMetaData);
                                    item = (ItemStack) populatedStack[2];
                                    merchantIngredients.add(item);
                                }
                                MerchantRecipe merchantRecipe = new MerchantRecipe(result, uses, maxUses, experienceReward);
                                if (recipe.size() > 6) {
                                    int villagerExperience = (int) recipe.get(5);
                                    float priceMultiplier = (float) recipe.get(6);
                                    merchantRecipe = new MerchantRecipe(result, uses, maxUses, experienceReward, villagerExperience, priceMultiplier);
                                }
                                merchantRecipe.setIngredients(merchantIngredients);
                                merchantRecipes.add(merchantRecipe);
                            }
                            if (!merchantRecipes.isEmpty()) {
                                abstractVillager.setRecipes(merchantRecipes);
                            }
                        }
                        else {
                            Villager villager = (Villager) abstractVillager;

                            if (count == 3) {
                                int set = (int) value;
                                villager.setVillagerLevel(set);
                            }
                            else if (count == 4) {
                                int set = (int) value;
                                villager.setVillagerExperience(set);
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
                            Spell set = (Spell) value;
                            spellcaster.setSpell(set);
                        }
                    }
                    else if (entity instanceof Wolf) {
                        Wolf wolf = (Wolf) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            wolf.setSitting(set);
                        }
                        else if (count == 1) {
                            DyeColor set = (DyeColor) value;
                            wolf.setCollarColor(set);
                        }
                    }
                    else if (entity instanceof ZombieVillager) {
                        ZombieVillager zombieVillager = (ZombieVillager) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            zombieVillager.setBaby(set);
                        }
                        else if (count == 1) {
                            if (value instanceof String) {
                                value = BukkitAdapter.ADAPTER.getRegistryValue((String) value, Profession.class);
                            }
                            Profession set = (Profession) value;
                            zombieVillager.setVillagerProfession(set);
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
                        AbstractHorse abstractHorse = (AbstractHorse) entity;
                        if (count == 0 && value != null) {
                            // deprecated
                            boolean set = (Boolean) value;
                            if (entity instanceof ChestedHorse) {
                                ChestedHorse chestedHorse = (ChestedHorse) entity;
                                chestedHorse.setCarryingChest(set);
                            }
                        }
                        else if (count == 1 && value != null) {
                            // deprecated
                            org.bukkit.entity.Horse.Color set = (org.bukkit.entity.Horse.Color) value;
                            if (entity instanceof Horse) {
                                Horse horse = (Horse) entity;
                                horse.setColor(set);
                            }
                        }
                        else if (count == 2) {
                            int set = (Integer) value;
                            abstractHorse.setDomestication(set);
                        }
                        else if (count == 3) {
                            double set = (Double) value;
                            abstractHorse.setJumpStrength(set);
                        }
                        else if (count == 4) {
                            int set = (Integer) value;
                            abstractHorse.setMaxDomestication(set);
                        }
                        else if (count == 5 && value != null) {
                            // deprecated
                            Style set = (Style) value;
                            Horse horse = (Horse) entity;
                            horse.setStyle(set);
                        }
                        if (entity instanceof Horse) {
                            Horse horse = (Horse) entity;
                            if (count == 8) {
                                if (value != null) {
                                    @SuppressWarnings("unchecked")
                                    ItemStack set = ItemStack.deserialize((Map<String, Object>) value);
                                    horse.getInventory().setSaddle(set);
                                }
                            }
                            else if (count == 9) {
                                org.bukkit.entity.Horse.Color set = (org.bukkit.entity.Horse.Color) value;
                                horse.setColor(set);
                            }
                            else if (count == 10) {
                                Style set = (Style) value;
                                horse.setStyle(set);
                            }
                            else if (count == 11) {
                                if (value != null) {
                                    @SuppressWarnings("unchecked")
                                    ItemStack set = ItemStack.deserialize((Map<String, Object>) value);
                                    horse.getInventory().setArmor(set);
                                }
                            }
                            else if (count == 12 && value != null) {
                                @SuppressWarnings("unchecked")
                                org.bukkit.Color set = org.bukkit.Color.deserialize((Map<String, Object>) value);
                                ItemStack armor = horse.getInventory().getArmor();
                                if (armor != null) {
                                    ItemMeta itemMeta = armor.getItemMeta();
                                    if (itemMeta instanceof LeatherArmorMeta) {
                                        LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta) itemMeta;
                                        leatherArmorMeta.setColor(set);
                                        armor.setItemMeta(leatherArmorMeta);
                                    }
                                }
                            }
                        }
                        else if (entity instanceof ChestedHorse) {
                            if (count == 7) {
                                ChestedHorse chestedHorse = (ChestedHorse) entity;
                                boolean set = (Boolean) value;
                                chestedHorse.setCarryingChest(set);
                            }
                            if (entity instanceof Llama) {
                                Llama llama = (Llama) entity;
                                if (count == 8) {
                                    if (value != null) {
                                        @SuppressWarnings("unchecked")
                                        ItemStack set = ItemStack.deserialize((Map<String, Object>) value);
                                        llama.getInventory().setDecor(set);
                                    }
                                }
                                else if (count == 9) {
                                    Llama.Color set = (Llama.Color) value;
                                    llama.setColor(set);
                                }
                            }
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
                    }
                    count++;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, block.getLocation());
    }

}
