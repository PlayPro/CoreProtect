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
import java.util.Map;
import java.util.Objects;

import org.bukkit.util.io.BukkitObjectInputStream;

final class LegacyBlockMetaCodec {

    private static final String WRAPPER_CLASS = "org.bukkit.util.io.Wrapper";
    private static final long WRAPPER_UID = -986209235411767547L;
    private static final String ALIAS_KEY = "==";

    private LegacyBlockMetaCodec() {
        throw new IllegalStateException("Codec class");
    }

    static List<Object> decode(byte[] encoded) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(encoded, "encoded");
        requireLength(encoded.length);
        try (MetadataInput input = new MetadataInput(new ByteArrayInputStream(encoded))) {
            Object value = input.readObject();
            if (!(value instanceof List<?>)) {
                throw new IllegalArgumentException("Block metadata root is not a list");
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
            throw new IllegalArgumentException("Block metadata exceeds the maximum encoded size");
        }
    }

    private static ObjectStreamClass validateWrapper(ObjectStreamClass descriptor) throws InvalidClassException {
        if (descriptor == null) {
            throw new InvalidClassException(WRAPPER_CLASS, "Block metadata wrapper is not serializable");
        }
        ObjectStreamField[] fields = descriptor.getFields();
        if (descriptor.getSerialVersionUID() != WRAPPER_UID || fields.length != 1
                || !fields[0].getName().equals("map") || !"Ljava/util/Map;".equals(fields[0].getTypeString())) {
            throw new InvalidClassException(WRAPPER_CLASS, "Unsupported block metadata wrapper layout");
        }
        return descriptor;
    }

    static final class ConfigurationValue implements Serializable {

        private static final long serialVersionUID = WRAPPER_UID;
        private final Map<String, Object> map;

        ConfigurationValue(String alias, Map<String, Object> values) {
            if (alias == null || alias.isEmpty()) {
                throw new IllegalArgumentException("Block metadata configuration alias is missing");
            }
            if (values.containsKey(ALIAS_KEY)) {
                throw new IllegalArgumentException("Block metadata configuration values contain an alias");
            }
            map = new LinkedHashMap<>();
            map.put(ALIAS_KEY, alias);
            map.putAll(values);
        }

        String alias() {
            Object alias = map == null ? null : map.get(ALIAS_KEY);
            if (!(alias instanceof String) || ((String) alias).isEmpty()) {
                throw new IllegalArgumentException("Block metadata configuration alias is missing");
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
