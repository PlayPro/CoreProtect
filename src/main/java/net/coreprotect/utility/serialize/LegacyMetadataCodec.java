package net.coreprotect.utility.serialize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Keyed;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.util.io.BukkitObjectInputStream;

final class LegacyMetadataCodec {

    private static final String WRAPPER_CLASS = "org.bukkit.util.io.Wrapper";
    private static final long WRAPPER_UID = -986209235411767547L;
    private static final String ALIAS_KEY = "==";
    private static final long MAX_REFERENCES = 1_000_000;
    private static final Set<Class<?>> SIMPLE_TYPES = Set.of(Object.class, String.class, Number.class, Boolean.class, Byte.class,
            Short.class, Integer.class, Long.class, Float.class, Double.class, Character.class, Enum.class, UUID.class, Date.class);
    private static final Set<String> SIMPLE_TYPE_NAMES = SIMPLE_TYPES.stream().map(Class::getName).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> COLLECTION_TYPES = Set.of(
            "java.util.ArrayList", "java.util.LinkedList", "java.util.Arrays$ArrayList",
            "java.util.HashMap", "java.util.LinkedHashMap", "java.util.TreeMap", "java.util.EnumMap",
            "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet", "java.util.EnumSet",
            "java.util.EnumSet$SerializationProxy", "java.util.RegularEnumSet", "java.util.JumboEnumSet", "java.util.Map$Entry",
            "java.util.Collections$EmptyList", "java.util.Collections$EmptyMap", "java.util.Collections$EmptySet",
            "java.util.Collections$SingletonList", "java.util.Collections$SingletonMap", "java.util.Collections$SingletonSet",
            "java.util.Collections$UnmodifiableCollection", "java.util.Collections$UnmodifiableList",
            "java.util.Collections$UnmodifiableRandomAccessList", "java.util.Collections$UnmodifiableMap", "java.util.Collections$UnmodifiableSet",
            "java.util.Collections$UnmodifiableSortedMap", "java.util.Collections$UnmodifiableSortedSet",
            "java.util.Collections$UnmodifiableNavigableMap", "java.util.Collections$UnmodifiableNavigableSet",
            "java.util.Collections$ReverseComparator", "java.util.Comparators$NaturalOrderComparator",
            "java.util.CollSer", "java.util.ImmutableCollections$List12", "java.util.ImmutableCollections$ListN",
            "java.util.ImmutableCollections$Map1", "java.util.ImmutableCollections$MapN",
            "java.util.ImmutableCollections$Set12", "java.util.ImmutableCollections$SetN",
            "com.google.common.collect.ImmutableMap$SerializedForm", "com.google.common.collect.RegularImmutableMap",
            "com.google.common.collect.ImmutableBiMap$SerializedForm", "com.google.common.collect.RegularImmutableBiMap",
            "com.google.common.collect.SingletonImmutableBiMap", "com.google.common.collect.ImmutableList$SerializedForm",
            "com.google.common.collect.RegularImmutableList", "com.google.common.collect.SingletonImmutableList",
            "com.google.common.collect.ImmutableSet$SerializedForm", "com.google.common.collect.RegularImmutableSet",
            "com.google.common.collect.SingletonImmutableSet");

    private LegacyMetadataCodec() {
        throw new IllegalStateException("Codec class");
    }

    static List<Object> decode(byte[] encoded) throws IOException, ClassNotFoundException {
        return decode(encoded, true);
    }

    static List<Object> decodeRuntime(byte[] encoded) throws IOException, ClassNotFoundException {
        return decode(encoded, false);
    }

    private static List<Object> decode(byte[] encoded, boolean neutral) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(encoded, "encoded");
        requireLength(encoded.length);
        try (MetadataInput input = new MetadataInput(new ByteArrayInputStream(encoded), neutral)) {
            Object value = input.readObject();
            if (!(value instanceof List<?>)) {
                throw new IllegalArgumentException("Legacy metadata root is not a list");
            }
            @SuppressWarnings("unchecked")
            List<Object> metadata = (List<Object>) value;
            if (!neutral && input.registryValues) {
                @SuppressWarnings("unchecked")
                List<Object> normalized = (List<Object>) normalizeRuntimeValue(metadata, new IdentityHashMap<>(), 1);
                return normalized;
            }
            return metadata;
        }
    }

    private static Object normalizeRuntimeValue(Object value, Map<Object, Object> resolved, int depth) {
        if (value == null) {
            return null;
        }
        if (resolved.containsKey(value)) {
            return resolved.get(value);
        }
        BinaryCodecSupport.requireDepth("Legacy metadata", depth);
        if (value instanceof RegistryValue) {
            return ((RegistryValue) value).key();
        }
        if (value instanceof ConfigurationSerializable) {
            return value;
        }
        if (value instanceof Map<?, ?>) {
            Map<Object, Object> result = new LinkedHashMap<>();
            resolved.put(value, result);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                Object key = normalizeRuntimeValue(entry.getKey(), resolved, depth + 1);
                if (result.containsKey(key)) {
                    throw new IllegalArgumentException("Duplicate legacy metadata map key " + key);
                }
                result.put(key, normalizeRuntimeValue(entry.getValue(), resolved, depth + 1));
            }
            return result;
        }
        if (value instanceof List<?>) {
            List<Object> result = new ArrayList<>(((List<?>) value).size());
            resolved.put(value, result);
            for (Object item : (List<?>) value) {
                result.add(normalizeRuntimeValue(item, resolved, depth + 1));
            }
            return result;
        }
        if (value instanceof Set<?>) {
            Set<Object> result = new LinkedHashSet<>();
            resolved.put(value, result);
            for (Object item : (Set<?>) value) {
                if (!result.add(normalizeRuntimeValue(item, resolved, depth + 1))) {
                    throw new IllegalArgumentException("Duplicate legacy metadata set value");
                }
            }
            return result;
        }
        if (value instanceof Object[]) {
            Object[] source = (Object[]) value;
            Object[] result = source.clone();
            resolved.put(value, result);
            Class<?> component = value.getClass().getComponentType();
            for (int index = 0; index < source.length; index++) {
                Object item = normalizeRuntimeValue(source[index], resolved, depth + 1);
                if (item != null && !component.isInstance(item)) {
                    throw new IllegalArgumentException("Incompatible legacy metadata array value for " + component.getName());
                }
                result[index] = item;
            }
            return result;
        }
        return value;
    }

    static byte[] encode(List<Object> metadata) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(metadata, "metadata");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MetadataOutput output = new MetadataOutput(bytes)) {
            output.writeObject(metadata);
        }
        requireLength(bytes.size());
        return bytes.toByteArray();
    }

    private static void requireLength(int length) {
        if (length > BinaryCodecSupport.MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("Legacy metadata exceeds the maximum encoded size");
        }
    }

    private static ObjectStreamClass validateWrapper(ObjectStreamClass descriptor) throws InvalidClassException {
        if (descriptor == null) {
            throw new InvalidClassException(WRAPPER_CLASS, "Legacy metadata wrapper is not serializable");
        }
        ObjectStreamField[] fields = descriptor.getFields();
        if (descriptor.getSerialVersionUID() != WRAPPER_UID || fields.length != 1
                || !fields[0].getName().equals("map") || !"Ljava/util/Map;".equals(fields[0].getTypeString())) {
            throw new InvalidClassException(WRAPPER_CLASS, "Unsupported legacy metadata wrapper layout");
        }
        return descriptor;
    }

    interface RegistryValue {

        String name();

        default String key() {
            return "minecraft:" + name().toLowerCase(Locale.ROOT);
        }
    }

    private static Class<? extends Enum<?>> registryEnum(String type) {
        switch (type) {
            case "org.bukkit.attribute.Attribute":
                return AttributeValue.class;
            case "org.bukkit.entity.Villager$Profession":
                return VillagerProfessionValue.class;
            case "org.bukkit.entity.Villager$Type":
                return VillagerTypeValue.class;
            case "org.bukkit.entity.Cat$Type":
                return CatTypeValue.class;
            case "org.bukkit.entity.Frog$Variant":
                return FrogVariantValue.class;
            case "org.bukkit.Sound":
                return LegacySoundValue.class;
            case "org.bukkit.block.banner.PatternType":
                return LegacyRegistryEnums.PatternValue.class;
            case "org.bukkit.Art":
                return LegacyRegistryEnums.ArtValue.class;
            case "org.bukkit.block.Biome":
                return LegacyRegistryEnums.BiomeValue.class;
            case "org.bukkit.Fluid":
                return LegacyRegistryEnums.FluidValue.class;
            case "org.bukkit.map.MapCursor$Type":
                return LegacyRegistryEnums.MapCursorValue.class;
            default:
                return null;
        }
    }

    static String legacyRegistryKey(String type, String name) {
        Class<? extends Enum<?>> enumType = registryEnum(type);
        if (enumType != null) {
            try {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                RegistryValue value = (RegistryValue) Enum.valueOf((Class) enumType, name);
                return value.key();
            }
            catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    enum AttributeValue implements RegistryValue {
        GENERIC_MAX_HEALTH,
        GENERIC_FOLLOW_RANGE,
        GENERIC_KNOCKBACK_RESISTANCE,
        GENERIC_MOVEMENT_SPEED,
        GENERIC_FLYING_SPEED,
        GENERIC_ATTACK_DAMAGE,
        GENERIC_ATTACK_KNOCKBACK,
        GENERIC_ATTACK_SPEED,
        GENERIC_ARMOR,
        GENERIC_ARMOR_TOUGHNESS,
        GENERIC_FALL_DAMAGE_MULTIPLIER,
        GENERIC_LUCK,
        GENERIC_MAX_ABSORPTION,
        GENERIC_SAFE_FALL_DISTANCE,
        GENERIC_SCALE,
        GENERIC_STEP_HEIGHT,
        GENERIC_GRAVITY,
        GENERIC_JUMP_STRENGTH,
        GENERIC_BURNING_TIME,
        GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE,
        GENERIC_MOVEMENT_EFFICIENCY,
        GENERIC_OXYGEN_BONUS,
        GENERIC_WATER_MOVEMENT_EFFICIENCY,
        PLAYER_BLOCK_INTERACTION_RANGE,
        PLAYER_ENTITY_INTERACTION_RANGE,
        PLAYER_BLOCK_BREAK_SPEED,
        PLAYER_MINING_EFFICIENCY,
        PLAYER_SNEAKING_SPEED,
        PLAYER_SUBMERGED_MINING_SPEED,
        PLAYER_SWEEPING_DAMAGE_RATIO,
        HORSE_JUMP_STRENGTH,
        ZOMBIE_SPAWN_REINFORCEMENTS;

        @Override
        public String key() {
            String name = name();
            return "minecraft:" + name.substring(name.indexOf('_') + 1).toLowerCase(Locale.ROOT);
        }
    }

    enum VillagerProfessionValue implements RegistryValue {
        NONE,
        ARMORER,
        BUTCHER,
        CARTOGRAPHER,
        CLERIC,
        FARMER,
        FISHERMAN,
        FLETCHER,
        LEATHERWORKER,
        LIBRARIAN,
        MASON,
        NITWIT,
        SHEPHERD,
        TOOLSMITH,
        WEAPONSMITH
    }

    enum VillagerTypeValue implements RegistryValue {
        DESERT,
        JUNGLE,
        PLAINS,
        SAVANNA,
        SNOW,
        SWAMP,
        TAIGA
    }

    enum CatTypeValue implements RegistryValue {
        TABBY,
        BLACK,
        RED,
        SIAMESE,
        BRITISH_SHORTHAIR,
        CALICO,
        PERSIAN,
        RAGDOLL,
        WHITE,
        JELLIE,
        ALL_BLACK
    }

    enum FrogVariantValue implements RegistryValue {
        TEMPERATE,
        WARM,
        COLD
    }

    static final class ConfigurationValue implements Serializable {

        private static final long serialVersionUID = WRAPPER_UID;
        private final Map<String, Object> map;

        ConfigurationValue(String alias, Map<String, Object> values) {
            if (alias == null || alias.isEmpty()) {
                throw new IllegalArgumentException("Legacy metadata configuration alias is missing");
            }
            if (values.containsKey(ALIAS_KEY)) {
                throw new IllegalArgumentException("Legacy metadata configuration values contain an alias");
            }
            map = new LinkedHashMap<>();
            map.put(ALIAS_KEY, alias);
            map.putAll(values);
        }

        String alias() {
            Object alias = map == null ? null : map.get(ALIAS_KEY);
            if (!(alias instanceof String) || ((String) alias).isEmpty()) {
                throw new IllegalArgumentException("Legacy metadata configuration alias is missing");
            }
            return (String) alias;
        }

        Map<String, Object> values() {
            Map<String, Object> values = new LinkedHashMap<>(map);
            values.remove(ALIAS_KEY);
            return values;
        }
    }

    private static final class MetadataInput extends ObjectInputStream {

        private final boolean neutral;
        private boolean registryValues;

        private MetadataInput(InputStream input, boolean neutral) throws IOException {
            super(input);
            this.neutral = neutral;
            ObjectInputFilter inherited = getObjectInputFilter();
            setObjectInputFilter(info -> {
                if (info.depth() > BinaryCodecSupport.MAX_DEPTH || info.references() > MAX_REFERENCES
                        || info.arrayLength() > BinaryCodecSupport.MAX_CONTAINER_LENGTH
                        || info.streamBytes() > BinaryCodecSupport.MAX_ENCODED_LENGTH
                        || (info.serialClass() != null && !isAllowedClass(info.serialClass()))) {
                    return ObjectInputFilter.Status.REJECTED;
                }
                return inherited == null ? ObjectInputFilter.Status.UNDECIDED : inherited.checkInput(info);
            });
            enableResolveObject(!neutral);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor) throws IOException, ClassNotFoundException {
            String name = descriptor.getName();
            int dimensions = arrayDimensions(name);
            if (descriptor.forClass() == null && !isAllowedName(componentName(name, dimensions), dimensions > 0)) {
                throw new InvalidClassException(name, "Unsupported legacy metadata type");
            }
            Class<?> type = super.resolveClass(descriptor);
            if (!isAllowedClass(type)) {
                throw new InvalidClassException(descriptor.getName(), "Unsupported legacy metadata type");
            }
            return type;
        }

        @Override
        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException {
            throw new InvalidClassException("Proxy classes are not supported in legacy metadata");
        }

        @Override
        protected Object resolveObject(Object value) {
            if (!(value instanceof ConfigurationValue)) {
                return value;
            }
            ConfigurationValue configuration = (ConfigurationValue) value;
            return BinaryCodecSupport.restoreConfiguration(configuration.alias(), configuration.values());
        }

        private static boolean isAllowedClass(Class<?> type) {
            boolean array = type.isArray();
            while (type.isArray()) {
                type = type.getComponentType();
            }
            if (type.isPrimitive() || (array && type == Serializable.class) || SIMPLE_TYPES.contains(type) || COLLECTION_TYPES.contains(type.getName())
                    || type == ConfigurationValue.class || (type.isEnum() && RegistryValue.class.isAssignableFrom(type))) {
                return true;
            }
            boolean platformType = type.getClassLoader() == ConfigurationSerializable.class.getClassLoader()
                    && isPlatformMetadataName(type.getName());
            return platformType && (type.isEnum() || BinaryCodecSupport.isPlatformConfigurationType(type));
        }

        private static boolean isAllowedName(String name, boolean array) {
            return SIMPLE_TYPE_NAMES.contains(name) || COLLECTION_TYPES.contains(name) || isPlatformMetadataName(name)
                    || name.startsWith(LegacyMetadataCodec.class.getName() + "$")
                    || name.startsWith(LegacyRegistryEnums.class.getName() + "$") || name.equals(LegacySoundValue.class.getName())
                    || (array && (name.equals(Serializable.class.getName()) || (name.length() == 1 && "BCDFIJSZ".contains(name))));
        }

        private static boolean isPlatformMetadataName(String name) {
            return name.startsWith("org.bukkit.") || name.startsWith("io.papermc.paper.") || name.startsWith("com.destroystokyo.paper.");
        }

        private static int arrayDimensions(String name) {
            int dimensions = 0;
            while (dimensions < name.length() && name.charAt(dimensions) == '[') {
                dimensions++;
            }
            return dimensions;
        }

        private static String componentName(String name, int dimensions) {
            return dimensions > 0 && name.startsWith("L", dimensions) && name.endsWith(";")
                    ? name.substring(dimensions + 1, name.length() - 1) : name.substring(dimensions);
        }

        private boolean usesMetadataArray(String component) {
            if (registrySurrogate(component) != null) {
                return true;
            }
            if (!isPlatformMetadataName(component)) {
                return false;
            }
            try {
                Class<?> type = Class.forName(component, false, ConfigurationSerializable.class.getClassLoader());
                return neutral ? ConfigurationSerializable.class.isAssignableFrom(type) : type == ConfigurationSerializable.class;
            }
            catch (ClassNotFoundException | LinkageError ignored) {
                return true;
            }
        }

        private Class<? extends Enum<?>> registrySurrogate(String name) {
            Class<? extends Enum<?>> surrogate = registryEnum(name);
            if (!neutral && surrogate != null && surrogate.getDeclaringClass() != LegacyMetadataCodec.class) {
                try {
                    if (Class.forName(name, false, ConfigurationSerializable.class.getClassLoader()).isEnum()) {
                        return null;
                    }
                }
                catch (ClassNotFoundException | LinkageError ignored) {
                }
            }
            return surrogate;
        }

        @Override
        protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
            ObjectStreamClass descriptor = super.readClassDescriptor();
            if (descriptor.getName().equals(WRAPPER_CLASS)) {
                validateWrapper(descriptor);
                return ObjectStreamClass.lookup(ConfigurationValue.class);
            }
            Class<?> registryValue = registrySurrogate(descriptor.getName());
            if (registryValue != null) {
                if (descriptor.getSerialVersionUID() != 0 || descriptor.getFields().length != 0) {
                    throw new InvalidClassException(descriptor.getName(), "Unsupported legacy metadata registry enum layout");
                }
                registryValues = true;
                return ObjectStreamClass.lookup(registryValue);
            }
            String name = descriptor.getName();
            int dimensions = arrayDimensions(name);
            if (dimensions > 0 && usesMetadataArray(componentName(name, dimensions))) {
                if (dimensions > 255 || descriptor.getFields().length != 0) {
                    throw new InvalidClassException(name, "Unsupported legacy metadata array layout");
                }
                return ObjectStreamClass.lookup(Array.newInstance(Object.class, new int[dimensions]).getClass());
            }
            return descriptor;
        }
    }

    private static final class MetadataOutput extends ObjectOutputStream {

        private final ObjectStreamClass wrapperDescriptor;

        private MetadataOutput(OutputStream output) throws IOException, ClassNotFoundException {
            super(output);
            Class<?> wrapper = Class.forName(WRAPPER_CLASS, false, BukkitObjectInputStream.class.getClassLoader());
            wrapperDescriptor = validateWrapper(ObjectStreamClass.lookup(wrapper));
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object value) {
            if (value instanceof BinaryCodecSupport.EnumValue) {
                Object restored = ((BinaryCodecSupport.EnumValue) value).restore();
                if (restored instanceof Keyed && !(restored instanceof Enum<?>)) {
                    return BinaryCodecSupport.registryKey(restored, "legacy metadata");
                }
                return restored;
            }
            return value;
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            super.writeClassDescriptor(descriptor.forClass() == ConfigurationValue.class ? wrapperDescriptor : descriptor);
        }
    }
}
