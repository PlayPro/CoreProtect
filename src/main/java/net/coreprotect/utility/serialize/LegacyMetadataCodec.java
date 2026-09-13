package net.coreprotect.utility.serialize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.util.io.BukkitObjectInputStream;

final class LegacyMetadataCodec {

    private static final String WRAPPER_CLASS = "org.bukkit.util.io.Wrapper";
    private static final String ATTRIBUTE_CLASS = "org.bukkit.attribute.Attribute";
    private static final long WRAPPER_UID = -986209235411767547L;
    private static final String ALIAS_KEY = "==";

    private LegacyMetadataCodec() {
        throw new IllegalStateException("Codec class");
    }

    static List<Object> decode(byte[] encoded) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(encoded, "encoded");
        requireLength(encoded.length);
        try (MetadataInput input = new MetadataInput(new ByteArrayInputStream(encoded))) {
            Object value = input.readObject();
            if (!(value instanceof List<?>)) {
                throw new IllegalArgumentException("Legacy metadata root is not a list");
            }
            @SuppressWarnings("unchecked")
            List<Object> metadata = (List<Object>) value;
            return metadata;
        }
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

    enum AttributeValue {
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

        String key() {
            String name = name();
            return "minecraft:" + name.substring(name.indexOf('_') + 1).toLowerCase(Locale.ROOT);
        }
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

        private MetadataInput(InputStream input) throws IOException {
            super(input);
        }

        @Override
        protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
            ObjectStreamClass descriptor = super.readClassDescriptor();
            if (descriptor.getName().equals(WRAPPER_CLASS)) {
                validateWrapper(descriptor);
                return ObjectStreamClass.lookup(ConfigurationValue.class);
            }
            if (descriptor.getName().equals(ATTRIBUTE_CLASS)) {
                if (descriptor.getSerialVersionUID() != 0 || descriptor.getFields().length != 0) {
                    throw new InvalidClassException(ATTRIBUTE_CLASS, "Unsupported legacy metadata attribute layout");
                }
                return ObjectStreamClass.lookup(AttributeValue.class);
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
