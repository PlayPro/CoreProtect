package net.coreprotect.database.rollback;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Builder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Jukebox;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.banner.Pattern;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
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

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.AttributeUtils;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.entity.HangingUtil;

public class RollbackUtil extends Lookup {

    public static Object resolveBlockContainer(Block block) {
        BlockState state = block.getState();
        return state instanceof Jukebox ? state : BlockUtils.getContainerInventory(state, false);
    }

    public static ContainerTarget resolveContainer(Block block, ContainerItem item) {
        boolean frameRow = !item.faceData.isEmpty();
        BlockFace face = frameRow ? HangingUtil.parseFrameFace(item.faceData) : null;
        if ((frameRow && (face == null || item.armorStand)) || (item.armorStand && (item.slot < 0 || item.slot > 5))) {
            return null;
        }

        if (!frameRow && !item.armorStand) {
            Material type = block.getType();
            if (BlockGroup.CONTAINERS.contains(type)) {
                Object container = resolveBlockContainer(block);
                if (container != null) {
                    return new ContainerTarget(type, container);
                }
            }
        }

        ContainerTarget target = null;
        for (Entity entity : block.getChunk().getEntities()) {
            if (!(entity instanceof ItemFrame) && !(entity instanceof ArmorStand)) {
                continue;
            }
            Location location = entity.getLocation();
            if (location.getBlockX() != block.getX() || location.getBlockY() != block.getY() || location.getBlockZ() != block.getZ()) {
                continue;
            }

            ContainerTarget candidate = null;
            if (entity instanceof ItemFrame && !item.armorStand && (face == null || ((ItemFrame) entity).getFacing() == face)) {
                candidate = new ContainerTarget(Material.ITEM_FRAME, entity);
            }
            else if (entity instanceof ArmorStand && !frameRow) {
                EntityEquipment equipment = ItemUtils.getEntityEquipment((ArmorStand) entity);
                if (equipment != null) {
                    candidate = new ContainerTarget(Material.ARMOR_STAND, equipment);
                }
            }
            if (candidate != null) {
                if (target != null) {
                    return null;
                }
                target = candidate;
            }
        }
        return target;
    }

    public static final class ContainerTarget {
        private final Material type;
        private final Object container;

        private ContainerTarget(Material type, Object container) {
            this.type = type;
            this.container = container;
        }

        public Material type() {
            return type;
        }

        public Object container() {
            return container;
        }
    }

    public static final class ContainerItem {
        private final int slot;
        private final String faceData;
        private final ItemStack stack;
        private final boolean armorStand;

        private ContainerItem(int slot, String faceData, ItemStack stack, boolean armorStand) {
            this.slot = slot;
            this.faceData = faceData;
            this.stack = stack;
            this.armorStand = armorStand;
        }

        public int slot() {
            return slot;
        }

        public ItemStack stack() {
            return stack;
        }

        public boolean sameTarget(ContainerItem previous) {
            if (previous == null || armorStand != previous.armorStand || !faceData.equals(previous.faceData)) {
                return false;
            }
            return !armorStand || (slot >= 0 && slot < 6) == (previous.slot >= 0 && previous.slot < 6);
        }

        private Object[] toLegacyArray() {
            return new Object[] { slot, faceData, stack };
        }
    }

    protected static int modifyContainerItems(Material type, Object container, int slot, ItemStack item, int action) {
        return replayContainerItems(type, container, slot, item, action).modifiedArmorSlot();
    }

    public static ContainerReplayResult replayContainerItems(Material type, Object container, int slot, ItemStack item, int action) {
        if (container == null || ItemUtils.isEmptyContainerSlot(item) || action < 0 || action > 1) {
            return ContainerReplayResult.REJECTED;
        }
        try {
            if (container instanceof EntityEquipment && type == Material.ARMOR_STAND) {
                return replayEquipment((EntityEquipment) container, slot, item, action);
            }
            if (container instanceof ItemFrame) {
                ItemFrame frame = (ItemFrame) container;
                return replaySingleSlot(frame.getItem(), item, action, replacement -> {
                    frame.setItem(replacement);
                    return true;
                });
            }
            if (type == Material.JUKEBOX) {
                Object state = container instanceof Inventory ? ((Inventory) container).getHolder() : container;
                if (state instanceof Jukebox) {
                    Jukebox jukebox = (Jukebox) state;
                    return replaySingleSlot(jukebox.getRecord(), item, action, replacement -> {
                        jukebox.setRecord(replacement);
                        return jukebox.update();
                    });
                }
                return ContainerReplayResult.REJECTED;
            }
            if (container instanceof Inventory) {
                Inventory inventory = (Inventory) container;
                return action == 1 ? restoreInventoryItems(type, inventory, item) : removeInventoryItems(inventory, item);
            }
        }
        catch (RuntimeException e) {
            ErrorReporter.report(e);
        }
        return ContainerReplayResult.REJECTED;
    }

    private static ContainerReplayResult replayEquipment(EntityEquipment equipment, int slot, ItemStack item, int action) {
        if (slot >= 0 && slot < 4) {
            ItemStack[] armor = equipment.getArmorContents().clone();
            return replaySingleSlot(armor[slot], item, action, replacement -> {
                armor[slot] = replacement;
                equipment.setArmorContents(armor);
                return true;
            });
        }
        if (slot == 4 || slot == 5) {
            ItemStack current = slot == 4 ? equipment.getItemInMainHand() : equipment.getItemInOffHand();
            return replaySingleSlot(current, item, action, replacement -> {
                if (replacement != null && equipment.getHolder() instanceof ArmorStand) {
                    ((ArmorStand) equipment.getHolder()).setArms(true);
                }
                if (slot == 4) {
                    equipment.setItemInMainHand(replacement);
                }
                else {
                    equipment.setItemInOffHand(replacement);
                }
                return true;
            });
        }
        return ContainerReplayResult.REJECTED;
    }

    private static ContainerReplayResult replaySingleSlot(ItemStack current, ItemStack item, int action, Predicate<ItemStack> update) {
        boolean empty = ItemUtils.isEmptyContainerSlot(current);
        if (item.getAmount() != 1 || (!empty && (current.getAmount() != 1 || !current.isSimilar(item)))) {
            return ContainerReplayResult.REJECTED;
        }
        if ((action == 0 && empty) || (action == 1 && !empty)) {
            return ContainerReplayResult.UNCHANGED;
        }
        ItemStack replacement = action == 1 ? copyWithAmount(item, 1) : null;
        return update.test(replacement) ? new ContainerReplayResult(true, 1, -1) : ContainerReplayResult.REJECTED;
    }

    private static ContainerReplayResult restoreInventoryItems(Material type, Inventory inventory, ItemStack item) {
        ItemStack[] storage = inventory.getStorageContents().clone();
        PlayerInventory playerInventory = inventory instanceof PlayerInventory ? (PlayerInventory) inventory : null;
        int remaining = item.getAmount();
        int armorSlot = playerInventory == null ? -1 : ItemUtils.getPlayerArmorSlot(item.getType());
        ItemStack[] armor = armorSlot < 0 ? null : playerInventory.getArmorContents().clone();
        if (armor != null && ItemUtils.isEmptyContainerSlot(armor[armorSlot])) {
            armor[armorSlot] = copyWithAmount(item, 1);
            remaining--;
        }
        else {
            armorSlot = -1;
        }

        int beforeStorage = remaining;
        if (type != null && BukkitAdapter.ADAPTER.isChiseledBookshelf(type)) {
            for (int index = 0; index < storage.length && remaining > 0; index++) {
                if (ItemUtils.isEmptyContainerSlot(storage[index])) {
                    storage[index] = copyWithAmount(item, 1);
                    remaining--;
                }
            }
        }
        else {
            remaining = (int) ItemUtils.insertIntoContainerSnapshot(storage, item, remaining, inventory.getMaxStackSize());
        }
        boolean storageChanged = beforeStorage != remaining;
        ItemStack offhand = null;
        if (remaining > 0 && playerInventory != null) {
            ItemStack current = playerInventory.getItemInOffHand();
            boolean empty = ItemUtils.isEmptyContainerSlot(current);
            int existing = empty ? 0 : current.getAmount();
            if ((empty || current.isSimilar(item)) && remaining <= ItemUtils.getContainerStackLimit(item, inventory.getMaxStackSize()) - existing) {
                offhand = copyWithAmount(item, existing + remaining);
                remaining = 0;
            }
        }
        if (remaining > 0) {
            return ContainerReplayResult.REJECTED;
        }

        if (storageChanged) {
            inventory.setStorageContents(storage);
        }
        if (armorSlot >= 0) {
            playerInventory.setArmorContents(armor);
        }
        if (offhand != null) {
            playerInventory.setItemInOffHand(offhand);
        }
        return new ContainerReplayResult(true, item.getAmount(), armorSlot);
    }

    private static ContainerReplayResult removeInventoryItems(Inventory inventory, ItemStack item) {
        boolean playerInventory = inventory instanceof PlayerInventory;
        ItemStack[] contents = (playerInventory ? inventory.getContents() : inventory.getStorageContents()).clone();
        int remaining = item.getAmount();
        for (int index = contents.length - 1; index >= 0 && remaining > 0; index--) {
            ItemStack current = contents[index];
            if (ItemUtils.isEmptyContainerSlot(current) || !current.isSimilar(item)) {
                continue;
            }
            int removed = Math.min(remaining, current.getAmount());
            int left = current.getAmount() - removed;
            contents[index] = left == 0 ? null : copyWithAmount(current, left);
            remaining -= removed;
        }
        if (remaining == item.getAmount()) {
            return ContainerReplayResult.UNCHANGED;
        }
        if (remaining > 0) {
            return ContainerReplayResult.REJECTED;
        }
        if (playerInventory) {
            inventory.setContents(contents);
        }
        else {
            inventory.setStorageContents(contents);
        }
        return new ContainerReplayResult(true, item.getAmount(), -1);
    }

    private static ItemStack copyWithAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }

    public static final class ContainerReplayResult {
        private static final ContainerReplayResult REJECTED = new ContainerReplayResult(false, 0, -1);
        private static final ContainerReplayResult UNCHANGED = new ContainerReplayResult(true, 0, -1);
        private final boolean applied;
        private final int modifiedAmount;
        private final int modifiedArmorSlot;

        private ContainerReplayResult(boolean applied, int modifiedAmount, int modifiedArmorSlot) {
            this.applied = applied;
            this.modifiedAmount = modifiedAmount;
            this.modifiedArmorSlot = modifiedArmorSlot;
        }

        public boolean applied() {
            return applied;
        }

        public int modifiedAmount() {
            return modifiedAmount;
        }

        public int modifiedArmorSlot() {
            return modifiedArmorSlot;
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
            ErrorReporter.report(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ContainerItem decodeItemStack(ItemStack itemstack, Object list) {
        int slot = 0;
        String faceData = "";
        boolean armorStand = false;
        if (list == null) {
            return new ContainerItem(slot, faceData, itemstack, armorStand);
        }

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
                        ItemStack item = ItemUtils.unserializeItemStackLegacy(value);
                        if (item != null) {
                            shulkerBox.getInventory().addItem(item);
                        }
                    }
                    meta.setBlockState(shulkerBox);
                    itemstack.setItemMeta(meta);
                }

                return new ContainerItem(slot, faceData, itemstack, armorStand);
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
                    armorStand = true;
                    Object storedSlot = mapData.get("slot");
                    slot = storedSlot instanceof Integer ? (Integer) storedSlot : -1;
                }
                else if (mapData.get("facing") != null) {
                    faceData = String.valueOf(mapData.get("facing"));
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
                        Map<Object, Map<String, Object>> modifiersMap = (Map<Object, Map<String, Object>>) item;
                        for (Map.Entry<Object, Map<String, Object>> entry : modifiersMap.entrySet()) {
                            try {
                                Attribute attribute = AttributeUtils.resolve(entry.getKey());
                                AttributeModifier modifier = AttributeModifier.deserialize(entry.getValue());
                                itemMeta.addAttributeModifier(attribute, modifier);
                            }
                            catch (IllegalArgumentException e) {
                                // Attribute is unavailable or the modifier cannot be applied.
                            }
                        }
                    }

                    itemstack.setItemMeta(itemMeta);
                }
                else if (itemCount == 0) {
                    ItemMeta itemMeta = itemstack.getItemMeta();
                    if (itemMeta != null) {
                        ItemMeta meta = ItemUtils.deserializeItemMeta(itemMeta.getClass(), mapData);
                        itemstack.setItemMeta(meta);
                    }

                    if (map.size() > 1) {
                        ItemMeta subMeta = itemstack.getItemMeta();
                        if (subMeta instanceof PotionMeta) {
                            org.bukkit.Color color = org.bukkit.Color.deserialize(map.get(1));
                            ((PotionMeta) subMeta).setColor(color);
                            itemstack.setItemMeta(subMeta);
                        }
                    }
                }
                else {
                    ItemMeta itemMeta = itemstack.getItemMeta();
                    if (itemMeta instanceof LeatherArmorMeta) {
                        LeatherArmorMeta meta = (LeatherArmorMeta) itemMeta;
                        for (Map<String, Object> colorData : map) {
                            org.bukkit.Color color = org.bukkit.Color.deserialize(colorData);
                            meta.setColor(color);
                        }
                        itemstack.setItemMeta(meta);
                    }
                    else if (itemMeta instanceof PotionMeta) {
                        PotionMeta meta = (PotionMeta) itemMeta;
                        for (Map<String, Object> potionData : map) {
                            PotionEffect effect = new PotionEffect(potionData);
                            meta.addCustomEffect(effect, true);
                        }
                        itemstack.setItemMeta(meta);
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
                            ItemStack crossbowItem = ItemUtils.unserializeItemStack(itemData);
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
            ErrorReporter.report(e);
        }
        return new ContainerItem(slot, faceData, itemstack, armorStand);
    }

    public static Object[] populateItemStack(ItemStack itemstack, Object list) {
        return decodeItemStack(itemstack, list).toLegacyArray();
    }

    public static Object[] populateItemStack(ItemStack itemstack, byte[] metadata) {
        return decodeContainerItem(itemstack, metadata).toLegacyArray();
    }

    public static ContainerItem decodeContainerItem(ItemStack itemstack, byte[] metadata) {
        return decodeItemStack(itemstack, deserializeMetadata(metadata));
    }

    /**
     * Deserializes metadata from a byte array into a list of objects.
     *
     * @param metadata
     *            The byte array containing serialized metadata
     * @return The deserialized list of objects or null if deserialization fails
     */
    public static List<Object> deserializeMetadata(byte[] metadata) {
        return BlockStatement.deserializeMetadata(metadata);
    }


    static void queueStoredEntitySpawn(String user, BlockState block, int storedTypeId, int data) {
        Queue.queueEntitySpawn(user, block, storedTypeId, data);
    }

}
