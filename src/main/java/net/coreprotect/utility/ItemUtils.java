package net.coreprotect.utility;

import java.io.ByteArrayOutputStream;
import java.util.*;
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
    private static final int MAX_GIVABLE_ITEMS = 4096;
    private static final Map<Integer, ItemStack> GIVABLE_ITEMS = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<ItemStack, Integer> GIVABLE_ITEM_IDS = new HashMap<>();
    private static long nextGivableItemId;

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

    public static synchronized ItemStack getGivableItem(int id) {
        ItemStack item = GIVABLE_ITEMS.get(id);
        return item == null ? null : item.clone();
    }

    public static synchronized Integer makeGivableItem(ItemStack item) {
        if (item == null) {
            return null;
        }

        ItemStack snapshot = item.clone();
        Integer id = GIVABLE_ITEM_IDS.get(snapshot);
        if (id != null) {
            GIVABLE_ITEMS.get(id);
            return id;
        }
        if (nextGivableItemId > Integer.MAX_VALUE) {
            return null;
        }

        id = (int) nextGivableItemId++;
        GIVABLE_ITEMS.put(id, snapshot);
        GIVABLE_ITEM_IDS.put(snapshot, id);
        if (GIVABLE_ITEMS.size() > MAX_GIVABLE_ITEMS) {
            Iterator<Map.Entry<Integer, ItemStack>> iterator = GIVABLE_ITEMS.entrySet().iterator();
            GIVABLE_ITEM_IDS.remove(iterator.next().getValue());
            iterator.remove();
        }
        return id;
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
            ErrorReporter.report(e);
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

    public static ItemStack[] getSharedContainerState(ItemStack[] array, ItemStack[] previousState) {
        if (array == null) {
            return null;
        }
        if (previousState == null || previousState.length != array.length) {
            return getContainerState(array);
        }

        ItemStack[] result = new ItemStack[array.length];
        boolean unchanged = true;
        for (int i = 0; i < array.length; i++) {
            ItemStack itemStack = array[i];
            ItemStack previousItem = previousState[i];
            if (itemStack == null ? previousItem == null : itemStack.equals(previousItem)) {
                result[i] = previousItem;
            }
            else {
                result[i] = itemStack == null ? null : itemStack.clone();
                unchanged = false;
            }
        }

        return unchanged ? previousState : result;
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
            if (isEmptyContainerSlot(containerItem) || containerItem.isSimilar(item) && containerItem.getAmount() < getContainerStackLimit(containerItem, forceMaxStack)) {
                return true;
            }
        }

        return false;
    }

    public static long insertIntoContainerSnapshot(ItemStack[] contents, ItemStack item, long remaining, int inventoryMaxStackSize) {
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack existing = contents[slot];
            if (isEmptyContainerSlot(existing) || !existing.isSimilar(item)) {
                continue;
            }

            int amount = existing.getAmount();
            int capacity = getContainerStackLimit(existing, inventoryMaxStackSize) - amount;
            if (capacity > 0) {
                int added = (int) Math.min(remaining, capacity);
                ItemStack updated = existing.clone();
                updated.setAmount(amount + added);
                contents[slot] = updated;
                remaining -= added;
            }
        }
        if (remaining <= 0) {
            return remaining;
        }

        int stackLimit = getContainerStackLimit(item, inventoryMaxStackSize);
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            if (isEmptyContainerSlot(contents[slot])) {
                int added = (int) Math.min(remaining, stackLimit);
                ItemStack inserted = item.clone();
                inserted.setAmount(added);
                contents[slot] = inserted;
                remaining -= added;
            }
        }
        return remaining;
    }

    public static int getContainerStackLimit(ItemStack item, int inventoryMaxStackSize) {
        int stackLimit = item.getMaxStackSize();
        if (inventoryMaxStackSize > 0 && (inventoryMaxStackSize < stackLimit || stackLimit <= 0)) {
            return inventoryMaxStackSize;
        }
        return Math.max(1, stackLimit);
    }

    private static boolean isEmptyContainerSlot(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
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
                ErrorReporter.report(e);
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
            ErrorReporter.report(e);
        }
        return equipment;
    }

    public static ItemStack[] getItemFrameItem(ItemFrame entity) {
        ItemStack[] contents = null;
        try {
            contents = new ItemStack[] { entity.getItem() };
        }
        catch (Exception e) {
            ErrorReporter.report(e);
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
            Material baseMaterial = Material.getMaterial(material.name().replace("WALL_", ""));
            if (baseMaterial != null) {
                material = baseMaterial;
            }
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

                Set<Object> active = Collections.newSetFromMap(new IdentityHashMap<>());
                SanitizationResult sanitizationResult = sanitizeByteData(workingData, problematicTypes, active);
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

            if (sanitized && ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                LOGGER.warning("CoreProtect failed to serialize metadata after targeted type sanitization.");
            }
            ErrorReporter.report(failure, ConfigHandler.EDITION_BRANCH.contains("-dev"));
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
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        String registryLocation = null;
        Throwable current = throwable;
        while (current != null && visited.add(current)) {
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

    private static boolean isProblematicType(Object value, Set<String> problematicTypes) {
        Class<?> valueType = value.getClass();
        if (value instanceof String || value instanceof Boolean || value instanceof Character || value instanceof UUID || valueType.isArray()
                || (value instanceof Number && valueType.getModule() == Number.class.getModule())) {
            return false;
        }

        String typeToken = normalizeTypeToken(valueType.getSimpleName());
        if (typeToken == null) {
            return false;
        }

        for (String problematicType : problematicTypes) {
            if (typeToken.startsWith(problematicType)) {
                return true;
            }
        }

        return false;
    }

    private static SanitizationResult sanitizeByteData(Object data, Set<String> problematicTypes, Set<Object> active) {
        if (data == null) {
            return new SanitizationResult(null, false);
        }

        if (!(data instanceof Map<?, ?>) && !(data instanceof List<?>) && !(data instanceof Set<?>) && !(data instanceof Object[])) {
            return isProblematicType(data, problematicTypes) ? new SanitizationResult(UNSERIALIZABLE_VALUE, true) : new SanitizationResult(data, false);
        }
        if (!active.add(data)) {
            return new SanitizationResult(UNSERIALIZABLE_VALUE, true);
        }

        try {
            return sanitizeByteDataContainer(data, problematicTypes, active);
        }
        finally {
            active.remove(data);
        }
    }

    private static SanitizationResult sanitizeByteDataContainer(Object data, Set<String> problematicTypes, Set<Object> active) {
        if (data instanceof Map<?, ?>) {
            Map<?, ?> sourceMap = (Map<?, ?>) data;
            Map<Object, Object> sanitizedMap = new LinkedHashMap<>();
            boolean modified = false;
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                SanitizationResult sanitizedKey = sanitizeByteData(entry.getKey(), problematicTypes, active);
                SanitizationResult sanitizedValue = sanitizeByteData(entry.getValue(), problematicTypes, active);
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
                SanitizationResult sanitizedValue = sanitizeByteData(value, problematicTypes, active);
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

        if (data instanceof Set<?>) {
            Set<Object> sanitizedSet = new LinkedHashSet<>();
            boolean modified = false;
            for (Object value : (Set<?>) data) {
                SanitizationResult sanitizedValue = sanitizeByteData(value, problematicTypes, active);
                if (sanitizedValue.value != UNSERIALIZABLE_VALUE) {
                    sanitizedSet.add(sanitizedValue.value);
                }
                modified |= sanitizedValue.modified;
            }
            return new SanitizationResult(modified ? sanitizedSet : data, modified);
        }

        Object[] sourceArray = (Object[]) data;
        Object[] sanitizedArray = sourceArray.clone();
        Class<?> componentType = sourceArray.getClass().getComponentType();
        boolean modified = false;
        for (int index = 0; index < sourceArray.length; index++) {
            SanitizationResult sanitizedValue = sanitizeByteData(sourceArray[index], problematicTypes, active);
            Object value = sanitizedValue.value == UNSERIALIZABLE_VALUE ? null : sanitizedValue.value;
            if (value != null && !componentType.isInstance(value)) {
                sanitizedArray = Arrays.copyOf(sanitizedArray, sanitizedArray.length, Object[].class);
                componentType = Object.class;
            }
            sanitizedArray[index] = value;
            modified |= sanitizedValue.modified;
        }
        return new SanitizationResult(modified ? sanitizedArray : data, modified);
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
        catch (Exception e) { // only print exception on development branch
            ErrorReporter.report(e, ConfigHandler.EDITION_BRANCH.contains("-dev"));
        }

        return null;
    }

    public static ItemStack getItemStack(byte[] metadata, int type, int amount) {
        if (metadata == null || amount <= 0) {
            return null;
        }

        Material material = MaterialUtils.getType(type);
        if (material == null || BlockUtils.isAir(material) || !material.isItem()) {
            return null;
        }

        ItemStack item = new ItemStack(material, amount);
        return (ItemStack) net.coreprotect.database.rollback.Rollback.populateItemStack(item, metadata)[2];
    }

    public static String getEnchantments(byte[] metadata, int type, int amount) {
        ItemStack item = getItemStack(metadata, type, amount);
        if (item == null || !item.hasItemMeta()) {
            return "";
        }

        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return "";
        }

        boolean customName = itemMeta.hasDisplayName();
        String displayName = customName ? itemMeta.getDisplayName() : BukkitAdapter.ADAPTER.getItemName(itemMeta);
        List<String> enchantments = ItemMetaHandler.getEnchantments(item, displayName);
        if (displayName.isEmpty() && enchantments.isEmpty()) {
            return "";
        }

        StringBuilder message = new StringBuilder(enchantments.isEmpty() ? Color.WHITE : Color.AQUA);
        if (displayName.isEmpty()) {
            displayName = StringUtils.capitalize(item.getType().name().replace("_", " "), true);
        }
        else if (customName) {
            message.append(Color.ITALIC);
        }
        message.append(displayName).append(Color.GREY);

        for (String enchantment : enchantments) {
            message.append('\n').append(enchantment);
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
        if (!(value instanceof Map)) {
            return null;
        }

        Map<?, ?> itemMap = (Map<?, ?>) value;
        Object stackData = itemMap.get("0");
        if (!(stackData instanceof Map) || ((Map<?, ?>) stackData).isEmpty()) {
            return null;
        }

        @SuppressWarnings("unchecked")
        ItemStack item = ItemStack.deserialize((Map<String, Object>) stackData);
        Object metadata = itemMap.get("1");
        return metadata instanceof List ? (ItemStack) net.coreprotect.database.rollback.Rollback.populateItemStack(item, metadata)[2] : item;
    }
}
