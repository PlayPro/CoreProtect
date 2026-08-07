package net.coreprotect.utility.serialize;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import net.coreprotect.bukkit.BukkitAdapter;

public final class EntityDataCodec {

    public enum Kind {
        ENTITY("entity", "CP2E:"),
        ENTITY_SPAWN("entity_spawn", "CP2S:");

        private final String id;
        private final String prefix;
        private final byte[] prefixBytes;

        Kind(String id, String prefix) {
            this.id = id;
            this.prefix = prefix;
            prefixBytes = prefix.getBytes(StandardCharsets.US_ASCII);
        }
    }

    private static final int MAX_TEXT_LENGTH = 32 * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final String BUKKIT_PACKAGE = "org.bukkit.";
    private static final String REMOVED_FOOD_EFFECT_ALIAS = "FoodEffect";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private EntityDataCodec() {
        throw new IllegalStateException("Codec class");
    }

    public static byte[] encode(Kind kind, List<Object> data) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(data, "data");

        return fromText(kind.prefix + GSON.toJson(encodeValue(data, false, 1)));
    }

    public static List<Object> decode(Kind expectedKind, byte[] encoded) {
        Objects.requireNonNull(expectedKind, "expectedKind");

        try {
            String text = toText(encoded);
            Kind kind = encodedKind(text);
            if (kind != expectedKind) {
                throw new IllegalArgumentException("Entity data kind " + kind.id + " cannot be read as " + expectedKind.id);
            }
            JsonElement parsed = new JsonParser().parse(text.substring(kind.prefix.length()));
            Object value = decodeValue(parsed, 1);
            if (!(value instanceof List<?>)) {
                throw new IllegalArgumentException("Entity data root is not a list");
            }
            @SuppressWarnings("unchecked")
            List<Object> data = (List<Object>) value;
            return data;
        }
        catch (JsonParseException | StackOverflowError exception) {
            throw new IllegalArgumentException("Invalid entity data", exception);
        }
    }

    public static byte[] canonicalize(Kind expectedKind, byte[] encoded) {
        return encode(expectedKind, decode(expectedKind, encoded));
    }

    public static boolean isEncoded(byte[] data) {
        return encodedKind(data) != null;
    }

    public static String toText(byte[] data) {
        if (!isEncoded(data)) {
            throw new IllegalArgumentException("Entity data does not use the CoreProtect text format");
        }
        if (data.length > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Entity data exceeds the maximum encoded size");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
                    .toString();
        }
        catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Entity data is not valid UTF-8", exception);
        }
    }

    public static byte[] fromText(String data) {
        if (data == null) {
            return null;
        }
        if (encodedKind(data) == null) {
            throw new IllegalArgumentException("Entity data does not use the CoreProtect text format");
        }
        if (data.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Entity data exceeds the maximum encoded size");
        }
        validateUnicode(data);

        byte[] encoded = data.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Entity data exceeds the maximum encoded size");
        }
        return encoded;
    }

    private static Kind encodedKind(byte[] data) {
        if (data == null) {
            return null;
        }
        for (Kind kind : Kind.values()) {
            if (startsWith(data, kind.prefixBytes)) {
                return kind;
            }
        }
        return null;
    }

    private static Kind encodedKind(String data) {
        for (Kind kind : Kind.values()) {
            if (data.startsWith(kind.prefix)) {
                return kind;
            }
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (data[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static JsonElement encodeValue(Object value, boolean configurationValue, int depth) {
        requireDepth(depth);
        if (value == null) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        }
        if (value instanceof String) {
            validateUnicode((String) value);
            return new JsonPrimitive((String) value);
        }
        if (value instanceof Integer || value instanceof Double && Double.isFinite((Double) value)) {
            return new JsonPrimitive((Number) value);
        }
        if (value instanceof Byte) {
            return stringTag("b", value.toString());
        }
        if (value instanceof Short) {
            return stringTag("h", value.toString());
        }
        if (value instanceof Long) {
            return stringTag("l", value.toString());
        }
        if (value instanceof Float) {
            return stringTag("f", value.toString());
        }
        if (value instanceof Double) {
            return stringTag("d", value.toString());
        }
        if (value instanceof UUID) {
            return stringTag("u", value.toString());
        }
        if (value instanceof byte[]) {
            return stringTag("a", Base64.getEncoder().withoutPadding().encodeToString((byte[]) value));
        }
        if (value instanceof int[]) {
            JsonArray values = new JsonArray();
            for (int item : (int[]) value) {
                values.add(item);
            }
            return tag("i", values);
        }
        if (value instanceof long[]) {
            JsonArray values = new JsonArray();
            for (long item : (long[]) value) {
                values.add(Long.toString(item));
            }
            return tag("j", values);
        }
        if (value instanceof NamespacedKey) {
            return new JsonPrimitive(value.toString());
        }
        if (value instanceof ConfigurationSerializable) {
            return encodeConfigurationValue((ConfigurationSerializable) value, depth);
        }
        if (value instanceof Keyed || value instanceof Sound || value instanceof PotionEffectType) {
            return new JsonPrimitive(registryKey(value));
        }
        if (value instanceof Enum<?>) {
            return configurationValue ? encodeEnum((Enum<?>) value) : new JsonPrimitive(((Enum<?>) value).name());
        }
        if (value instanceof Set<?>) {
            List<JsonElement> values = new ArrayList<>();
            for (Object item : (Set<?>) value) {
                values.add(encodeValue(item, configurationValue, depth + 1));
            }
            values.sort(Comparator.comparing(GSON::toJson));
            JsonArray array = new JsonArray();
            String previous = null;
            for (JsonElement item : values) {
                String encoded = GSON.toJson(item);
                if (encoded.equals(previous)) {
                    throw new IllegalArgumentException("Entity data set contains duplicate encoded values");
                }
                array.add(item);
                previous = encoded;
            }
            return tag("s", array);
        }
        if (value instanceof Collection<?>) {
            JsonArray values = new JsonArray();
            for (Object item : (Collection<?>) value) {
                values.add(encodeValue(item, configurationValue, depth + 1));
            }
            return values;
        }
        if (value instanceof Map<?, ?>) {
            return encodeMap((Map<?, ?>) value, configurationValue, depth);
        }
        throw new IllegalArgumentException("Unsupported entity data value " + value.getClass().getName());
    }

    private static JsonElement encodeConfigurationValue(ConfigurationSerializable value, int depth) {
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

        JsonObject result = tag("c", encodeMap(serialized, true, depth + 1));
        result.addProperty("c", alias);
        return result;
    }

    private static JsonElement encodeEnum(Enum<?> value) {
        Class<?> type = value.getDeclaringClass();
        if (!isBukkitApiType(type)) {
            throw new IllegalArgumentException("Disallowed entity data enum " + type.getName());
        }

        JsonObject result = stringTag("e", value.name());
        result.addProperty("c", type.getName().substring(BUKKIT_PACKAGE.length()));
        return result;
    }

    private static JsonElement encodeMap(Map<?, ?> values, boolean configurationValue, int depth) {
        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = mapKey(entry.getKey());
            validateUnicode(key);
            if (sorted.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate entity data map key " + key);
            }
            sorted.put(key, entry.getValue());
        }

        if (sorted.containsKey("$")) {
            JsonArray entries = new JsonArray();
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                JsonArray pair = new JsonArray();
                pair.add(entry.getKey());
                pair.add(encodeValue(entry.getValue(), configurationValue, depth + 1));
                entries.add(pair);
            }
            return tag("m", entries);
        }

        JsonObject result = new JsonObject();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            result.add(entry.getKey(), encodeValue(entry.getValue(), configurationValue, depth + 1));
        }
        return result;
    }

    private static JsonObject stringTag(String type, String value) {
        validateUnicode(value);
        return tag(type, new JsonPrimitive(value));
    }

    private static JsonObject tag(String type, JsonElement value) {
        JsonObject result = new JsonObject();
        result.addProperty("$", type);
        result.add("v", value);
        return result;
    }

    private static Object decodeValue(JsonElement value, int depth) {
        if (depth > MAX_DEPTH * 4) {
            throw new IllegalArgumentException("Entity data exceeds the maximum nesting depth");
        }
        if (value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isString()) {
                String result = primitive.getAsString();
                validateUnicode(result);
                return result;
            }
            return parseNumber(primitive.getAsString());
        }
        if (value.isJsonArray()) {
            List<Object> result = new ArrayList<>();
            for (JsonElement item : value.getAsJsonArray()) {
                result.add(decodeValue(item, depth + 1));
            }
            return result;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            validateUnicode(entry.getKey());
            result.put(entry.getKey(), decodeValue(entry.getValue(), depth + 1));
        }
        return result.containsKey("$") ? decodeTag(result) : result;
    }

    private static Object decodeTag(Map<String, Object> values) {
        String type = requireString(values, "$");
        switch (type) {
            case "b":
            case "h":
            case "l":
            case "f":
                requireFields(values, "$", "v");
                return parseTaggedNumber(type, requireString(values, "v"));
            case "d":
                requireFields(values, "$", "v");
                return parseSpecialDouble(requireString(values, "v"));
            case "u":
                requireFields(values, "$", "v");
                return parseUuid(requireString(values, "v"));
            case "a":
                requireFields(values, "$", "v");
                return parseByteArray(requireString(values, "v"));
            case "i":
                requireFields(values, "$", "v");
                return parseIntArray(requireList(values, "v"));
            case "j":
                requireFields(values, "$", "v");
                return parseLongArray(requireList(values, "v"));
            case "s":
                requireFields(values, "$", "v");
                return parseSet(requireList(values, "v"));
            case "m":
                requireFields(values, "$", "v");
                return parseEscapedMap(requireList(values, "v"));
            case "e":
                requireFields(values, "$", "c", "v");
                return parseEnum(requireString(values, "c"), requireString(values, "v"));
            case "c":
                requireFields(values, "$", "c", "v");
                return parseConfigurationValue(requireString(values, "c"), requireMap(values, "v"));
            default:
                throw new IllegalArgumentException("Unsupported entity data marker " + type);
        }
    }

    private static Number parseNumber(String value) {
        try {
            if (value.indexOf('.') >= 0 || value.indexOf('e') >= 0 || value.indexOf('E') >= 0) {
                double result = Double.parseDouble(value);
                if (!Double.isFinite(result)) {
                    throw new IllegalArgumentException("Invalid entity data number " + value);
                }
                return result;
            }
            return Integer.valueOf(value);
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid entity data number " + value, exception);
        }
    }

    private static Number parseTaggedNumber(String type, String value) {
        try {
            Number result;
            switch (type) {
                case "b":
                    result = Byte.valueOf(value);
                    break;
                case "h":
                    result = Short.valueOf(value);
                    break;
                case "l":
                    result = Long.valueOf(value);
                    break;
                default:
                    result = Float.valueOf(value);
                    break;
            }
            if (!result.toString().equals(value)) {
                throw new IllegalArgumentException("Noncanonical entity data number " + value);
            }
            return result;
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid entity data number " + value, exception);
        }
    }

    private static Double parseSpecialDouble(String value) {
        switch (value) {
            case "NaN":
                return Double.NaN;
            case "Infinity":
                return Double.POSITIVE_INFINITY;
            case "-Infinity":
                return Double.NEGATIVE_INFINITY;
            default:
                throw new IllegalArgumentException("Invalid special double entity data value " + value);
        }
    }

    private static UUID parseUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equals(value)) {
                throw new IllegalArgumentException("Noncanonical UUID entity data value " + value);
            }
            return uuid;
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid UUID entity data value " + value, exception);
        }
    }

    private static byte[] parseByteArray(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (!Base64.getEncoder().withoutPadding().encodeToString(bytes).equals(value)) {
                throw new IllegalArgumentException("Noncanonical byte array entity data value");
            }
            return bytes;
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid byte array entity data value", exception);
        }
    }

    private static int[] parseIntArray(List<Object> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof Integer)) {
                throw new IllegalArgumentException("Invalid integer array entity data value");
            }
            result[index] = (Integer) value;
        }
        return result;
    }

    private static long[] parseLongArray(List<Object> values) {
        long[] result = new long[values.size()];
        for (int index = 0; index < values.size(); index++) {
            try {
                String value = requireString(values.get(index), "long array value");
                result[index] = Long.parseLong(value);
                if (!Long.toString(result[index]).equals(value)) {
                    throw new IllegalArgumentException("Noncanonical long array entity data value");
                }
            }
            catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid long array entity data value", exception);
            }
        }
        return result;
    }

    private static Set<Object> parseSet(List<Object> values) {
        Set<Object> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (!result.add(value)) {
                throw new IllegalArgumentException("Duplicate entity data set value");
            }
        }
        return result;
    }

    private static Map<String, Object> parseEscapedMap(List<Object> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object value : values) {
            if (!(value instanceof List<?>) || ((List<?>) value).size() != 2) {
                throw new IllegalArgumentException("Invalid escaped entity data map entry");
            }
            List<?> entry = (List<?>) value;
            String key = requireString(entry.get(0), "map key");
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate entity data map key " + key);
            }
            result.put(key, entry.get(1));
        }
        return result;
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
        return name.startsWith("org.bukkit.")
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

    private static String requireString(Map<String, Object> values, String field) {
        return requireString(values.get(field), field);
    }

    private static String requireString(Object value, String field) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Entity data field " + field + " is not a string");
        }
        return (String) value;
    }

    private static List<Object> requireList(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException("Entity data field " + field + " is not a list");
        }
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) value;
        return result;
    }

    private static Map<String, Object> requireMap(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Entity data field " + field + " is not a map");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    private static void requireFields(Map<String, Object> values, String... fields) {
        if (values.size() != fields.length) {
            throw new IllegalArgumentException("Invalid entity data marker fields");
        }
        for (String field : fields) {
            if (!values.containsKey(field)) {
                throw new IllegalArgumentException("Missing entity data marker field " + field);
            }
        }
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
}
