package net.coreprotect.utility;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectOutputStream;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.serialize.ItemMetaHandler;

public class ItemUtils {

    private static final Object UNSERIALIZABLE_VALUE = new Object();
    private static final Logger LOGGER = Logger.getLogger("CoreProtect");
    private static final int MAX_SANITIZE_ATTEMPTS = 2;
    private static final Pattern TYPE_TOKEN_PATTERN = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$\\.]*)\\{");
    private static final Pattern REGISTRY_LOCATION_PATTERN = Pattern.compile("location=([^,\\]\\s]+)");

    private static final class FailureContext {

        private final Set<String> typeTokens;
        private final String registryLocation;

        private FailureContext(Set<String> typeTokens, String registryLocation) {
            this.typeTokens = typeTokens;
            this.registryLocation = registryLocation;
        }
    }

    private static final class SanitizationResult {

        private final Object value;
        private final boolean modified;

        private SanitizationResult(Object value, boolean modified) {
            this.value = value;
            this.modified = modified;
        }
    }

    private ItemUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static void mergeItems(Material material, ItemStack[] items) {
        if (material != null && (material.equals(Material.ARMOR_STAND) || BukkitAdapter.ADAPTER.isItemFrame(material))) {
            return;
        }
        try {
            int c1 = 0;
            for (ItemStack o1 : items) {
                if (o1 != null && o1.getAmount() > 0) {
                    int c2 = 0;
                    for (ItemStack o2 : items) {
                        if (o2 != null && c2 > c1 && o1.isSimilar(o2) && !BlockUtils.isAir(o1.getType())) { // Ignores amount
                            int namount = o1.getAmount() + o2.getAmount();
                            o1.setAmount(namount);
                            o2.setAmount(0);
                        }
                        c2++;
                    }
                }
                c1++;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ItemStack[] getContainerState(ItemStack[] array) {
        ItemStack[] result = array == null ? null : array.clone();
        if (result == null) {
            return result;
        }

        int count = 0;
        for (ItemStack itemStack : array) {
            ItemStack clonedItem = null;
            if (itemStack != null) {
                clonedItem = itemStack.clone();
            }
            result[count] = clonedItem;
            count++;
        }

        return result;
    }

    public static ItemStack[] sortContainerState(ItemStack[] array) {
        if (array == null) {
            return null;
        }

        ItemStack[] sorted = new ItemStack[array.length];
        Map<String, ItemStack> map = new HashMap<>();
        for (ItemStack itemStack : array) {
            if (itemStack == null) {
                continue;
            }

            map.put(itemStack.toString(), itemStack);
        }

        ArrayList<String> sortedKeys = new ArrayList<>(map.keySet());
        Collections.sort(sortedKeys);

        int i = 0;
        for (String key : sortedKeys) {
            sorted[i] = map.get(key);
            i++;
        }

        return sorted;
    }

    /* return true if ItemStack[] contents are identical */
    public static boolean compareContainers(ItemStack[] oldContainer, ItemStack[] newContainer) {
        if (oldContainer.length != newContainer.length) {
            return false;
        }

        for (int i = 0; i < oldContainer.length; i++) {
            ItemStack oldItem = oldContainer[i];
            ItemStack newItem = newContainer[i];

            if (oldItem == null && newItem == null) {
                continue;
            }

            if (oldItem == null || !oldItem.equals(newItem)) {
                return false;
            }
        }

        return true;
    }

    /* return true if newContainer contains new items */
    public static boolean addedContainer(ItemStack[] oldContainer, ItemStack[] newContainer) {
        if (oldContainer.length != newContainer.length) {
            return false;
        }

        for (int i = 0; i < oldContainer.length; i++) {
            ItemStack oldItem = oldContainer[i];
            ItemStack newItem = newContainer[i];

            if (oldItem == null && newItem == null) {
                continue;
            }

            if (oldItem != null && newItem == null) {
                return false;
            }

            if (oldItem == null) {
                return true;
            }

            if (!newItem.equals(oldItem)) {
                return (newItem.isSimilar(oldItem) && newItem.getAmount() > oldItem.getAmount());
            }
        }

        return false;
    }

    /* return true if item can be added to container */
    public static boolean canAddContainer(ItemStack[] container, ItemStack item, int forceMaxStack) {
        for (ItemStack containerItem : container) {
            if (containerItem == null || containerItem.getType() == Material.AIR) {
                return true;
            }

            int maxStackSize = containerItem.getMaxStackSize();
            if (forceMaxStack > 0 && (forceMaxStack < maxStackSize || maxStackSize == -1)) {
                maxStackSize = forceMaxStack;
            }

            if (maxStackSize == -1) {
                maxStackSize = 1;
            }

            if (containerItem.isSimilar(item) && containerItem.getAmount() < maxStackSize) {
                return true;
            }
        }

        return false;
    }

    public static int setPlayerArmor(PlayerInventory inventory, ItemStack itemStack) {
        String itemName = itemStack.getType().name();
        boolean isHelmet = (itemName.endsWith("_HELMET") || itemName.endsWith("_HEAD") || itemName.endsWith("_SKULL") || itemName.endsWith("_PUMPKIN"));
        boolean isChestplate = (itemName.endsWith("_CHESTPLATE"));
        boolean isLeggings = (itemName.endsWith("_LEGGINGS"));
        boolean isBoots = (itemName.endsWith("_BOOTS"));

        if (isHelmet && inventory.getHelmet() == null) {
            inventory.setHelmet(itemStack);
            return 3;
        }
        else if (isChestplate && inventory.getChestplate() == null) {
            inventory.setChestplate(itemStack);
            return 2;
        }
        else if (isLeggings && inventory.getLeggings() == null) {
            inventory.setLeggings(itemStack);
            return 1;
        }
        else if (isBoots && inventory.getBoots() == null) {
            inventory.setBoots(itemStack);
            return 0;
        }

        return -1;
    }

    public static ItemStack[] getArmorStandContents(EntityEquipment equipment) {
        ItemStack[] contents = new ItemStack[6];
        if (equipment != null) {
            // 0: BOOTS, 1: LEGGINGS, 2: CHESTPLATE, 3: HELMET
            ItemStack[] armorContent = equipment.getArmorContents();
            System.arraycopy(armorContent, 0, contents, 0, 4);
            contents[4] = equipment.getItemInMainHand();
            contents[5] = equipment.getItemInOffHand();
        }
        else {
            Arrays.fill(contents, new ItemStack(Material.AIR));
        }

        return contents;
    }

    public static ItemStack[] getContainerContents(Material type, Object container, Location location) {
        ItemStack[] contents = null;
        if (Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS && BlockGroup.CONTAINERS.contains(type)) {
            try {
                // container may be null if called from within WorldEdit logger
                if (container == null) {
                    container = location.getBlock();
                }

                if (type == Material.ARMOR_STAND) {
                    LivingEntity entity = (LivingEntity) container;
                    EntityEquipment equipment = getEntityEquipment(entity);
                    if (equipment != null) {
                        contents = getArmorStandContents(equipment);
                    }
                }
                else if (type == Material.ITEM_FRAME) {
                    ItemFrame entity = (ItemFrame) container;
                    contents = getItemFrameItem(entity);
                }
                else if (type == Material.JUKEBOX) {
                    org.bukkit.block.Jukebox blockState = (org.bukkit.block.Jukebox) ((Block) container).getState();
                    contents = BlockUtils.getJukeboxItem(blockState);
                }
                else {
                    Block block = (Block) container;
                    Inventory inventory = BlockUtils.getContainerInventory(block.getState(), true);
                    if (inventory != null) {
                        contents = inventory.getContents();
                    }
                }

                if (type == Material.ARMOR_STAND || type == Material.ITEM_FRAME) {
                    boolean hasItem = false;
                    for (ItemStack item : contents) {
                        if (item != null && !item.getType().equals(Material.AIR)) {
                            hasItem = true;
                            break;
                        }
                    }
                    if (!hasItem) {
                        contents = null;
                    }
                }

                if (contents != null) {
                    contents = getContainerState(contents);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return contents;
    }

    public static EntityEquipment getEntityEquipment(LivingEntity entity) {
        EntityEquipment equipment = null;
        try {
            equipment = entity.getEquipment();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return equipment;
    }

    public static ItemStack[] getItemFrameItem(ItemFrame entity) {
        ItemStack[] contents = null;
        try {
            contents = new ItemStack[] { entity.getItem() };
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return contents;
    }

    public static int getItemStackHashCode(ItemStack item) {
        try {
            return item.hashCode();
        }
        catch (Exception exception) {
            return -1;
        }
    }

    public static Material itemFilter(Material material, boolean blockTable) {
        if (material == null || (!blockTable && material.isItem())) {
            return material;
        }

        material = BukkitAdapter.ADAPTER.getPlantSeeds(material);
        if (material.name().contains("WALL_")) {
            material = Material.valueOf(material.name().replace("WALL_", ""));
        }

        return material;
    }

    public static Material inventoryItemFilter(Material material, boolean blockTable) {
        material = itemFilter(material, blockTable);
        if (material == null) {
            return null;
        }

        if (material == Material.LAVA) {
            return Material.LAVA_BUCKET;
        }

        if (material == Material.WATER) {
            return Material.WATER_BUCKET;
        }

        return material.isItem() ? material : null;
    }

    public static ItemStack newItemStack(Material type, int amount) {
        return new ItemStack(type, amount);
    }

    public static void updateInventory(org.bukkit.entity.Player player) {
        player.updateInventory();
    }
    
    public static byte[] convertByteData(Object data) {
        if (data == null) {
            return null;
        }

        try {
            return serializeByteData(data);
        }
        catch (Exception initialException) {
            Object workingData = data;
            Exception failure = initialException;
            Set<String> problematicTypes = new LinkedHashSet<>();
            String registryLocation = null;
            boolean sanitized = false;

            for (int attempt = 0; attempt < MAX_SANITIZE_ATTEMPTS; attempt++) {
                FailureContext failureContext = parseSerializationFailure(failure);
                problematicTypes.addAll(failureContext.typeTokens);
                if (registryLocation == null && failureContext.registryLocation != null) {
                    registryLocation = failureContext.registryLocation;
                }

                if (problematicTypes.isEmpty()) {
                    break;
                }

                SanitizationResult sanitizationResult = sanitizeByteData(workingData, problematicTypes);
                if (!sanitizationResult.modified || sanitizationResult.value == UNSERIALIZABLE_VALUE) {
                    break;
                }

                sanitized = true;
                workingData = sanitizationResult.value;

                try {
                    byte[] result = serializeByteData(workingData);
                    if (ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                        logSanitizedMetadata(problematicTypes, registryLocation);
                    }
                    return result;
                }
                catch (Exception retryException) {
                    failure = retryException;
                }
            }

            if (ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                if (sanitized) {
                    LOGGER.warning("CoreProtect failed to serialize metadata after targeted type sanitization.");
                }
                failure.printStackTrace();
            }
        }

        return null;
    }

    private static byte[] serializeByteData(Object data) throws Exception {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); BukkitObjectOutputStream objectStream = new BukkitObjectOutputStream(byteStream)) {
            objectStream.writeObject(data);
            objectStream.flush();
            return byteStream.toByteArray();
        }
    }

    private static FailureContext parseSerializationFailure(Throwable throwable) {
        Set<String> typeTokens = new LinkedHashSet<>();
        String registryLocation = null;
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                Matcher typeMatcher = TYPE_TOKEN_PATTERN.matcher(message);
                while (typeMatcher.find()) {
                    addTypeToken(typeTokens, typeMatcher.group(1));
                }

                if (registryLocation == null) {
                    Matcher locationMatcher = REGISTRY_LOCATION_PATTERN.matcher(message);
                    if (locationMatcher.find()) {
                        registryLocation = locationMatcher.group(1);
                    }
                }
            }

            for (StackTraceElement frame : current.getStackTrace()) {
                if ("serialize".equals(frame.getMethodName())) {
                    addTypeToken(typeTokens, frame.getClassName());
                }
            }

            current = current.getCause();
        }

        return new FailureContext(typeTokens, registryLocation);
    }

    private static void addTypeToken(Set<String> typeTokens, String token) {
        String normalizedToken = normalizeTypeToken(token);
        if (normalizedToken != null) {
            typeTokens.add(normalizedToken);
        }
    }

    private static String normalizeTypeToken(String token) {
        if (token == null) {
            return null;
        }

        String normalized = token.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex + 1 < normalized.length()) {
            normalized = normalized.substring(dotIndex + 1);
        }

        int dollarIndex = normalized.lastIndexOf('$');
        if (dollarIndex >= 0 && dollarIndex + 1 < normalized.length()) {
            normalized = normalized.substring(dollarIndex + 1);
        }

        StringBuilder safeToken = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char currentChar = normalized.charAt(i);
            if ((currentChar >= 'A' && currentChar <= 'Z') || (currentChar >= 'a' && currentChar <= 'z') || (currentChar >= '0' && currentChar <= '9') || currentChar == '_') {
                safeToken.append(currentChar);
            }
        }

        if (safeToken.length() == 0) {
            return null;
        }

        return safeToken.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean isProblematicType(Class<?> valueType, Set<String> problematicTypes) {
        String typeToken = normalizeTypeToken(valueType.getSimpleName());
        if (typeToken == null) {
            return false;
        }

        for (String problematicType : problematicTypes) {
            if (typeToken.equals(problematicType) || typeToken.startsWith(problematicType) || problematicType.startsWith(typeToken)) {
                return true;
            }
        }

        return false;
    }

    private static SanitizationResult sanitizeByteData(Object data, Set<String> problematicTypes) {
        if (data == null) {
            return new SanitizationResult(null, false);
        }

        if (isProblematicType(data.getClass(), problematicTypes)) {
            return new SanitizationResult(UNSERIALIZABLE_VALUE, true);
        }

        if (data instanceof Map<?, ?>) {
            Map<?, ?> sourceMap = (Map<?, ?>) data;
            Map<Object, Object> sanitizedMap = new LinkedHashMap<>();
            boolean modified = false;
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                SanitizationResult sanitizedKey = sanitizeByteData(entry.getKey(), problematicTypes);
                SanitizationResult sanitizedValue = sanitizeByteData(entry.getValue(), problematicTypes);
                if (sanitizedKey.value == UNSERIALIZABLE_VALUE || sanitizedValue.value == UNSERIALIZABLE_VALUE) {
                    modified = true;
                    continue;
                }

                modified |= sanitizedKey.modified || sanitizedValue.modified;
                sanitizedMap.put(sanitizedKey.value, sanitizedValue.value);
            }

            if (!modified) {
                return new SanitizationResult(data, false);
            }

            return new SanitizationResult(sanitizedMap, true);
        }

        if (data instanceof List<?>) {
            List<?> sourceList = (List<?>) data;
            List<Object> sanitizedList = new ArrayList<>(sourceList.size());
            boolean modified = false;
            for (Object value : sourceList) {
                SanitizationResult sanitizedValue = sanitizeByteData(value, problematicTypes);
                if (sanitizedValue.value == UNSERIALIZABLE_VALUE) {
                    sanitizedList.add(null);
                    modified = true;
                }
                else {
                    sanitizedList.add(sanitizedValue.value);
                    modified |= sanitizedValue.modified;
                }
            }

            if (!modified) {
                return new SanitizationResult(data, false);
            }

            return new SanitizationResult(sanitizedList, true);
        }

        if (data.getClass().isArray()) {
            Class<?> componentType = data.getClass().getComponentType();
            if (componentType.isPrimitive()) {
                return new SanitizationResult(data, false);
            }

            int length = Array.getLength(data);
            Object sanitizedArray = Array.newInstance(componentType, length);
            boolean modified = false;
            for (int index = 0; index < length; index++) {
                SanitizationResult sanitizedValue = sanitizeByteData(Array.get(data, index), problematicTypes);
                if (sanitizedValue.value == UNSERIALIZABLE_VALUE) {
                    Array.set(sanitizedArray, index, null);
                    modified = true;
                }
                else {
                    Array.set(sanitizedArray, index, sanitizedValue.value);
                    modified |= sanitizedValue.modified;
                }
            }

            if (!modified) {
                return new SanitizationResult(data, false);
            }

            return new SanitizationResult(sanitizedArray, true);
        }

        return new SanitizationResult(data, false);
    }

    private static void logSanitizedMetadata(Set<String> problematicTypes, String registryLocation) {
        StringBuilder message = new StringBuilder("CoreProtect sanitized unsupported metadata during byte serialization (types: ");
        message.append(String.join(", ", problematicTypes));
        if (registryLocation != null) {
            message.append(", missing key: ").append(registryLocation);
        }
        message.append(").");
        LOGGER.warning(message.toString());
    }

    public static ItemMeta deserializeItemMeta(Class<? extends ItemMeta> itemMetaClass, Map<String, Object> args) {
        try {
            org.bukkit.configuration.serialization.DelegateDeserialization delegate = itemMetaClass.getAnnotation(org.bukkit.configuration.serialization.DelegateDeserialization.class);
            return (ItemMeta) org.bukkit.configuration.serialization.ConfigurationSerialization.deserializeObject(args, delegate.value());
        }
        catch (Exception e) { // only display exception on development branch
            if (ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                e.printStackTrace();
            }
        }

        return null;
    }
    
    public static String getEnchantments(byte[] metadata, int type, int amount) {
        if (metadata == null) {
            return "";
        }

        ItemStack item = new ItemStack(MaterialUtils.getType(type), amount);
        item = (ItemStack) net.coreprotect.database.rollback.Rollback.populateItemStack(item, metadata)[2];
        String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : "";
        StringBuilder message = new StringBuilder(Color.ITALIC + displayName + Color.GREY);

        List<String> enchantments = ItemMetaHandler.getEnchantments(item, displayName);
        for (String enchantment : enchantments) {
            if (message.length() > 0) {
                message.append("\n");
            }
            message.append(enchantment);
        }

        if (!displayName.isEmpty()) {
            message.insert(0, enchantments.isEmpty() ? Color.WHITE : Color.AQUA);
        }
        else if (!enchantments.isEmpty()) {
            String name = StringUtils.capitalize(item.getType().name().replace("_", " "), true);
            message.insert(0, Color.AQUA + Color.ITALIC + name);
        }

        return message.toString();
    }
    
    public static Map<Integer, Object> serializeItemStackLegacy(ItemStack itemStack, String faceData, int slot) {
        Map<Integer, Object> result = new HashMap<>();
        Map<String, Object> itemMap = serializeItemStack(itemStack, faceData, slot);
        if (itemMap.size() > 1) {
            result.put(0, itemMap.get("0"));
            result.put(1, itemMap.get("1"));
        }

        return result;
    }

    public static ItemStack unserializeItemStackLegacy(Object value) {
        ItemStack result = null;
        if (value instanceof Map) {
            Map<String, Object> newMap = new HashMap<>();
            @SuppressWarnings("unchecked")
            Map<Integer, Object> itemMap = (Map<Integer, Object>) value;
            newMap.put("0", itemMap.get(0));
            newMap.put("1", itemMap.get(1));
            result = unserializeItemStack(newMap);
        }

        return result;
    }

    public static Map<String, Object> serializeItemStack(ItemStack itemStack, String faceData, int slot) {
        Map<String, Object> itemMap = new HashMap<>();
        if (itemStack != null && !itemStack.getType().equals(Material.AIR)) {
            ItemStack item = itemStack.clone();
            List<List<Map<String, Object>>> metadata = ItemMetaHandler.serialize(item, null, faceData, slot);
            item.setItemMeta(null);
            itemMap.put("0", item.serialize());
            itemMap.put("1", metadata);
        }

        return itemMap;
    }

    public static ItemStack unserializeItemStack(Object value) {
        ItemStack result = null;
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> itemMap = (Map<String, Object>) value;
            @SuppressWarnings("unchecked")
            ItemStack item = ItemStack.deserialize((Map<String, Object>) itemMap.get("0"));
            @SuppressWarnings("unchecked")
            List<List<Map<String, Object>>> metadata = (List<List<Map<String, Object>>>) itemMap.get("1");

            Object[] populatedStack = net.coreprotect.database.rollback.Rollback.populateItemStack(item, metadata);
            result = (ItemStack) populatedStack[2];
        }

        return result;
    }
} 
