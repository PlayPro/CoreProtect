package net.coreprotect.database.rollback;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Builder;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Jukebox;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.banner.Pattern;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectInputStream;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.database.Lookup;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.Util;

public class RollbackUtil extends Lookup {

    protected static int modifyContainerItems(Material type, Object container, int slot, ItemStack itemstack, int action) {
        int modifiedArmor = -1;
        try {
            ItemStack[] contents = null;

            if (type != null && type.equals(Material.ARMOR_STAND)) {
                EntityEquipment equipment = (EntityEquipment) container;
                if (equipment != null) {
                    if (action == 1) {
                        itemstack.setAmount(1);
                    }
                    else {
                        itemstack.setType(Material.AIR);
                        itemstack.setAmount(0);
                    }

                    if (slot < 4) {
                        contents = equipment.getArmorContents();
                        if (slot >= 0) {
                            contents[slot] = itemstack;
                        }
                        equipment.setArmorContents(contents);
                    }
                    else {
                        ArmorStand armorStand = (ArmorStand) equipment.getHolder();
                        armorStand.setArms(true);
                        switch (slot) {
                            case 4:
                                equipment.setItemInMainHand(itemstack);
                                break;
                            case 5:
                                equipment.setItemInOffHand(itemstack);
                                break;
                        }
                    }
                }
            }
            else if (type != null && type.equals(Material.ITEM_FRAME)) {
                ItemFrame frame = (ItemFrame) container;
                if (frame != null) {
                    if (action == 1) {
                        itemstack.setAmount(1);
                    }
                    else {
                        itemstack.setType(Material.AIR);
                        itemstack.setAmount(0);
                    }

                    frame.setItem(itemstack);
                }
            }
            else if (type != null && type.equals(Material.JUKEBOX)) {
                Jukebox jukebox = (Jukebox) container;
                if (jukebox != null) {
                    if (action == 1 && itemstack.getType().name().startsWith("MUSIC_DISC")) {
                        itemstack.setAmount(1);
                    }
                    else {
                        itemstack.setType(Material.AIR);
                        itemstack.setAmount(0);
                    }

                    jukebox.setRecord(itemstack);
                    jukebox.update();
                }
            }
            else {
                Inventory inventory = (Inventory) container;
                if (inventory != null) {
                    boolean isPlayerInventory = (inventory instanceof PlayerInventory);
                    if (action == 1) {
                        int count = 0;
                        int amount = itemstack.getAmount();
                        itemstack.setAmount(1);

                        while (count < amount) {
                            boolean addedItem = false;
                            if (isPlayerInventory) {
                                int setArmor = Util.setPlayerArmor((PlayerInventory) inventory, itemstack);
                                addedItem = (setArmor > -1);
                                modifiedArmor = addedItem ? setArmor : modifiedArmor;
                            }
                            if (!addedItem) {
                                if (BukkitAdapter.ADAPTER.isChiseledBookshelf(type)) {
                                    ItemStack[] inventoryContents = inventory.getStorageContents();
                                    int i = 0;
                                    for (ItemStack stack : inventoryContents) {
                                        if (stack == null) {
                                            inventoryContents[i] = itemstack;
                                            addedItem = true;
                                            break;
                                        }
                                        i++;
                                    }
                                    if (addedItem) {
                                        inventory.setStorageContents(inventoryContents);
                                    }
                                    else {
                                        addedItem = (inventory.addItem(itemstack).size() == 0);
                                    }
                                }
                                else {
                                    addedItem = (inventory.addItem(itemstack).size() == 0);
                                }
                            }
                            if (!addedItem && isPlayerInventory) {
                                PlayerInventory playerInventory = (PlayerInventory) inventory;
                                ItemStack offhand = playerInventory.getItemInOffHand();
                                if (offhand == null || offhand.getType() == Material.AIR || (itemstack.isSimilar(offhand) && offhand.getAmount() < offhand.getMaxStackSize())) {
                                    ItemStack setOffhand = itemstack.clone();
                                    if (itemstack.isSimilar(offhand)) {
                                        setOffhand.setAmount(offhand.getAmount() + 1);
                                    }

                                    playerInventory.setItemInOffHand(setOffhand);
                                }
                            }
                            count++;
                        }
                    }
                    else {
                        int removeAmount = itemstack.getAmount();
                        ItemStack removeMatch = itemstack.clone();
                        removeMatch.setAmount(1);

                        ItemStack[] inventoryContents = (isPlayerInventory ? inventory.getContents() : inventory.getStorageContents()).clone();
                        for (int i = inventoryContents.length - 1; i >= 0; i--) {
                            if (inventoryContents[i] != null) {
                                ItemStack itemStack = inventoryContents[i].clone();
                                int maxAmount = itemStack.getAmount();
                                int currentAmount = maxAmount;
                                itemStack.setAmount(1);

                                if (itemStack.toString().equals(removeMatch.toString())) {
                                    for (int scan = 0; scan < maxAmount; scan++) {
                                        if (removeAmount > 0) {
                                            currentAmount--;
                                            itemStack.setAmount(currentAmount);
                                            removeAmount--;
                                        }
                                        else {
                                            break;
                                        }
                                    }
                                }
                                else {
                                    itemStack.setAmount(maxAmount);
                                }

                                if (itemStack.getAmount() == 0) {
                                    inventoryContents[i] = null;
                                }
                                else {
                                    inventoryContents[i] = itemStack;
                                }
                            }

                            if (removeAmount == 0) {
                                break;
                            }
                        }

                        if (isPlayerInventory) {
                            inventory.setContents(inventoryContents);
                        }
                        else {
                            inventory.setStorageContents(inventoryContents);
                        }

                        int count = 0;
                        while (count < removeAmount) {
                            inventory.removeItem(removeMatch);
                            count++;
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return modifiedArmor;
    }

    public static void sortContainerItems(PlayerInventory inventory, List<Integer> modifiedArmorSlots) {
        try {
            ItemStack[] armorContents = inventory.getArmorContents();
            ItemStack[] storageContents = inventory.getStorageContents();

            for (int armor = 0; armor < armorContents.length; armor++) {
                ItemStack armorItem = armorContents[armor];
                if (armorItem == null || !modifiedArmorSlots.contains(armor)) {
                    continue;
                }

                for (int storage = 0; storage < storageContents.length; storage++) {
                    ItemStack storageItem = storageContents[storage];
                    if (storageItem == null) {
                        storageContents[storage] = armorItem;
                        armorContents[armor] = null;
                        break;
                    }
                }
            }

            inventory.setArmorContents(armorContents);
            inventory.setStorageContents(storageContents);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void buildFireworkEffect(Builder effectBuilder, Material rowType, ItemStack itemstack) {
        try {
            FireworkEffect effect = effectBuilder.build();
            if ((rowType == Material.FIREWORK_ROCKET)) {
                FireworkMeta meta = (FireworkMeta) itemstack.getItemMeta();
                meta.addEffect(effect);
                itemstack.setItemMeta(meta);
            }
            else if ((rowType == Material.FIREWORK_STAR)) {
                FireworkEffectMeta meta = (FireworkEffectMeta) itemstack.getItemMeta();
                meta.setEffect(effect);
                itemstack.setItemMeta(meta);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static Object[] populateItemStack(ItemStack itemstack, Object list) {
        int slot = 0;
        String faceData = "";

        try {
            /*
            if (list instanceof Object[]) {
                slot = (int) ((Object[]) list)[0];
                ItemMeta itemMeta = (ItemMeta) ((Object[]) list)[1];
                itemstack.setItemMeta(itemMeta);
                return new Object[] { slot, itemstack };
            }
            */

            Material rowType = itemstack.getType();
            List<Object> metaList = (List<Object>) list;
            if (metaList.size() > 0 && !(metaList.get(0) instanceof List<?>)) {
                if (rowType.name().endsWith("_BANNER")) {
                    BannerMeta meta = (BannerMeta) itemstack.getItemMeta();
                    for (Object value : metaList) {
                        if (value instanceof Map) {
                            Pattern pattern = new Pattern((Map<String, Object>) value);
                            meta.addPattern(pattern);
                        }
                    }
                    itemstack.setItemMeta(meta);
                }
                else if (BlockGroup.SHULKER_BOXES.contains(rowType)) {
                    BlockStateMeta meta = (BlockStateMeta) itemstack.getItemMeta();
                    ShulkerBox shulkerBox = (ShulkerBox) meta.getBlockState();
                    for (Object value : metaList) {
                        ItemStack item = Util.unserializeItemStackLegacy(value);
                        if (item != null) {
                            shulkerBox.getInventory().addItem(item);
                        }
                    }
                    meta.setBlockState(shulkerBox);
                    itemstack.setItemMeta(meta);
                }

                return new Object[] { slot, faceData, itemstack };
            }

            int itemCount = 0;
            Builder effectBuilder = FireworkEffect.builder();
            for (List<Map<String, Object>> map : (List<List<Map<String, Object>>>) list) {
                if (map.size() == 0) {
                    if (itemCount == 3 && (rowType == Material.FIREWORK_ROCKET || rowType == Material.FIREWORK_STAR)) {
                        buildFireworkEffect(effectBuilder, rowType, itemstack);
                        itemCount = 0;
                    }

                    itemCount++;
                    continue;
                }
                Map<String, Object> mapData = map.get(0);

                if (mapData.get("slot") != null) {
                    slot = (Integer) mapData.get("slot");
                }
                else if (mapData.get("facing") != null) {
                    faceData = (String) mapData.get("facing");
                }
                else if (mapData.get("modifiers") != null) {
                    ItemMeta itemMeta = itemstack.getItemMeta();
                    if (itemMeta.hasAttributeModifiers()) {
                        for (Map.Entry<Attribute, AttributeModifier> entry : itemMeta.getAttributeModifiers().entries()) {
                            itemMeta.removeAttributeModifier(entry.getKey(), entry.getValue());
                        }
                    }

                    List<Object> modifiers = (List<Object>) mapData.get("modifiers");

                    for (Object item : modifiers) {
                        Map<Attribute, Map<String, Object>> modifiersMap = (Map<Attribute, Map<String, Object>>) item;
                        for (Map.Entry<Attribute, Map<String, Object>> entry : modifiersMap.entrySet()) {
                            try {
                                Attribute attribute = entry.getKey();
                                AttributeModifier modifier = AttributeModifier.deserialize(entry.getValue());
                                itemMeta.addAttributeModifier(attribute, modifier);
                            }
                            catch (IllegalArgumentException e) {
                                // AttributeModifier already exists
                            }
                        }
                    }

                    itemstack.setItemMeta(itemMeta);
                }
                else if (itemCount == 0) {
                    ItemMeta meta = Util.deserializeItemMeta(itemstack.getItemMeta().getClass(), map.get(0));
                    itemstack.setItemMeta(meta);

                    if (map.size() > 1 && (rowType == Material.POTION)) {
                        PotionMeta subMeta = (PotionMeta) itemstack.getItemMeta();
                        org.bukkit.Color color = org.bukkit.Color.deserialize(map.get(1));
                        subMeta.setColor(color);
                        itemstack.setItemMeta(subMeta);
                    }
                }
                else {
                    if ((rowType == Material.LEATHER_HORSE_ARMOR) || (rowType == Material.LEATHER_HELMET) || (rowType == Material.LEATHER_CHESTPLATE) || (rowType == Material.LEATHER_LEGGINGS) || (rowType == Material.LEATHER_BOOTS)) { // leather armor
                        for (Map<String, Object> colorData : map) {
                            LeatherArmorMeta meta = (LeatherArmorMeta) itemstack.getItemMeta();
                            org.bukkit.Color color = org.bukkit.Color.deserialize(colorData);
                            meta.setColor(color);
                            itemstack.setItemMeta(meta);
                        }
                    }
                    else if ((rowType == Material.POTION)) { // potion
                        for (Map<String, Object> potionData : map) {
                            PotionMeta meta = (PotionMeta) itemstack.getItemMeta();
                            PotionEffect effect = new PotionEffect(potionData);
                            meta.addCustomEffect(effect, true);
                            itemstack.setItemMeta(meta);
                        }
                    }
                    else if (rowType.name().endsWith("_BANNER")) {
                        for (Map<String, Object> patternData : map) {
                            BannerMeta meta = (BannerMeta) itemstack.getItemMeta();
                            Pattern pattern = new Pattern(patternData);
                            meta.addPattern(pattern);
                            itemstack.setItemMeta(meta);
                        }
                    }
                    else if ((rowType == Material.CROSSBOW)) {
                        CrossbowMeta meta = (CrossbowMeta) itemstack.getItemMeta();
                        for (Map<String, Object> itemData : map) {
                            ItemStack crossbowItem = Util.unserializeItemStack(itemData);
                            if (crossbowItem != null) {
                                meta.addChargedProjectile(crossbowItem);
                            }
                        }
                        itemstack.setItemMeta(meta);
                    }
                    else if (rowType == Material.MAP || rowType == Material.FILLED_MAP) {
                        for (Map<String, Object> colorData : map) {
                            MapMeta meta = (MapMeta) itemstack.getItemMeta();
                            org.bukkit.Color color = org.bukkit.Color.deserialize(colorData);
                            meta.setColor(color);
                            itemstack.setItemMeta(meta);
                        }
                    }
                    else if ((rowType == Material.FIREWORK_ROCKET) || (rowType == Material.FIREWORK_STAR)) {
                        if (itemCount == 1) {
                            effectBuilder = FireworkEffect.builder();
                            for (Map<String, Object> fireworkData : map) {
                                org.bukkit.FireworkEffect.Type type = (org.bukkit.FireworkEffect.Type) fireworkData.getOrDefault("type", org.bukkit.FireworkEffect.Type.BALL);
                                boolean hasFlicker = (Boolean) fireworkData.get("flicker");
                                boolean hasTrail = (Boolean) fireworkData.get("trail");
                                effectBuilder.with(type);
                                effectBuilder.flicker(hasFlicker);
                                effectBuilder.trail(hasTrail);
                            }
                        }
                        else if (itemCount == 2) {
                            for (Map<String, Object> colorData : map) {
                                org.bukkit.Color color = org.bukkit.Color.deserialize(colorData);
                                effectBuilder.withColor(color);
                            }
                        }
                        else if (itemCount == 3) {
                            for (Map<String, Object> colorData : map) {
                                org.bukkit.Color color = org.bukkit.Color.deserialize(colorData);
                                effectBuilder.withFade(color);
                            }
                            buildFireworkEffect(effectBuilder, rowType, itemstack);
                            itemCount = 0;
                        }
                    }
                    else if ((rowType == Material.SUSPICIOUS_STEW)) {
                        for (Map<String, Object> suspiciousStewData : map) {
                            SuspiciousStewMeta meta = (SuspiciousStewMeta) itemstack.getItemMeta();
                            PotionEffect effect = new PotionEffect(suspiciousStewData);
                            meta.addCustomEffect(effect, true);
                            itemstack.setItemMeta(meta);
                        }
                    }
                    else {
                        BukkitAdapter.ADAPTER.setItemMeta(rowType, itemstack, map);
                    }
                }

                itemCount++;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return new Object[] { slot, faceData, itemstack };
    }

    public static Object[] populateItemStack(ItemStack itemstack, byte[] metadata) {
        if (metadata != null) {
            try {
                ByteArrayInputStream metaByteStream = new ByteArrayInputStream(metadata);
                BukkitObjectInputStream metaObjectStream = new BukkitObjectInputStream(metaByteStream);
                Object metaList = metaObjectStream.readObject();
                metaObjectStream.close();
                metaByteStream.close();

                return populateItemStack(itemstack, metaList);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return new Object[] { 0, "", itemstack };
    }

}
