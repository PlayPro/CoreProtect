package net.coreprotect.utility.serialize;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.potion.PotionEffectType;

import net.coreprotect.bukkit.BukkitAdapter;

public final class BlockMetaCodec {

    private enum Kind {
        COMMAND(1),
        BANNER(2),
        SHULKER(3),
        GENERIC(4);

        private final int code;

        Kind(int code) {
            this.code = code;
        }

        private static Kind fromCode(int code) {
            for (Kind kind : values()) {
                if (kind.code == code) {
                    return kind;
                }
            }
            return null;
        }
    }

    private static final String DESCRIPTION = "Block metadata";
    private static final int MAGIC_FIRST = 'C';
    private static final int MAGIC_SECOND = 'B';
    private static final int VERSION = 1;

    private static final int NULL = 0;
    private static final int FALSE = 1;
    private static final int TRUE = 2;
    private static final int INTEGER = 3;
    private static final int STRING = 4;
    private static final int LIST = 5;
    private static final int MAP = 6;
    private static final int BYTE = 7;
    private static final int SHORT = 8;
    private static final int LONG = 9;
    private static final int FLOAT = 10;
    private static final int DOUBLE = 11;
    private static final int SPECIAL_DOUBLE = 12;
    private static final int UUID_VALUE = 13;
    private static final int BYTE_ARRAY = 14;
    private static final int INT_ARRAY = 15;
    private static final int LONG_ARRAY = 16;
    private static final int SET = 17;
    private static final int DYE_COLOR = 18;
    private static final int FIREWORK_TYPE = 19;
    private static final int CONFIGURATION = 20;
    private static final int ENUM = 21;
    private static final int DOUBLE_ZERO = 22;
    private static final int DOUBLE_ONE = 23;
    private static final int DOUBLE_INTEGER = 24;
    private static final int DOUBLE_FLOAT = 25;
    private static final int LEGACY_ITEM = 26;
    private static final int STRING_KEY_ITEM = 27;

    private static final int ITEM_DATA_VERSION = 1;
    private static final int ITEM_SCHEMA_VERSION = 1 << 1;
    private static final int ITEM_METADATA = 1 << 2;
    private static final int ITEM_BASE_EXTENSIONS = 1 << 3;
    private static final int ITEM_EXTENSIONS = 1 << 4;
    private static final int ITEM_FLAGS = ITEM_DATA_VERSION | ITEM_SCHEMA_VERSION | ITEM_METADATA | ITEM_BASE_EXTENSIONS | ITEM_EXTENSIONS;

    private static final int STRING_LITERAL = 0;
    private static final int STRING_DICTIONARY_VALUE = 1;
    private static final int STRING_MINECRAFT = 2;
    private static final int STRING_REFERENCE = 3;
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final String BUKKIT_PACKAGE = "org.bukkit.";
    private static final String REMOVED_FOOD_EFFECT_ALIAS = "FoodEffect";
    private static final String[] DYE_COLORS = {
            "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
            "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
    };
    private static final String[] FIREWORK_TYPES = {
            "BALL", "BALL_LARGE", "STAR", "CREEPER", "BURST"
    };
    private static final String[] STRING_DICTIONARY = {
            "meta-type",
            "enchants",
            "repair-cost",
            "display-name",
            "Damage",
            "potion-type",
            "pattern",
            "stored-enchants",
            "color",
            "power",
            "material",
            "trim",
            "ALPHA",
            "BLUE",
            "GREEN",
            "RED",
            "flicker",
            "trail",
            "type",
            "map-id",
            "internal",
            "blockMaterial",
            "item-name",
            "unhandled",
            "pages",
            "author",
            "title",
            "generation",
            "lore",
            "custom-model-data",
            "unbreakable",
            "item-flags",
            "attribute-modifiers",
            "modifiers",
            "amount",
            "operation",
            "key",
            "slot",
            "facing",
            "base-color",
            "minecraft:unbreaking",
            "minecraft:mending",
            "minecraft:efficiency",
            "minecraft:silk_touch",
            "minecraft:protection",
            "minecraft:fortune",
            "minecraft:sharpness",
            "minecraft:looting",
            "minecraft:aqua_affinity",
            "minecraft:respiration",
            "minecraft:feather_falling",
            "minecraft:riptide",
            "minecraft:sweeping_edge",
            "minecraft:impaling",
            "minecraft:loyalty",
            "minecraft:power",
            "minecraft:channeling",
            "minecraft:smite",
            "minecraft:depth_strider",
            "minecraft:flame",
            "minecraft:luck_of_the_sea",
            "minecraft:lure",
            "minecraft:punch",
            "minecraft:thorns"
    };
    private static final Map<String, Integer> STRING_IDENTIFIERS = stringIdentifiers();

    private BlockMetaCodec() {
        throw new IllegalStateException("Codec class");
    }

    public static byte[] encode(List<Object> metadata) {
        Objects.requireNonNull(metadata, "metadata");
        Kind kind = kind(metadata);
        BinaryOutput output = new BinaryOutput();
        output.write(MAGIC_FIRST);
        output.write(MAGIC_SECOND);
        output.write(VERSION);
        output.write(kind.code);
        switch (kind) {
            case COMMAND:
                encodeCommand(output, metadata);
                break;
            case BANNER:
                encodeBanner(output, metadata);
                break;
            case SHULKER:
                encodeShulker(output, metadata);
                break;
            case GENERIC:
                encodeValue(output, metadata, 1);
                break;
            default:
                throw new IllegalStateException("Unsupported block metadata kind " + kind);
        }
        return output.toByteArray();
    }

    public static List<Object> decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length > BinaryCodecSupport.MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("Block metadata exceeds the maximum encoded size");
        }

        try {
            BinaryInput input = new BinaryInput(encoded);
            Kind kind = input.readHeader();
            List<Object> metadata;
            switch (kind) {
                case COMMAND:
                    metadata = input.readCommand();
                    break;
                case BANNER:
                    metadata = input.readBanner();
                    break;
                case SHULKER:
                    metadata = input.readShulker();
                    break;
                case GENERIC:
                    Object value = input.readValue(1);
                    if (!(value instanceof List<?>)) {
                        throw new IllegalArgumentException("Block metadata root is not a list");
                    }
                    @SuppressWarnings("unchecked")
                    List<Object> values = (List<Object>) value;
                    metadata = values;
                    break;
                default:
                    throw new IllegalStateException("Unsupported block metadata kind " + kind);
            }
            input.requireEnd();
            return metadata;
        }
        catch (StackOverflowError error) {
            throw new IllegalArgumentException("Block metadata exceeds the maximum nesting depth", error);
        }
    }

    public static byte[] canonicalize(byte[] encoded) {
        return encode(decode(encoded));
    }

    public static boolean isEncoded(byte[] data) {
        return data != null && data.length >= 2
                && Byte.toUnsignedInt(data[0]) == MAGIC_FIRST
                && Byte.toUnsignedInt(data[1]) == MAGIC_SECOND;
    }

    private static Kind kind(List<Object> metadata) {
        if (!metadata.isEmpty()) {
            boolean command = true;
            for (Object value : metadata) {
                if (!(value instanceof String)) {
                    command = false;
                    break;
                }
            }
            if (command) {
                return Kind.COMMAND;
            }
            if (isBanner(metadata)) {
                return Kind.BANNER;
            }
            if (isShulker(metadata)) {
                return Kind.SHULKER;
            }
        }
        return Kind.GENERIC;
    }

    private static boolean isBanner(List<Object> metadata) {
        if (!(metadata.get(0) instanceof DyeColor)) {
            return false;
        }
        for (int index = 1; index < metadata.size(); index++) {
            if (!(metadata.get(index) instanceof Map<?, ?>)) {
                return false;
            }
            Map<?, ?> pattern = (Map<?, ?>) metadata.get(index);
            if (!(pattern.get("color") instanceof String) || colorIdentifier((String) pattern.get("color")) < 0
                    || !(pattern.get("pattern") instanceof String)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isShulker(List<Object> metadata) {
        for (Object value : metadata) {
            if (!(value instanceof Map<?, ?>) || itemKeyType((Map<?, ?>) value) != LEGACY_ITEM) {
                return false;
            }
        }
        return true;
    }

    private static void encodeCommand(BinaryOutput output, List<Object> metadata) {
        output.writeLength(metadata.size());
        for (Object value : metadata) {
            output.writeString((String) value);
        }
    }

    private static void encodeBanner(BinaryOutput output, List<Object> metadata) {
        output.write(colorIdentifier(((DyeColor) metadata.get(0)).name()));
        output.writeLength(metadata.size() - 1);
        for (int index = 1; index < metadata.size(); index++) {
            Map<?, ?> pattern = (Map<?, ?>) metadata.get(index);
            output.write(colorIdentifier((String) pattern.get("color")));
            output.writeString((String) pattern.get("pattern"));
            Map<Object, Object> extensions = withoutKeys(pattern, "color", "pattern");
            output.write(extensions.isEmpty() ? 0 : 1);
            if (!extensions.isEmpty()) {
                encodeValue(output, extensions, 2);
            }
        }
    }

    private static void encodeShulker(BinaryOutput output, List<Object> metadata) {
        output.writeLength(metadata.size());
        for (Object value : metadata) {
            encodeItem(output, (Map<?, ?>) value, LEGACY_ITEM, 1);
        }
    }

    private static void encodeValue(BinaryOutput output, Object value, int depth) {
        BinaryCodecSupport.requireDepth(DESCRIPTION, depth);
        if (value == null) {
            output.write(NULL);
        }
        else if (value instanceof Boolean) {
            output.write((Boolean) value ? TRUE : FALSE);
        }
        else if (value instanceof String) {
            output.write(STRING);
            output.writeString((String) value);
        }
        else if (value instanceof Integer) {
            output.write(INTEGER);
            output.writeZigZag((Integer) value);
        }
        else if (value instanceof Double) {
            encodeDouble(output, (Double) value);
        }
        else if (value instanceof Byte) {
            output.write(BYTE);
            output.write((Byte) value);
        }
        else if (value instanceof Short) {
            output.write(SHORT);
            output.writeZigZag((Short) value);
        }
        else if (value instanceof Long) {
            output.write(LONG);
            output.writeZigZag((Long) value);
        }
        else if (value instanceof Float) {
            output.write(FLOAT);
            output.writeInt(Float.floatToIntBits((Float) value));
        }
        else if (value instanceof UUID) {
            UUID uuid = (UUID) value;
            output.write(UUID_VALUE);
            output.writeLong(uuid.getMostSignificantBits());
            output.writeLong(uuid.getLeastSignificantBits());
        }
        else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            output.write(BYTE_ARRAY);
            output.writeByteLength(bytes.length);
            output.write(bytes, 0, bytes.length);
        }
        else if (value instanceof int[]) {
            int[] values = (int[]) value;
            output.write(INT_ARRAY);
            output.writeLength(values.length);
            for (int item : values) {
                output.writeZigZag(item);
            }
        }
        else if (value instanceof long[]) {
            long[] values = (long[]) value;
            output.write(LONG_ARRAY);
            output.writeLength(values.length);
            for (long item : values) {
                output.writeZigZag(item);
            }
        }
        else if (value instanceof DyeColor) {
            output.write(DYE_COLOR);
            output.write(colorIdentifier(((DyeColor) value).name()));
        }
        else if (value instanceof FireworkEffect.Type) {
            output.write(FIREWORK_TYPE);
            output.write(fireworkIdentifier(((FireworkEffect.Type) value).name()));
        }
        else if (value instanceof NamespacedKey) {
            output.write(STRING);
            output.writeString(value.toString());
        }
        else if (value instanceof ConfigurationSerializable) {
            encodeConfigurationValue(output, (ConfigurationSerializable) value, depth);
        }
        else if (value instanceof Keyed || value instanceof Sound || value instanceof PotionEffectType) {
            output.write(STRING);
            output.writeString(registryKey(value));
        }
        else if (value instanceof Enum<?>) {
            encodeEnum(output, (Enum<?>) value);
        }
        else if (value instanceof Set<?>) {
            encodeSet(output, (Set<?>) value, depth);
        }
        else if (value instanceof Collection<?>) {
            Collection<?> values = (Collection<?>) value;
            output.write(LIST);
            output.writeLength(values.size());
            for (Object item : values) {
                encodeValue(output, item, depth + 1);
            }
        }
        else if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            int itemType = itemKeyType(map);
            if (itemType != 0) {
                output.write(itemType);
                encodeItem(output, map, itemType, depth);
            }
            else {
                output.write(MAP);
                encodeMapBody(output, map, depth);
            }
        }
        else {
            throw new IllegalArgumentException("Unsupported block metadata value " + value.getClass().getName());
        }
    }

    private static void encodeDouble(BinaryOutput output, double value) {
        if (!Double.isFinite(value)) {
            output.write(SPECIAL_DOUBLE);
            output.write(Double.isNaN(value) ? 0 : value > 0 ? 1 : 2);
            return;
        }
        switch (BinaryCodecSupport.compactDoubleType(value)) {
            case ZERO:
                output.write(DOUBLE_ZERO);
                break;
            case ONE:
                output.write(DOUBLE_ONE);
                break;
            case INTEGER:
                output.write(DOUBLE_INTEGER);
                output.writeZigZag((long) value);
                break;
            case FLOAT:
                output.write(DOUBLE_FLOAT);
                output.writeInt(Float.floatToIntBits((float) value));
                break;
            case RAW:
                output.write(DOUBLE);
                output.writeLong(Double.doubleToLongBits(value));
                break;
            default:
                throw new IllegalStateException("Unsupported compact double type");
        }
    }

    private static void encodeConfigurationValue(BinaryOutput output, ConfigurationSerializable value, int depth) {
        Class<? extends ConfigurationSerializable> type = value.getClass();
        String alias = ConfigurationSerialization.getAlias(type);
        if (alias == null || alias.isEmpty()) {
            throw new IllegalArgumentException("Configuration-serializable block metadata type has no alias " + type.getName());
        }
        requirePlatformType(type);
        Map<String, Object> serialized = value.serialize();
        if (serialized == null) {
            throw new IllegalArgumentException("Configuration-serializable block metadata value is null " + alias);
        }
        output.write(CONFIGURATION);
        output.writeString(alias);
        encodeStringMapBody(output, serialized, depth);
    }

    private static void encodeEnum(BinaryOutput output, Enum<?> value) {
        Class<?> type = value.getDeclaringClass();
        if (!isBukkitApiType(type)) {
            throw new IllegalArgumentException("Disallowed block metadata enum " + type.getName());
        }
        output.write(ENUM);
        output.writeString(type.getName().substring(BUKKIT_PACKAGE.length()));
        output.writeString(value.name());
    }

    private static void encodeSet(BinaryOutput output, Set<?> values, int depth) {
        List<EncodedValue> sorted = new ArrayList<>(values.size());
        for (Object value : values) {
            sorted.add(new EncodedValue(value, encodeIsolated(value, depth + 1)));
        }
        sorted.sort(Comparator.comparing(EncodedValue::getEncoded, BinaryCodecSupport::compareBytes));
        output.write(SET);
        output.writeLength(sorted.size());
        byte[] previous = null;
        for (EncodedValue value : sorted) {
            if (previous != null && BinaryCodecSupport.compareBytes(previous, value.encoded) == 0) {
                throw new IllegalArgumentException("Block metadata set contains duplicate encoded values");
            }
            encodeValue(output, value.value, depth + 1);
            previous = value.encoded;
        }
    }

    private static void encodeMapBody(BinaryOutput output, Map<?, ?> values, int depth) {
        List<EncodedEntry> entries = new ArrayList<>(values.size());
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            entries.add(new EncodedEntry(entry.getKey(), entry.getValue(), encodeIsolated(entry.getKey(), depth + 1)));
        }
        entries.sort(Comparator.comparing(EncodedEntry::getEncodedKey, BinaryCodecSupport::compareBytes));
        output.writeLength(entries.size());
        byte[] previous = null;
        for (EncodedEntry entry : entries) {
            if (previous != null && BinaryCodecSupport.compareBytes(previous, entry.encodedKey) == 0) {
                throw new IllegalArgumentException("Block metadata map contains duplicate encoded keys");
            }
            encodeValue(output, entry.key, depth + 1);
            encodeValue(output, entry.value, depth + 1);
            previous = entry.encodedKey;
        }
    }

    private static void encodeStringMapBody(BinaryOutput output, Map<?, ?> values, int depth) {
        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = mapKey(entry.getKey());
            BinaryCodecSupport.validateUnicode(DESCRIPTION, key);
            if (sorted.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate block metadata map key " + key);
            }
            sorted.put(key, entry.getValue());
        }
        output.writeLength(sorted.size());
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            output.writeString(entry.getKey());
            encodeValue(output, entry.getValue(), depth + 1);
        }
    }

    private static byte[] encodeIsolated(Object value, int depth) {
        BinaryOutput output = new BinaryOutput();
        encodeValue(output, value, depth);
        return output.toByteArray();
    }

    private static int itemKeyType(Map<?, ?> item) {
        boolean legacy = item.containsKey(0);
        boolean stringKey = item.containsKey("0");
        if (legacy == stringKey) {
            return 0;
        }
        Object baseValue = item.get(legacy ? 0 : "0");
        if (!(baseValue instanceof Map<?, ?>)) {
            return 0;
        }
        Map<?, ?> base = (Map<?, ?>) baseValue;
        if (!(base.get("id") instanceof String) || !(base.get("count") instanceof Integer)) {
            return 0;
        }
        if (base.containsKey("DataVersion") && !(base.get("DataVersion") instanceof Integer)) {
            return 0;
        }
        if (base.containsKey("schema_version") && !(base.get("schema_version") instanceof Integer)) {
            return 0;
        }
        return legacy ? LEGACY_ITEM : STRING_KEY_ITEM;
    }

    private static void encodeItem(BinaryOutput output, Map<?, ?> item, int itemType, int depth) {
        BinaryCodecSupport.requireDepth(DESCRIPTION, depth);
        Object baseKey = itemType == LEGACY_ITEM ? 0 : "0";
        Object metadataKey = itemType == LEGACY_ITEM ? 1 : "1";
        Map<?, ?> base = (Map<?, ?>) item.get(baseKey);
        Map<Object, Object> baseExtensions = withoutKeys(base, "id", "count", "DataVersion", "schema_version");
        Map<Object, Object> extensions = withoutKeys(item, baseKey, metadataKey);
        int flags = 0;
        if (base.containsKey("DataVersion")) {
            flags |= ITEM_DATA_VERSION;
        }
        if (base.containsKey("schema_version")) {
            flags |= ITEM_SCHEMA_VERSION;
        }
        if (item.containsKey(metadataKey)) {
            flags |= ITEM_METADATA;
        }
        if (!baseExtensions.isEmpty()) {
            flags |= ITEM_BASE_EXTENSIONS;
        }
        if (!extensions.isEmpty()) {
            flags |= ITEM_EXTENSIONS;
        }

        output.writeString((String) base.get("id"));
        output.writeZigZag((Integer) base.get("count"));
        output.write(flags);
        if ((flags & ITEM_DATA_VERSION) != 0) {
            output.writeZigZag((Integer) base.get("DataVersion"));
        }
        if ((flags & ITEM_SCHEMA_VERSION) != 0) {
            output.writeZigZag((Integer) base.get("schema_version"));
        }
        if ((flags & ITEM_BASE_EXTENSIONS) != 0) {
            encodeValue(output, baseExtensions, depth + 1);
        }
        if ((flags & ITEM_METADATA) != 0) {
            encodeValue(output, item.get(metadataKey), depth + 1);
        }
        if ((flags & ITEM_EXTENSIONS) != 0) {
            encodeValue(output, extensions, depth + 1);
        }
    }

    private static Map<Object, Object> withoutKeys(Map<?, ?> values, Object... excluded) {
        Set<Object> excludedKeys = new LinkedHashSet<>(Arrays.asList(excluded));
        Map<Object, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!excludedKeys.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static Object parseConfigurationValue(String alias, Map<String, Object> serialized) {
        Class<? extends ConfigurationSerializable> type = ConfigurationSerialization.getClassByAlias(alias);
        if (type == null) {
            if (REMOVED_FOOD_EFFECT_ALIAS.equals(alias)) {
                return serialized;
            }
            throw new IllegalArgumentException("Unknown configuration-serializable block metadata type " + alias);
        }
        requirePlatformType(type);
        try {
            Object result = ConfigurationSerialization.deserializeObject(serialized, type);
            if (result == null) {
                throw new IllegalArgumentException("Unable to deserialize configuration-serializable block metadata type " + alias);
            }
            return result;
        }
        catch (RuntimeException | LinkageError exception) {
            throw new IllegalArgumentException("Unable to deserialize configuration-serializable block metadata type " + alias, exception);
        }
    }

    private static Object parseEnum(String className, String value) {
        String qualifiedClassName = BUKKIT_PACKAGE + className;
        try {
            Class<?> type = Class.forName(qualifiedClassName, false, BlockMetaCodec.class.getClassLoader());
            if (!isBukkitApiType(type) || !type.isEnum()) {
                throw new IllegalArgumentException("Disallowed block metadata enum " + qualifiedClassName);
            }
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Object result = Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), value);
            return result;
        }
        catch (ClassNotFoundException | LinkageError | SecurityException exception) {
            throw new IllegalArgumentException("Unknown block metadata enum " + qualifiedClassName, exception);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + qualifiedClassName + " block metadata enum value " + value, exception);
        }
    }

    private static String mapKey(Object value) {
        if (value instanceof String || value instanceof NamespacedKey) {
            return value.toString();
        }
        if (value instanceof Keyed || value instanceof Sound || value instanceof PotionEffectType) {
            return registryKey(value);
        }
        throw new IllegalArgumentException("Unsupported block metadata map key " + (value == null ? "null" : value.getClass().getName()));
    }

    private static String registryKey(Object value) {
        try {
            Object key = BukkitAdapter.ADAPTER.getRegistryKey(value);
            if (key instanceof String) {
                return requireKey((String) key);
            }
        }
        catch (RuntimeException | LinkageError exception) {
        }

        if (value instanceof Keyed) {
            NamespacedKey key = ((Keyed) value).getKey();
            if (key != null) {
                return key.toString();
            }
        }
        if (value instanceof Sound && value instanceof Enum<?>) {
            return requireKey("minecraft:" + ((Enum<?>) value).name().toLowerCase(Locale.ROOT).replace('_', '.'));
        }
        if (value instanceof PotionEffectType) {
            @SuppressWarnings({ "deprecation", "removal" })
            String name = ((PotionEffectType) value).getName();
            if (name != null && !name.isEmpty()) {
                return requireKey("minecraft:" + name.toLowerCase(Locale.ROOT));
            }
        }
        throw new IllegalArgumentException("Unable to determine registry key for block metadata type " + value.getClass().getName());
    }

    private static boolean isBukkitApiType(Class<?> type) {
        String name = type.getName();
        return name.startsWith(BUKKIT_PACKAGE)
                && !name.startsWith("org.bukkit.craftbukkit.")
                && type.getClassLoader() == ConfigurationSerializable.class.getClassLoader();
    }

    private static void requirePlatformType(Class<?> type) {
        if (type.getClassLoader() != ConfigurationSerializable.class.getClassLoader()) {
            throw new IllegalArgumentException("Disallowed configuration-serializable block metadata type " + type.getName());
        }
    }

    @SuppressWarnings({ "deprecation", "removal" })
    private static NamespacedKey parseNamespacedKey(String value) {
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1 || value.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException("Invalid namespaced key " + value);
        }
        return new NamespacedKey(value.substring(0, separator), value.substring(separator + 1));
    }

    private static String requireKey(String value) {
        parseNamespacedKey(value);
        return value;
    }

    private static Map<String, Integer> stringIdentifiers() {
        Map<String, Integer> identifiers = new HashMap<>();
        for (int index = 0; index < STRING_DICTIONARY.length; index++) {
            if (identifiers.put(STRING_DICTIONARY[index], index) != null) {
                throw new IllegalStateException("Duplicate block metadata dictionary value " + STRING_DICTIONARY[index]);
            }
        }
        return Collections.unmodifiableMap(identifiers);
    }

    private static int colorIdentifier(String value) {
        for (int index = 0; index < DYE_COLORS.length; index++) {
            if (DYE_COLORS[index].equals(value)) {
                return index;
            }
        }
        return -1;
    }

    private static int fireworkIdentifier(String value) {
        for (int index = 0; index < FIREWORK_TYPES.length; index++) {
            if (FIREWORK_TYPES[index].equals(value)) {
                return index;
            }
        }
        return -1;
    }

    private static final class EncodedValue {
        private final Object value;
        private final byte[] encoded;

        private EncodedValue(Object value, byte[] encoded) {
            this.value = value;
            this.encoded = encoded;
        }

        private byte[] getEncoded() {
            return encoded;
        }
    }

    private static final class EncodedEntry {
        private final Object key;
        private final Object value;
        private final byte[] encodedKey;

        private EncodedEntry(Object key, Object value, byte[] encodedKey) {
            this.key = key;
            this.value = value;
            this.encodedKey = encodedKey;
        }

        private byte[] getEncodedKey() {
            return encodedKey;
        }
    }

    private static final class BinaryOutput extends BinaryCodecSupport.Output {
        private final Map<String, Integer> localStringIdentifiers = new HashMap<>();

        private BinaryOutput() {
            super(DESCRIPTION, 256);
        }

        private void writeString(String value) {
            BinaryCodecSupport.validateUnicode(DESCRIPTION, value);
            Integer dictionaryIdentifier = STRING_IDENTIFIERS.get(value);
            if (dictionaryIdentifier != null) {
                writeVarUnsigned(((long) dictionaryIdentifier << 2) | STRING_DICTIONARY_VALUE);
                return;
            }

            byte[] bytes;
            int stringType;
            if (value.startsWith(MINECRAFT_PREFIX) && value.length() > MINECRAFT_PREFIX.length()) {
                bytes = value.substring(MINECRAFT_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
                stringType = STRING_MINECRAFT;
            }
            else {
                bytes = value.getBytes(StandardCharsets.UTF_8);
                stringType = STRING_LITERAL;
            }
            long literalCode = ((long) bytes.length << 2) | stringType;
            int literalSize = BinaryCodecSupport.unsignedVarSize(literalCode) + bytes.length;
            Integer localIdentifier = localStringIdentifiers.get(value);
            if (localIdentifier != null) {
                long referenceCode = ((long) localIdentifier << 2) | STRING_REFERENCE;
                if (BinaryCodecSupport.unsignedVarSize(referenceCode) < literalSize) {
                    writeVarUnsigned(referenceCode);
                    return;
                }
            }

            writeVarUnsigned(literalCode);
            write(bytes, 0, bytes.length);
            if (!value.isEmpty() && localIdentifier == null) {
                if (localStringIdentifiers.size() >= BinaryCodecSupport.MAX_CONTAINER_LENGTH) {
                    throw new IllegalArgumentException("Block metadata string table exceeds the maximum size");
                }
                localStringIdentifiers.put(value, localStringIdentifiers.size());
            }
        }

    }

    private static final class BinaryInput extends BinaryCodecSupport.Input {
        private final List<String> localStrings = new ArrayList<>();
        private final Map<String, Integer> localStringIdentifiers = new HashMap<>();

        private BinaryInput(byte[] data) {
            super(data, DESCRIPTION);
        }

        private Kind readHeader() {
            if (readUnsignedByte() != MAGIC_FIRST || readUnsignedByte() != MAGIC_SECOND) {
                throw new IllegalArgumentException("Block metadata does not use the CoreProtect binary format");
            }
            int version = readUnsignedByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported block metadata format version " + version);
            }
            int kindCode = readUnsignedByte();
            Kind kind = Kind.fromCode(kindCode);
            if (kind == null) {
                throw new IllegalArgumentException("Unsupported block metadata kind " + kindCode);
            }
            return kind;
        }

        private List<Object> readCommand() {
            int length = readLength("command list");
            List<Object> values = new ArrayList<>(Math.min(length, 1024));
            for (int index = 0; index < length; index++) {
                values.add(readString());
            }
            return values;
        }

        private List<Object> readBanner() {
            List<Object> values = new ArrayList<>();
            values.add(readDyeColor());
            int length = readLength("banner pattern list");
            for (int index = 0; index < length; index++) {
                Map<Object, Object> pattern = new LinkedHashMap<>();
                pattern.put("color", readDyeColor().name());
                pattern.put("pattern", readString());
                int extensions = readUnsignedByte();
                if (extensions == 1) {
                    Map<?, ?> extensionValues = requireMap(readValue(2), "banner pattern extensions");
                    for (Map.Entry<?, ?> entry : extensionValues.entrySet()) {
                        if (pattern.containsKey(entry.getKey())) {
                            throw new IllegalArgumentException("Block metadata banner pattern extension replaces a required value");
                        }
                        pattern.put(entry.getKey(), entry.getValue());
                    }
                }
                else if (extensions != 0) {
                    throw new IllegalArgumentException("Unsupported block metadata banner pattern flags " + extensions);
                }
                values.add(pattern);
            }
            return values;
        }

        private List<Object> readShulker() {
            int length = readLength("shulker item list");
            List<Object> values = new ArrayList<>(Math.min(length, 1024));
            for (int index = 0; index < length; index++) {
                values.add(readItem(LEGACY_ITEM, 1));
            }
            return values;
        }

        private Object readValue(int depth) {
            BinaryCodecSupport.requireDepth(DESCRIPTION, depth);
            int type = readUnsignedByte();
            switch (type) {
                case NULL:
                    return null;
                case FALSE:
                    return false;
                case TRUE:
                    return true;
                case INTEGER:
                    return checkedInteger(readZigZag());
                case STRING:
                    return readString();
                case LIST:
                    return readList(depth);
                case MAP:
                    return readMap(depth);
                case BYTE:
                    return (byte) readUnsignedByte();
                case SHORT:
                    return checkedShort(readZigZag());
                case LONG:
                    return readZigZag();
                case FLOAT:
                    return readFloat();
                case DOUBLE:
                    return readRawDouble();
                case SPECIAL_DOUBLE:
                    return readSpecialDouble();
                case UUID_VALUE:
                    return new UUID(readLong(), readLong());
                case BYTE_ARRAY:
                    return readBytes(readByteLength());
                case INT_ARRAY:
                    return readIntArray();
                case LONG_ARRAY:
                    return readLongArray();
                case SET:
                    return readSet(depth);
                case DYE_COLOR:
                    return readDyeColor();
                case FIREWORK_TYPE:
                    return readFireworkType();
                case CONFIGURATION:
                    return parseConfigurationValue(readString(), readStringMapBody(depth));
                case ENUM:
                    return parseEnum(readString(), readString());
                case DOUBLE_ZERO:
                    return 0.0D;
                case DOUBLE_ONE:
                    return 1.0D;
                case DOUBLE_INTEGER:
                    return readIntegralDouble();
                case DOUBLE_FLOAT:
                    return readFloatDouble();
                case LEGACY_ITEM:
                case STRING_KEY_ITEM:
                    return readItem(type, depth);
                default:
                    throw new IllegalArgumentException("Unsupported block metadata type " + type);
            }
        }

        private List<Object> readList(int depth) {
            int length = readLength("list");
            List<Object> values = new ArrayList<>(Math.min(length, 1024));
            for (int index = 0; index < length; index++) {
                values.add(readValue(depth + 1));
            }
            return values;
        }

        private Map<Object, Object> readMap(int depth) {
            int length = readLength("map");
            Map<Object, Object> values = new LinkedHashMap<>(Math.min(length, 1024));
            byte[] previous = null;
            for (int index = 0; index < length; index++) {
                Object key = readValue(depth + 1);
                byte[] encodedKey = encodeIsolated(key, depth + 1);
                if (previous != null && BinaryCodecSupport.compareBytes(previous, encodedKey) >= 0) {
                    throw new IllegalArgumentException("Block metadata map keys are not canonical");
                }
                if (values.containsKey(key)) {
                    throw new IllegalArgumentException("Block metadata map contains duplicate keys");
                }
                values.put(key, readValue(depth + 1));
                previous = encodedKey;
            }
            return values;
        }

        private Map<String, Object> readStringMapBody(int depth) {
            int length = readLength("map");
            Map<String, Object> values = new LinkedHashMap<>(Math.min(length, 1024));
            String previous = null;
            for (int index = 0; index < length; index++) {
                String key = readString();
                if (previous != null && previous.compareTo(key) >= 0) {
                    throw new IllegalArgumentException("Block metadata map keys are not canonical");
                }
                values.put(key, readValue(depth + 1));
                previous = key;
            }
            return values;
        }

        private Set<Object> readSet(int depth) {
            int length = readLength("set");
            Set<Object> values = new LinkedHashSet<>(Math.min(length, 1024));
            byte[] previous = null;
            for (int index = 0; index < length; index++) {
                Object value = readValue(depth + 1);
                byte[] encoded = encodeIsolated(value, depth + 1);
                if (previous != null && BinaryCodecSupport.compareBytes(previous, encoded) >= 0) {
                    throw new IllegalArgumentException("Block metadata set values are not canonical");
                }
                if (!values.add(value)) {
                    throw new IllegalArgumentException("Block metadata set contains duplicate values");
                }
                previous = encoded;
            }
            return values;
        }

        private Object readItem(int itemType, int depth) {
            BinaryCodecSupport.requireDepth(DESCRIPTION, depth);
            Map<Object, Object> base = new LinkedHashMap<>();
            base.put("id", readString());
            base.put("count", checkedInteger(readZigZag()));
            int flags = readUnsignedByte();
            if ((flags & ~ITEM_FLAGS) != 0) {
                throw new IllegalArgumentException("Unsupported block metadata item flags " + flags);
            }
            if ((flags & ITEM_DATA_VERSION) != 0) {
                base.put("DataVersion", checkedInteger(readZigZag()));
            }
            if ((flags & ITEM_SCHEMA_VERSION) != 0) {
                base.put("schema_version", checkedInteger(readZigZag()));
            }
            if ((flags & ITEM_BASE_EXTENSIONS) != 0) {
                Map<?, ?> extensions = requireMap(readValue(depth + 1), "item base extensions");
                for (Map.Entry<?, ?> entry : extensions.entrySet()) {
                    if (base.containsKey(entry.getKey())) {
                        throw new IllegalArgumentException("Block metadata item extension replaces a required value");
                    }
                    base.put(entry.getKey(), entry.getValue());
                }
            }

            Object baseKey = itemType == LEGACY_ITEM ? 0 : "0";
            Object metadataKey = itemType == LEGACY_ITEM ? 1 : "1";
            Map<Object, Object> item = new LinkedHashMap<>();
            item.put(baseKey, base);
            if ((flags & ITEM_METADATA) != 0) {
                item.put(metadataKey, readValue(depth + 1));
            }
            if ((flags & ITEM_EXTENSIONS) != 0) {
                Map<?, ?> extensions = requireMap(readValue(depth + 1), "item extensions");
                for (Map.Entry<?, ?> entry : extensions.entrySet()) {
                    if (item.containsKey(entry.getKey())) {
                        throw new IllegalArgumentException("Block metadata item extension replaces a required value");
                    }
                    item.put(entry.getKey(), entry.getValue());
                }
            }
            return item;
        }

        private DyeColor readDyeColor() {
            int identifier = readUnsignedByte();
            if (identifier >= DYE_COLORS.length) {
                throw new IllegalArgumentException("Unknown block metadata dye color " + identifier);
            }
            try {
                return DyeColor.valueOf(DYE_COLORS[identifier]);
            }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unsupported block metadata dye color " + DYE_COLORS[identifier], exception);
            }
        }

        private FireworkEffect.Type readFireworkType() {
            int identifier = readUnsignedByte();
            if (identifier >= FIREWORK_TYPES.length) {
                throw new IllegalArgumentException("Unknown block metadata firework type " + identifier);
            }
            try {
                return FireworkEffect.Type.valueOf(FIREWORK_TYPES[identifier]);
            }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unsupported block metadata firework type " + FIREWORK_TYPES[identifier], exception);
            }
        }

        private String readString() {
            long code = readVarUnsigned();
            int stringType = (int) (code & 3);
            long value = code >>> 2;
            if (stringType == STRING_DICTIONARY_VALUE) {
                if (value >= STRING_DICTIONARY.length) {
                    throw new IllegalArgumentException("Unknown block metadata string identifier " + value);
                }
                return STRING_DICTIONARY[(int) value];
            }
            if (stringType == STRING_REFERENCE) {
                if (value >= localStrings.size()) {
                    throw new IllegalArgumentException("Unknown block metadata string reference " + value);
                }
                String result = localStrings.get((int) value);
                if (BinaryCodecSupport.unsignedVarSize(code) >= literalSize(result)) {
                    throw new IllegalArgumentException("Block metadata string reference is not canonical");
                }
                return result;
            }
            if (value > Integer.MAX_VALUE || value > remaining()) {
                throw new IllegalArgumentException("Block metadata string exceeds the remaining input");
            }
            String decoded = readUtf8((int) value);
            String result = stringType == STRING_MINECRAFT ? MINECRAFT_PREFIX + decoded : decoded;
            boolean minecraft = result.startsWith(MINECRAFT_PREFIX) && result.length() > MINECRAFT_PREFIX.length();
            if ((stringType == STRING_MINECRAFT) != minecraft) {
                throw new IllegalArgumentException("Block metadata string prefix is not canonical");
            }
            if (STRING_IDENTIFIERS.containsKey(result)) {
                throw new IllegalArgumentException("Block metadata dictionary string is not canonical");
            }
            int encodedSize = BinaryCodecSupport.unsignedVarSize(code) + (int) value;
            Integer identifier = localStringIdentifiers.get(result);
            if (identifier != null) {
                long reference = ((long) identifier << 2) | STRING_REFERENCE;
                if (BinaryCodecSupport.unsignedVarSize(reference) < encodedSize) {
                    throw new IllegalArgumentException("Block metadata repeated string is not canonical");
                }
            }
            else if (!result.isEmpty()) {
                if (localStrings.size() >= BinaryCodecSupport.MAX_CONTAINER_LENGTH) {
                    throw new IllegalArgumentException("Block metadata string table exceeds the maximum size");
                }
                localStringIdentifiers.put(result, localStrings.size());
                localStrings.add(result);
            }
            return result;
        }

        private int literalSize(String value) {
            byte[] bytes;
            int stringType;
            if (value.startsWith(MINECRAFT_PREFIX) && value.length() > MINECRAFT_PREFIX.length()) {
                bytes = value.substring(MINECRAFT_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
                stringType = STRING_MINECRAFT;
            }
            else {
                bytes = value.getBytes(StandardCharsets.UTF_8);
                stringType = STRING_LITERAL;
            }
            return BinaryCodecSupport.unsignedVarSize(((long) bytes.length << 2) | stringType) + bytes.length;
        }
    }

    private static Map<?, ?> requireMap(Object value, String description) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Block metadata " + description + " is not a map");
        }
        return (Map<?, ?>) value;
    }
}
