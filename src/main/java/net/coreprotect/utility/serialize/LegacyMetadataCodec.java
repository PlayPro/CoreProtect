package net.coreprotect.utility.serialize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.util.io.BukkitObjectInputStream;

final class LegacyMetadataCodec {

    private static final String WRAPPER_CLASS = "org.bukkit.util.io.Wrapper";
    private static final long WRAPPER_UID = -986209235411767547L;
    private static final String ALIAS_KEY = "==";

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

    private static final class MetadataInput extends BukkitObjectInputStream {

        private final boolean neutral;
        private boolean registryValues;

        private MetadataInput(InputStream input, boolean neutral) throws IOException {
            super(input);
            this.neutral = neutral;
            enableResolveObject(!neutral);
        }

        @Override
        protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
            ObjectStreamClass descriptor = super.readClassDescriptor();
            if (neutral && descriptor.getName().equals(WRAPPER_CLASS)) {
                validateWrapper(descriptor);
                return ObjectStreamClass.lookup(ConfigurationValue.class);
            }
            Class<?> registryValue;
            switch (descriptor.getName()) {
                case "org.bukkit.attribute.Attribute":
                    registryValue = AttributeValue.class;
                    break;
                case "org.bukkit.entity.Villager$Profession":
                    registryValue = VillagerProfessionValue.class;
                    break;
                case "org.bukkit.entity.Villager$Type":
                    registryValue = VillagerTypeValue.class;
                    break;
                case "org.bukkit.entity.Cat$Type":
                    registryValue = CatTypeValue.class;
                    break;
                case "org.bukkit.entity.Frog$Variant":
                    registryValue = FrogVariantValue.class;
                    break;
                default:
                    registryValue = null;
                    break;
            }
            if (registryValue != null) {
                if (descriptor.getSerialVersionUID() != 0 || descriptor.getFields().length != 0) {
                    throw new InvalidClassException(descriptor.getName(), "Unsupported legacy metadata registry enum layout");
                }
                registryValues = true;
                return ObjectStreamClass.lookup(registryValue);
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
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            super.writeClassDescriptor(descriptor.forClass() == ConfigurationValue.class ? wrapperDescriptor : descriptor);
        }
    }
}
