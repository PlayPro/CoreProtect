package net.coreprotect.api;

import org.bukkit.Location;

/**
 * Shared options for typed lookup API methods.
 */
public final class LookupOptions {
    private final String user;
    private final int time;
    private final int radius;
    private final Location location;
    private final int limitOffset;
    private final int limitCount;

    private LookupOptions(Builder builder) {
        this.user = builder.user;
        this.time = builder.time;
        this.radius = builder.radius;
        this.location = builder.location;
        this.limitOffset = builder.limitOffset;
        this.limitCount = builder.limitCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUser() {
        return user;
    }

    public int getTime() {
        return time;
    }

    public int getRadius() {
        return radius;
    }

    public Location getLocation() {
        return location;
    }

    public int getLimitOffset() {
        return limitOffset;
    }

    public int getLimitCount() {
        return limitCount;
    }

    public boolean hasLimit() {
        return limitOffset >= 0 && limitCount >= 0;
    }

    public static final class Builder {
        private String user;
        private int time;
        private int radius = -1;
        private Location location;
        private int limitOffset = -1;
        private int limitCount = -1;

        private Builder() {
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder time(int time) {
            this.time = time;
            return this;
        }

        public Builder location(Location location) {
            this.location = location;
            this.radius = 0;
            return this;
        }

        public Builder radius(Location location, int radius) {
            this.location = location;
            this.radius = radius;
            return this;
        }

        public Builder limit(int offset, int count) {
            this.limitOffset = offset;
            this.limitCount = count;
            return this;
        }

        public LookupOptions build() {
            return new LookupOptions(this);
        }
    }
}
