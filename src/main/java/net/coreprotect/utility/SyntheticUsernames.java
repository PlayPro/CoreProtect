package net.coreprotect.utility;

import java.util.UUID;

public final class SyntheticUsernames {

    private static final String UUID_SUFFIX = "_uuid:";

    private SyntheticUsernames() {
    }

    public static String normalize(String user) {
        if (user == null) {
            return null;
        }

        int index = user.indexOf(UUID_SUFFIX);
        if (index == -1) {
            return user;
        }

        return user.substring(0, index);
    }

    public static String qualifyWithUuid(String base, UUID uuid) {
        if (base == null || uuid == null) {
            return base;
        }

        return base + UUID_SUFFIX + uuid;
    }
}
