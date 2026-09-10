package net.coreprotect.utility;

import java.util.Locale;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

public final class AttributeUtils {

    private AttributeUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Attribute resolve(Object value) {
        if (value instanceof Attribute) {
            return (Attribute) value;
        }
        Attribute exact = Registry.ATTRIBUTE.get(key(value));
        if (exact != null) {
            return exact;
        }
        return resolve(value, Registry.ATTRIBUTE);
    }

    static Attribute resolve(Object value, Iterable<Attribute> attributes) {
        if (value instanceof Attribute) {
            return (Attribute) value;
        }
        NamespacedKey key = key(value);
        String normalized = normalize(key.getKey());
        Attribute alias = null;
        for (Attribute candidate : attributes) {
            NamespacedKey candidateKey = ((Keyed) candidate).getKey();
            if (key.equals(candidateKey)) {
                return candidate;
            }
            if (alias == null && key.getNamespace().equals(NamespacedKey.MINECRAFT)
                    && candidateKey.getNamespace().equals(NamespacedKey.MINECRAFT)
                    && normalized.equals(normalize(candidateKey.getKey()))) {
                alias = candidate;
            }
        }
        if (alias != null) {
            return alias;
        }
        throw new IllegalArgumentException("Unknown attribute key " + value);
    }

    private static NamespacedKey key(Object value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Invalid attribute key " + value);
        }

        String name = (String) value;
        if (name.indexOf(':') < 0 && name.equals(name.toUpperCase(Locale.ROOT))) {
            name = name.toLowerCase(Locale.ROOT);
            int separator = name.indexOf('_');
            if (separator > 0 && isLegacyCategory(name.substring(0, separator))) {
                name = name.substring(0, separator) + '.' + name.substring(separator + 1);
            }
        }
        NamespacedKey key = NamespacedKey.fromString(name);
        if (key == null) {
            throw new IllegalArgumentException("Invalid attribute key " + value);
        }

        return key;
    }

    private static String normalize(String key) {
        int separator = key.indexOf('.');
        if (separator > 0 && isLegacyCategory(key.substring(0, separator))) {
            return key.substring(separator + 1);
        }
        return key;
    }

    private static boolean isLegacyCategory(String category) {
        return category.equals("generic") || category.equals("player")
                || category.equals("horse") || category.equals("zombie");
    }
}
