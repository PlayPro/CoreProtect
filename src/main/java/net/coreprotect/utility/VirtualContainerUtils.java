package net.coreprotect.utility;

import java.util.regex.Pattern;

public final class VirtualContainerUtils {

    // 191 characters fit a utf8mb4 index on older MySQL versions
    private static final int MAXIMUM_KEY_LENGTH = 191;
    private static final Pattern TYPE = Pattern.compile("[a-z0-9._-]+:[a-z0-9._-]+");
    private static final Pattern ID = Pattern.compile("[a-z0-9._-]{1,64}");

    private VirtualContainerUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Builds the stored key of a virtual container, "type/id".
     *
     * @return the key, or null when the type or id is invalid
     */
    public static String getKey(String type, String id) {
        if (!isValidType(type) || id == null || !ID.matcher(id).matches()) {
            return null;
        }
        String key = type + "/" + id;
        return key.length() <= MAXIMUM_KEY_LENGTH ? key : null;
    }

    /**
     * Returns whether a type is a lowercase namespaced key without "/", so a stored key splits into type and id at
     * its only "/".
     */
    public static boolean isValidType(String type) {
        return type != null && type.length() < MAXIMUM_KEY_LENGTH - 1 && TYPE.matcher(type).matches();
    }
}
