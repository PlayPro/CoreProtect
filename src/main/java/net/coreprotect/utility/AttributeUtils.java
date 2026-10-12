package net.coreprotect.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;

public final class AttributeUtils {

    /**
     * Attribute base values that entity kill data can leave out, frozen from the 26.2 attribute registry defaults.
     * Kill data marks each left-out attribute with the bit of its position here, and a restore sets exactly this
     * value, whatever the defaults of the restoring server are. Entries may only be appended: changing or reordering
     * one would change what the bits of stored kill data mean. The bits are a long, so at most 64 entries fit.
     */
    private static final List<BaselineAttribute> BASELINE = List.of(
            new BaselineAttribute("air_drag_modifier", 1.0),
            new BaselineAttribute("armor", 0.0),
            new BaselineAttribute("armor_toughness", 0.0),
            new BaselineAttribute("attack_damage", 2.0),
            new BaselineAttribute("attack_knockback", 0.0),
            new BaselineAttribute("attack_speed", 4.0),
            new BaselineAttribute("below_name_distance", 10.0),
            new BaselineAttribute("block_break_speed", 1.0),
            new BaselineAttribute("block_interaction_range", 4.5),
            new BaselineAttribute("bounciness", 0.0),
            new BaselineAttribute("burning_time", 1.0),
            new BaselineAttribute("camera_distance", 4.0),
            new BaselineAttribute("entity_interaction_range", 3.0),
            new BaselineAttribute("explosion_knockback_resistance", 0.0),
            new BaselineAttribute("fall_damage_multiplier", 1.0),
            new BaselineAttribute("flying_speed", 0.4),
            new BaselineAttribute("follow_range", 32.0),
            new BaselineAttribute("friction_modifier", 1.0),
            new BaselineAttribute("gravity", 0.08),
            new BaselineAttribute("jump_strength", 0.41999998688697815),
            new BaselineAttribute("knockback_resistance", 0.0),
            new BaselineAttribute("luck", 0.0),
            new BaselineAttribute("max_absorption", 0.0),
            new BaselineAttribute("max_health", 20.0),
            new BaselineAttribute("mining_efficiency", 0.0),
            new BaselineAttribute("movement_efficiency", 0.0),
            new BaselineAttribute("movement_speed", 0.7),
            new BaselineAttribute("name_tag_distance", 64.0),
            new BaselineAttribute("oxygen_bonus", 0.0),
            new BaselineAttribute("safe_fall_distance", 3.0),
            new BaselineAttribute("scale", 1.0),
            new BaselineAttribute("sneaking_speed", 0.3),
            new BaselineAttribute("spawn_reinforcements", 0.0),
            new BaselineAttribute("step_height", 0.6),
            new BaselineAttribute("submerged_mining_speed", 0.2),
            new BaselineAttribute("sweeping_damage_ratio", 0.0),
            new BaselineAttribute("tempt_range", 10.0),
            new BaselineAttribute("water_movement_efficiency", 0.0),
            new BaselineAttribute("waypoint_receive_range", 0.0),
            new BaselineAttribute("waypoint_transmit_range", 0.0)
    );

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

    /**
     * Returns the baseline bit of an attribute that kill data can leave out: it has no modifiers and its base value is
     * the baseline value.
     *
     * @param attributeInstance
     *            the attribute of the entity that is being logged
     * @return the bit, or -1 when the attribute has to be stored
     */
    public static int baselineBit(AttributeInstance attributeInstance) {
        if (!attributeInstance.getModifiers().isEmpty()) {
            return -1;
        }
        NamespacedKey key = ((Keyed) attributeInstance.getAttribute()).getKey();
        if (!key.getNamespace().equals(NamespacedKey.MINECRAFT)) {
            return -1;
        }
        String name = normalize(key.getKey());
        for (int bit = 0; bit < BASELINE.size(); bit++) {
            BaselineAttribute baseline = BASELINE.get(bit);
            if (baseline.key.equals(name)) {
                return Double.compare(attributeInstance.getBaseValue(), baseline.value) == 0 ? bit : -1;
            }
        }
        return -1;
    }

    /**
     * Sets every attribute whose bit is set to its baseline value without modifiers. Attributes without a bit keep the
     * value they spawned with, including attributes that did not exist when the kill was logged.
     *
     * @param attributable
     *            the restored entity
     * @param baselineBits
     *            the baseline bits stored with the kill data
     */
    public static void restoreBaseline(Attributable attributable, long baselineBits) {
        for (int bit = 0; bit < BASELINE.size(); bit++) {
            if ((baselineBits & (1L << bit)) == 0) {
                continue;
            }
            BaselineAttribute baseline = BASELINE.get(bit);
            Attribute attribute;
            try {
                attribute = resolve(NamespacedKey.MINECRAFT + ":" + baseline.key);
            }
            catch (IllegalArgumentException exception) {
                continue;
            }

            AttributeInstance attributeInstance = attributable.getAttribute(attribute);
            if (attributeInstance == null) {
                continue;
            }
            attributeInstance.setBaseValue(baseline.value);
            for (AttributeModifier modifier : new ArrayList<>(attributeInstance.getModifiers())) {
                attributeInstance.removeModifier(modifier);
            }
        }
    }

    private static final class BaselineAttribute {
        private final String key;
        private final double value;

        private BaselineAttribute(String key, double value) {
            this.key = key;
            this.value = value;
        }
    }
}
