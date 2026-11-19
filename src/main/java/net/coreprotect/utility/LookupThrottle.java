package net.coreprotect.utility;

import net.coreprotect.config.ConfigHandler;

public final class LookupThrottle {
    private static final ThreadLocal<Claim> CURRENT_LOOKUP = new ThreadLocal<>();

    private LookupThrottle() {
    }

    public static Claim acquire(String name, long cooldownMillis) {
        synchronized (ConfigHandler.lookupThrottle) {
            long now = System.currentTimeMillis();
            Object[] state = ConfigHandler.lookupThrottle.get(name);
            if (state != null && ((boolean) state[0] || now - (long) state[1] < cooldownMillis)) {
                return null;
            }

            Object[] active = new Object[] { true, now };
            ConfigHandler.lookupThrottle.put(name, active);
            return new Claim(name, active);
        }
    }

    public static void publish(String name, Runnable update) {
        synchronized (ConfigHandler.lookupThrottle) {
            Claim claim = CURRENT_LOOKUP.get();
            if (claim == null || (claim.name.equals(name) && claim.isCurrent())) {
                update.run();
            }
        }
    }

    public static final class Claim implements AutoCloseable {
        private final String name;
        private final Object[] entry;

        private Claim(String name, Object[] entry) {
            this.name = name;
            this.entry = entry;
        }

        public boolean isCurrent() {
            return ConfigHandler.lookupThrottle.get(name) == entry;
        }

        public void runLookup(Runnable action) {
            if (!isCurrent()) {
                return;
            }
            CURRENT_LOOKUP.set(this);
            try {
                action.run();
            }
            finally {
                CURRENT_LOOKUP.remove();
                close();
            }
        }

        @Override
        public void close() {
            synchronized (ConfigHandler.lookupThrottle) {
                if (isCurrent()) {
                    ConfigHandler.lookupThrottle.put(name, new Object[] { false, System.currentTimeMillis() });
                }
            }
        }
    }
}
