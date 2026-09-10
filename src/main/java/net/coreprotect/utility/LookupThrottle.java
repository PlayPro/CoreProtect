package net.coreprotect.utility;

import net.coreprotect.config.ConfigHandler;

public final class LookupThrottle {

    private LookupThrottle() {
    }

    public static boolean tryAcquire(String name, long cooldownMillis) {
        synchronized (ConfigHandler.lookupThrottle) {
            long now = System.currentTimeMillis();
            Object[] state = ConfigHandler.lookupThrottle.get(name);
            if (state != null && ((boolean) state[0] || now - (long) state[1] < cooldownMillis)) {
                return false;
            }

            ConfigHandler.lookupThrottle.put(name, new Object[] { true, now });
            return true;
        }
    }

    public static void release(String name) {
        synchronized (ConfigHandler.lookupThrottle) {
            ConfigHandler.lookupThrottle.put(name, new Object[] { false, System.currentTimeMillis() });
        }
    }
}
