package net.coreprotect.api;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;

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
    private final List<Material> includeMaterials;
    private final List<Material> excludeMaterials;

    private LookupOptions(Builder builder) {
        this.user = builder.user;
        this.time = builder.time;
        this.radius = builder.radius;
        this.location = builder.location;
        this.limitOffset = builder.limitOffset;
        this.limitCount = builder.limitCount;
        this.includeMaterials = builder.includeMaterials;
        this.excludeMaterials = builder.excludeMaterials;
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

    public List<Material> getIncludeMaterials() {
        return includeMaterials;
    }

    public List<Material> getExcludeMaterials() {
        return excludeMaterials;
    }

    public static final class Builder {
        private String user;
        private int time;
        private int radius = -1;
        private Location location;
        private int limitOffset = -1;
        private int limitCount = -1;
        private List<Material> includeMaterials = List.of();
        private List<Material> excludeMaterials = List.of();

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

        public Builder includeMaterials(List<Material> materials) {
            this.includeMaterials = List.copyOf(materials);
            return this;
        }

        public Builder excludeMaterials(List<Material> materials) {
            this.excludeMaterials = List.copyOf(materials);
            return this;
        }

        public LookupOptions build() {
            return new LookupOptions(this);
        }
    }
}
