package net.coreprotect.utility.serialize;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.potion.PotionEffectType;

import net.coreprotect.bukkit.BukkitAdapter;

public final class EntityDataCodec {

    public enum Kind {
        ENTITY("entity", 1),
        ENTITY_SPAWN("entity_spawn", 2);

        private final String id;
        private final int code;

        Kind(String id, int code) {
            this.id = id;
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

    private static final int MAX_ENCODED_LENGTH = 32 * 1024 * 1024;
    private static final int MAX_CONTAINER_LENGTH = 4 * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAGIC_FIRST = 'C';
    private static final int MAGIC_SECOND = 'P';
    private static final int VERSION = 1;
    private static final int NULL = 0;
    private static final int FALSE = 1;
    private static final int TRUE = 2;
    private static final int INTEGER = 3;
    private static final int DOUBLE = 4;
    private static final int STRING = 5;
    private static final int LIST = 6;
    private static final int MAP = 7;
    private static final int BYTE = 8;
    private static final int SHORT = 9;
    private static final int LONG = 10;
    private static final int FLOAT = 11;
    private static final int SPECIAL_DOUBLE = 12;
    private static final int UUID_VALUE = 13;
    private static final int BYTE_ARRAY = 14;
    private static final int INT_ARRAY = 15;
    private static final int LONG_ARRAY = 16;
    private static final int SET = 17;
    private static final int ENUM = 18;
    private static final int CONFIGURATION = 19;
    private static final int DOUBLE_ZERO = 20;
    private static final int DOUBLE_ONE = 21;
    private static final int DOUBLE_INTEGER = 22;
    private static final int DOUBLE_FLOAT = 23;
    private static final String BUKKIT_PACKAGE = "org.bukkit.";
    private static final String REMOVED_FOOD_EFFECT_ALIAS = "FoodEffect";
    private static final String[] STRING_DICTIONARY = {
            "minecraft:random_spawn_bonus",
            "minecraft:explosion_knockback_resistance",
            "minecraft:water_movement_efficiency",
            "minecraft:waypoint_transmit_range",
            "minecraft:fall_damage_multiplier",
            "minecraft:knockback_resistance",
            "minecraft:movement_efficiency",
            "minecraft:safe_fall_distance",
            "operation",
            "minecraft:attack_knockback",
            "minecraft:armor_toughness",
            "minecraft:camera_distance",
            "minecraft:max_absorption",
            "minecraft:movement_speed",
            "minecraft:jump_strength",
            "minecraft:burning_time",
            "minecraft:follow_range",
            "minecraft:oxygen_bonus",
            "minecraft:attack_damage",
            "minecraft:step_height",
            "minecraft:max_health",
            "minecraft:spawn_reinforcements",
            "amount",
            "minecraft:base_attack_damage",
            "minecraft:gravity",
            "minecraft:armor",
            "minecraft:scale",
            "minecraft:attacking",
            "key",
            "minecraft:zombie_random_spawn_bonus",
            "minecraft:entity_interaction_range",
            "minecraft:leader_zombie_bonus"
    };
    private static final Map<String, Integer> STRING_IDENTIFIERS = stringIdentifiers();

    private EntityDataCodec() {
        throw new IllegalStateException("Codec class");
    }

    public static byte[] encode(Kind kind, List<Object> data) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(data, "data");

        BinaryOutput output = new BinaryOutput();
        output.write(MAGIC_FIRST);
        output.write(MAGIC_SECOND);
        output.write(VERSION);
        output.write(kind.code);
        encodeValue(output, data, false, 1);
        return output.toByteArray();
    }

    public static List<Object> decode(Kind expectedKind, byte[] encoded) {
        Objects.requireNonNull(expectedKind, "expectedKind");
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("Entity data exceeds the maximum encoded size");
        }

        try {
            BinaryInput input = new BinaryInput(encoded);
            input.requireHeader(expectedKind);
            Object value = input.readValue(1);
            input.requireEnd();
            if (!(value instanceof List<?>)) {
                throw new IllegalArgumentException("Entity data root is not a list");
            }
            @SuppressWarnings("unchecked")
            List<Object> data = (List<Object>) value;
            return data;
        }
        catch (StackOverflowError error) {
            throw new IllegalArgumentException("Entity data exceeds the maximum nesting depth", error);
        }
    }

    public static byte[] canonicalize(Kind expectedKind, byte[] encoded) {
        return encode(expectedKind, decode(expectedKind, encoded));
    }

    public static boolean isEncoded(byte[] data) {
        return data != null && data.length >= 4
                && Byte.toUnsignedInt(data[0]) == MAGIC_FIRST
                && Byte.toUnsignedInt(data[1]) == MAGIC_SECOND
                && Byte.toUnsignedInt(data[2]) == VERSION
                && Kind.fromCode(Byte.toUnsignedInt(data[3])) != null;
    }

    private static void encodeValue(BinaryOutput output, Object value, boolean configurationValue, int depth) {
        requireDepth(depth);
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
            encodeEnum(output, (Enum<?>) value, configurationValue);
        }
        else if (value instanceof Set<?>) {
            encodeSet(output, (Set<?>) value, configurationValue, depth);
        }
        else if (value instanceof Collection<?>) {
            Collection<?> values = (Collection<?>) value;
            output.write(LIST);
            output.writeLength(values.size());
            for (Object item : values) {
                encodeValue(output, item, configurationValue, depth + 1);
            }
        }
        else if (value instanceof Map<?, ?>) {
            output.write(MAP);
            encodeMapBody(output, (Map<?, ?>) value, configurationValue, depth);
        }
        else {
            throw new IllegalArgumentException("Unsupported entity data value " + value.getClass().getName());
        }
    }

    private static void encodeDouble(BinaryOutput output, double value) {
        if (!Double.isFinite(value)) {
            output.write(SPECIAL_DOUBLE);
            output.write(Double.isNaN(value) ? 0 : value > 0 ? 1 : 2);
            return;
        }

        long bits = Double.doubleToLongBits(value);
        if (bits == Double.doubleToLongBits(0.0)) {
            output.write(DOUBLE_ZERO);
        }
        else if (bits == Double.doubleToLongBits(1.0)) {
            output.write(DOUBLE_ONE);
        }
        else if (value != 0.0 && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE && (double) (long) value == value) {
            output.write(DOUBLE_INTEGER);
            output.writeZigZag((long) value);
        }
        else if ((double) (float) value == value) {
            output.write(DOUBLE_FLOAT);
            output.writeInt(Float.floatToIntBits((float) value));
        }
        else {
            output.write(DOUBLE);
            output.writeLong(bits);
        }
    }

    private static void encodeConfigurationValue(BinaryOutput output, ConfigurationSerializable value, int depth) {
        Class<? extends ConfigurationSerializable> type = value.getClass();
        String alias = ConfigurationSerialization.getAlias(type);
        if (alias == null || alias.isEmpty()) {
            throw new IllegalArgumentException("Configuration-serializable entity data type has no alias " + type.getName());
        }
        requirePlatformType(type);
        Map<String, Object> serialized = value.serialize();
        if (serialized == null) {
            throw new IllegalArgumentException("Configuration-serializable entity data value is null " + alias);
        }

        output.write(CONFIGURATION);
        output.writeString(alias);
        encodeMapBody(output, serialized, true, depth);
    }

    private static void encodeEnum(BinaryOutput output, Enum<?> value, boolean configurationValue) {
        if (!configurationValue) {
            output.write(STRING);
            output.writeString(value.name());
            return;
        }

        Class<?> type = value.getDeclaringClass();
        if (!isBukkitApiType(type)) {
            throw new IllegalArgumentException("Disallowed entity data enum " + type.getName());
        }
        output.write(ENUM);
        output.writeString(type.getName().substring(BUKKIT_PACKAGE.length()));
        output.writeString(value.name());
    }

    private static void encodeSet(BinaryOutput output, Set<?> values, boolean configurationValue, int depth) {
        List<byte[]> encodedValues = new ArrayList<>(values.size());
        for (Object value : values) {
            BinaryOutput item = new BinaryOutput();
            encodeValue(item, value, configurationValue, depth + 1);
            encodedValues.add(item.toByteArray());
        }
        encodedValues.sort(EntityDataCodec::compareBytes);
        byte[] previous = null;
        output.write(SET);
        output.writeLength(encodedValues.size());
        for (byte[] encoded : encodedValues) {
            if (previous != null && compareBytes(previous, encoded) == 0) {
                throw new IllegalArgumentException("Entity data set contains duplicate encoded values");
            }
            output.write(encoded, 0, encoded.length);
            previous = encoded;
        }
    }

    private static void encodeMapBody(BinaryOutput output, Map<?, ?> values, boolean configurationValue, int depth) {
        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = mapKey(entry.getKey());
            validateUnicode(key);
            if (sorted.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate entity data map key " + key);
            }
            sorted.put(key, entry.getValue());
        }

        output.writeLength(sorted.size());
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            output.writeString(entry.getKey());
            encodeValue(output, entry.getValue(), configurationValue, depth + 1);
        }
    }

    private static int compareBytes(byte[] first, byte[] second) {
        int length = Math.min(first.length, second.length);
        for (int index = 0; index < length; index++) {
            int difference = Byte.toUnsignedInt(first[index]) - Byte.toUnsignedInt(second[index]);
            if (difference != 0) {
                return difference;
            }
        }
        return Integer.compare(first.length, second.length);
    }

    private static Object parseConfigurationValue(String alias, Map<String, Object> serialized) {
        Class<? extends ConfigurationSerializable> type = ConfigurationSerialization.getClassByAlias(alias);
        if (type == null) {
            if (REMOVED_FOOD_EFFECT_ALIAS.equals(alias)) {
                return serialized;
            }
            throw new IllegalArgumentException("Unknown configuration-serializable entity data type " + alias);
        }
        requirePlatformType(type);
        try {
            Object result = ConfigurationSerialization.deserializeObject(serialized, type);
            if (result == null) {
                throw new IllegalArgumentException("Unable to deserialize configuration-serializable entity data type " + alias);
            }
            return result;
        }
        catch (RuntimeException | LinkageError exception) {
            throw new IllegalArgumentException("Unable to deserialize configuration-serializable entity data type " + alias, exception);
        }
    }

    private static Object parseEnum(String className, String value) {
        String qualifiedClassName = BUKKIT_PACKAGE + className;
        try {
            Class<?> type = Class.forName(qualifiedClassName, false, EntityDataCodec.class.getClassLoader());
            if (!isBukkitApiType(type) || !type.isEnum()) {
                throw new IllegalArgumentException("Disallowed entity data enum " + qualifiedClassName);
            }
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Object result = Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), value);
            return result;
        }
        catch (ClassNotFoundException | LinkageError | SecurityException exception) {
            throw new IllegalArgumentException("Unknown entity data enum " + qualifiedClassName, exception);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + qualifiedClassName + " entity data enum value " + value, exception);
        }
    }

    private static String mapKey(Object value) {
        if (value instanceof String || value instanceof NamespacedKey) {
            return value.toString();
        }
        if (value instanceof Keyed || value instanceof Sound || value instanceof PotionEffectType) {
            return registryKey(value);
        }
        throw new IllegalArgumentException("Unsupported entity data map key " + (value == null ? "null" : value.getClass().getName()));
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
        throw new IllegalArgumentException("Unable to determine registry key for entity data type " + value.getClass().getName());
    }

    private static boolean isBukkitApiType(Class<?> type) {
        String name = type.getName();
        return name.startsWith(BUKKIT_PACKAGE)
                && !name.startsWith("org.bukkit.craftbukkit.")
                && type.getClassLoader() == ConfigurationSerializable.class.getClassLoader();
    }

    private static void requirePlatformType(Class<?> type) {
        if (type.getClassLoader() != ConfigurationSerializable.class.getClassLoader()) {
            throw new IllegalArgumentException("Disallowed configuration-serializable entity data type " + type.getName());
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
                throw new IllegalStateException("Duplicate entity data dictionary value " + STRING_DICTIONARY[index]);
            }
        }
        return Collections.unmodifiableMap(identifiers);
    }

    private static void validateUnicode(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    throw new IllegalArgumentException("Entity data contains an unpaired Unicode surrogate");
                }
            }
            else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException("Entity data contains an unpaired Unicode surrogate");
            }
        }
    }

    private static void requireDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Entity data exceeds the maximum nesting depth");
        }
    }

    private static final class BinaryOutput {

        private byte[] data = new byte[128];
        private int size;

        private void write(int value) {
            requireCapacity(1);
            data[size++] = (byte) value;
        }

        private void write(byte[] value, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, value.length);
            requireCapacity(length);
            System.arraycopy(value, offset, data, size, length);
            size += length;
        }

        private byte[] toByteArray() {
            return Arrays.copyOf(data, size);
        }

        private void writeInt(int value) {
            write(value >>> 24);
            write(value >>> 16);
            write(value >>> 8);
            write(value);
        }

        private void writeLong(long value) {
            write((int) (value >>> 56));
            write((int) (value >>> 48));
            write((int) (value >>> 40));
            write((int) (value >>> 32));
            write((int) (value >>> 24));
            write((int) (value >>> 16));
            write((int) (value >>> 8));
            write((int) value);
        }

        private void writeLength(int value) {
            if (value < 0 || value > MAX_CONTAINER_LENGTH) {
                throw new IllegalArgumentException("Entity data collection exceeds the maximum size");
            }
            writeVarUnsigned(value);
        }

        private void writeByteLength(int value) {
            if (value < 0 || value > MAX_ENCODED_LENGTH) {
                throw new IllegalArgumentException("Entity data binary value exceeds the maximum size");
            }
            writeVarUnsigned(value);
        }

        private void writeString(String value) {
            validateUnicode(value);
            Integer identifier = STRING_IDENTIFIERS.get(value);
            if (identifier != null) {
                writeVarUnsigned(((long) identifier << 1) | 1);
                return;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeVarUnsigned(((long) bytes.length) << 1);
            write(bytes, 0, bytes.length);
        }

        private void writeZigZag(long value) {
            writeVarUnsigned((value << 1) ^ (value >> 63));
        }

        private void writeVarUnsigned(long value) {
            while ((value & ~0x7fL) != 0) {
                write(((int) value & 0x7f) | 0x80);
                value >>>= 7;
            }
            write((int) value);
        }

        private void requireCapacity(int additional) {
            if (additional < 0 || size > MAX_ENCODED_LENGTH - additional) {
                throw new IllegalArgumentException("Entity data exceeds the maximum encoded size");
            }
            int required = size + additional;
            if (required > data.length) {
                data = Arrays.copyOf(data, Math.max(required, Math.min(MAX_ENCODED_LENGTH, data.length << 1)));
            }
        }
    }

    private static final class BinaryInput {
        private final byte[] data;
        private int position;

        private BinaryInput(byte[] data) {
            this.data = data;
        }

        private void requireHeader(Kind expectedKind) {
            if (readUnsignedByte() != MAGIC_FIRST || readUnsignedByte() != MAGIC_SECOND) {
                throw new IllegalArgumentException("Entity data does not use the CoreProtect binary format");
            }
            int version = readUnsignedByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported entity data format version " + version);
            }
            int kindCode = readUnsignedByte();
            Kind kind = Kind.fromCode(kindCode);
            if (kind == null) {
                throw new IllegalArgumentException("Unsupported entity data kind " + kindCode);
            }
            if (kind != expectedKind) {
                throw new IllegalArgumentException("Entity data kind " + kind.id + " cannot be read as " + expectedKind.id);
            }
        }

        private Object readValue(int depth) {
            requireDepth(depth);
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
                case DOUBLE:
                    return readRawDouble();
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
                case ENUM:
                    return parseEnum(readString(), readString());
                case CONFIGURATION:
                    return parseConfigurationValue(readString(), readMapBody(depth));
                case DOUBLE_ZERO:
                    return 0.0D;
                case DOUBLE_ONE:
                    return 1.0D;
                case DOUBLE_INTEGER:
                    return readIntegralDouble();
                case DOUBLE_FLOAT:
                    return readFloatDouble();
                default:
                    throw new IllegalArgumentException("Unsupported entity data type " + type);
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

        private Map<String, Object> readMap(int depth) {
            return readMapBody(depth);
        }

        private Map<String, Object> readMapBody(int depth) {
            int length = readLength("map");
            Map<String, Object> values = new LinkedHashMap<>(Math.min(length, 1024));
            String previous = null;
            for (int index = 0; index < length; index++) {
                String key = readString();
                if (previous != null && previous.compareTo(key) >= 0) {
                    throw new IllegalArgumentException("Entity data map keys are not canonical");
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
                int start = position;
                Object value = readValue(depth + 1);
                byte[] encoded = new byte[position - start];
                System.arraycopy(data, start, encoded, 0, encoded.length);
                if (previous != null && compareBytes(previous, encoded) >= 0) {
                    throw new IllegalArgumentException("Entity data set values are not canonical");
                }
                values.add(value);
                previous = encoded;
            }
            return values;
        }

        private int[] readIntArray() {
            int length = readLength("integer array");
            int[] values = new int[length];
            for (int index = 0; index < length; index++) {
                values[index] = checkedInteger(readZigZag());
            }
            return values;
        }

        private long[] readLongArray() {
            int length = readLength("long array");
            long[] values = new long[length];
            for (int index = 0; index < length; index++) {
                values[index] = readZigZag();
            }
            return values;
        }

        private float readFloat() {
            int bits = readInt();
            float value = Float.intBitsToFloat(bits);
            if (Float.floatToIntBits(value) != bits) {
                throw new IllegalArgumentException("Entity data contains a noncanonical float");
            }
            return value;
        }

        private double readRawDouble() {
            long bits = readLong();
            double value = Double.longBitsToDouble(bits);
            if (!Double.isFinite(value) || compactDoubleType(value) != DOUBLE) {
                throw new IllegalArgumentException("Entity data contains a noncanonical double");
            }
            return value;
        }

        private double readIntegralDouble() {
            long integer = readZigZag();
            double value = integer;
            if ((long) value != integer || compactDoubleType(value) != DOUBLE_INTEGER) {
                throw new IllegalArgumentException("Entity data contains a noncanonical integral double");
            }
            return value;
        }

        private double readFloatDouble() {
            float value = readFloat();
            double result = value;
            if (!Double.isFinite(result) || compactDoubleType(result) != DOUBLE_FLOAT) {
                throw new IllegalArgumentException("Entity data contains a noncanonical float-backed double");
            }
            return result;
        }

        private double readSpecialDouble() {
            switch (readUnsignedByte()) {
                case 0:
                    return Double.NaN;
                case 1:
                    return Double.POSITIVE_INFINITY;
                case 2:
                    return Double.NEGATIVE_INFINITY;
                default:
                    throw new IllegalArgumentException("Invalid special double entity data value");
            }
        }

        private String readString() {
            long code = readVarUnsigned();
            if (code < 0) {
                throw new IllegalArgumentException("Entity data string identifier is out of range");
            }
            if ((code & 1) != 0) {
                long identifier = code >>> 1;
                if (identifier >= STRING_DICTIONARY.length) {
                    throw new IllegalArgumentException("Unknown entity data string identifier " + identifier);
                }
                return STRING_DICTIONARY[(int) identifier];
            }

            long length = code >>> 1;
            if (length > Integer.MAX_VALUE || length > remaining()) {
                throw new IllegalArgumentException("Entity data string exceeds the remaining input");
            }
            try {
                String value = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(data, position, (int) length))
                        .toString();
                position += (int) length;
                if (STRING_IDENTIFIERS.containsKey(value)) {
                    throw new IllegalArgumentException("Entity data dictionary string is not canonical");
                }
                return value;
            }
            catch (CharacterCodingException exception) {
                throw new IllegalArgumentException("Entity data string is not valid UTF-8", exception);
            }
        }

        private int readLength(String description) {
            long value = readVarUnsigned();
            if (value < 0 || value > MAX_CONTAINER_LENGTH) {
                throw new IllegalArgumentException("Entity data " + description + " exceeds the maximum size");
            }
            return (int) value;
        }

        private int readByteLength() {
            long value = readVarUnsigned();
            if (value < 0 || value > MAX_ENCODED_LENGTH) {
                throw new IllegalArgumentException("Entity data binary value exceeds the maximum size");
            }
            return (int) value;
        }

        private byte[] readBytes(int length) {
            if (length > remaining()) {
                throw new IllegalArgumentException("Entity data value exceeds the remaining input");
            }
            byte[] value = new byte[length];
            System.arraycopy(data, position, value, 0, length);
            position += length;
            return value;
        }

        private int readUnsignedByte() {
            if (position >= data.length) {
                throw new IllegalArgumentException("Entity data ended unexpectedly");
            }
            return Byte.toUnsignedInt(data[position++]);
        }

        private int readInt() {
            return readUnsignedByte() << 24
                    | readUnsignedByte() << 16
                    | readUnsignedByte() << 8
                    | readUnsignedByte();
        }

        private long readLong() {
            return (long) readUnsignedByte() << 56
                    | (long) readUnsignedByte() << 48
                    | (long) readUnsignedByte() << 40
                    | (long) readUnsignedByte() << 32
                    | (long) readUnsignedByte() << 24
                    | (long) readUnsignedByte() << 16
                    | (long) readUnsignedByte() << 8
                    | readUnsignedByte();
        }

        private long readZigZag() {
            long value = readVarUnsigned();
            return (value >>> 1) ^ -(value & 1);
        }

        private long readVarUnsigned() {
            long value = 0;
            for (int index = 0, shift = 0; index < 10; index++, shift += 7) {
                int current = readUnsignedByte();
                if (index == 9 && (current & 0xfe) != 0) {
                    throw new IllegalArgumentException("Entity data variable integer is out of range");
                }
                value |= (long) (current & 0x7f) << shift;
                if ((current & 0x80) == 0) {
                    if (unsignedVarSize(value) != index + 1) {
                        throw new IllegalArgumentException("Entity data variable integer is not canonical");
                    }
                    return value;
                }
            }
            throw new IllegalArgumentException("Entity data variable integer is too long");
        }

        private int remaining() {
            return data.length - position;
        }

        private void requireEnd() {
            if (position != data.length) {
                throw new IllegalArgumentException("Entity data contains trailing bytes");
            }
        }
    }

    private static int compactDoubleType(double value) {
        long bits = Double.doubleToLongBits(value);
        if (bits == Double.doubleToLongBits(0.0)) {
            return DOUBLE_ZERO;
        }
        if (bits == Double.doubleToLongBits(1.0)) {
            return DOUBLE_ONE;
        }
        if (value != 0.0 && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE && (double) (long) value == value) {
            return DOUBLE_INTEGER;
        }
        if ((double) (float) value == value) {
            return DOUBLE_FLOAT;
        }
        return DOUBLE;
    }

    private static short checkedShort(long value) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Entity data short is out of range");
        }
        return (short) value;
    }

    private static int checkedInteger(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Entity data integer is out of range");
        }
        return (int) value;
    }

    private static int unsignedVarSize(long value) {
        int size = 1;
        while ((value & ~0x7fL) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }
}
