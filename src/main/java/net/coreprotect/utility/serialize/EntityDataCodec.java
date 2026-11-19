package net.coreprotect.utility.serialize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.potion.PotionEffectType;


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

    private static final String DESCRIPTION = "Entity data";
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
    private static final Map<String, Integer> STRING_IDENTIFIERS = BinaryCodecSupport.stringIdentifiers(DESCRIPTION, STRING_DICTIONARY);

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
        return decode(expectedKind, encoded, true);
    }

    private static List<Object> decode(Kind expectedKind, byte[] encoded, boolean resolveConfigurations) {
        Objects.requireNonNull(expectedKind, "expectedKind");
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length > BinaryCodecSupport.MAX_ENCODED_LENGTH) {
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
            return resolveConfigurations ? BinaryCodecSupport.restoreValues(data) : data;
        }
        catch (StackOverflowError error) {
            throw new IllegalArgumentException("Entity data exceeds the maximum nesting depth", error);
        }
    }

    public static byte[] canonicalize(Kind expectedKind, byte[] encoded) {
        return encode(expectedKind, decode(expectedKind, encoded, false));
    }

    public static byte[] fromLegacy(Kind kind, byte[] serialized) throws IOException, ClassNotFoundException {
        return encode(kind, LegacyMetadataCodec.decode(serialized));
    }

    public static byte[] toLegacy(Kind kind, byte[] encoded) throws IOException, ClassNotFoundException {
        return LegacyMetadataCodec.encode(decode(kind, encoded, false));
    }

    public static boolean isEncoded(byte[] data) {
        return data != null && data.length >= 4
                && Byte.toUnsignedInt(data[0]) == MAGIC_FIRST
                && Byte.toUnsignedInt(data[1]) == MAGIC_SECOND
                && Byte.toUnsignedInt(data[2]) == VERSION
                && Kind.fromCode(Byte.toUnsignedInt(data[3])) != null;
    }

    private static void encodeValue(BinaryOutput output, Object value, boolean configurationValue, int depth) {
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
        else if (value instanceof NamespacedKey) {
            output.write(STRING);
            output.writeString(value.toString());
        }
        else if (value instanceof LegacyMetadataCodec.ConfigurationValue) {
            encodeConfigurationValue(output, (LegacyMetadataCodec.ConfigurationValue) value, depth);
        }
        else if (value instanceof ConfigurationSerializable) {
            encodeConfigurationValue(output, BinaryCodecSupport.configurationValue((ConfigurationSerializable) value, DESCRIPTION), depth);
        }
        else if (value instanceof Keyed || value instanceof Sound || value instanceof PotionEffectType) {
            output.write(STRING);
            output.writeString(BinaryCodecSupport.registryKey(value, DESCRIPTION));
        }
        else if (value instanceof LegacyMetadataCodec.RegistryValue) {
            output.write(STRING);
            output.writeString(((LegacyMetadataCodec.RegistryValue) value).key());
        }
        else if (value instanceof BinaryCodecSupport.EnumValue) {
            encodeEnum(output, (BinaryCodecSupport.EnumValue) value);
        }
        else if (value instanceof Enum<?>) {
            Enum<?> enumValue = (Enum<?>) value;
            if (configurationValue) {
                encodeEnum(output, BinaryCodecSupport.enumValue(enumValue, DESCRIPTION));
            }
            else {
                output.write(STRING);
                output.writeString(enumValue.name());
            }
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

    private static void encodeConfigurationValue(BinaryOutput output, LegacyMetadataCodec.ConfigurationValue configuration, int depth) {
        output.write(CONFIGURATION);
        output.writeString(configuration.alias());
        encodeMapBody(output, configuration.values(), true, depth);
    }

    private static void encodeEnum(BinaryOutput output, BinaryCodecSupport.EnumValue value) {
        output.write(ENUM);
        output.writeString(value.typeName);
        output.writeString(value.constant);
    }

    private static void encodeSet(BinaryOutput output, Set<?> values, boolean configurationValue, int depth) {
        List<byte[]> encodedValues = new ArrayList<>(values.size());
        for (Object value : values) {
            BinaryOutput item = new BinaryOutput();
            encodeValue(item, value, configurationValue, depth + 1);
            encodedValues.add(item.toByteArray());
        }
        encodedValues.sort(BinaryCodecSupport::compareBytes);
        byte[] previous = null;
        output.write(SET);
        output.writeLength(encodedValues.size());
        for (byte[] encoded : encodedValues) {
            if (previous != null && BinaryCodecSupport.compareBytes(previous, encoded) == 0) {
                throw new IllegalArgumentException("Entity data set contains duplicate encoded values");
            }
            output.write(encoded, 0, encoded.length);
            previous = encoded;
        }
    }

    private static void encodeMapBody(BinaryOutput output, Map<?, ?> values, boolean configurationValue, int depth) {
        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = BinaryCodecSupport.mapKey(entry.getKey(), DESCRIPTION);
            BinaryCodecSupport.validateUnicode(DESCRIPTION, key);
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

    private static final class BinaryOutput extends BinaryCodecSupport.Output {

        private BinaryOutput() {
            super(DESCRIPTION);
        }

        private void writeString(String value) {
            BinaryCodecSupport.validateUnicode(DESCRIPTION, value);
            Integer identifier = STRING_IDENTIFIERS.get(value);
            if (identifier != null) {
                writeVarUnsigned(((long) identifier << 1) | 1);
                return;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeVarUnsigned(((long) bytes.length) << 1);
            write(bytes, 0, bytes.length);
        }
    }

    private static final class BinaryInput extends BinaryCodecSupport.Input {


        private BinaryInput(byte[] data) {
            super(data, DESCRIPTION);
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
                    return new BinaryCodecSupport.EnumValue(readString(), readString());
                case CONFIGURATION:
                    String alias = readString();
                    Map<String, Object> serialized = readMapBody(depth);
                    return new LegacyMetadataCodec.ConfigurationValue(alias, serialized);
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
                int start = position();
                Object value = readValue(depth + 1);
                byte[] encoded = bytesSince(start);
                if (previous != null && BinaryCodecSupport.compareBytes(previous, encoded) >= 0) {
                    throw new IllegalArgumentException("Entity data set values are not canonical");
                }
                values.add(value);
                previous = encoded;
            }
            return values;
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
            String value = readUtf8((int) length);
            if (STRING_IDENTIFIERS.containsKey(value)) {
                throw new IllegalArgumentException("Entity data dictionary string is not canonical");
            }
            return value;
        }
    }
}
