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
    private final List<String> users;
    private final List<String> excludeUsers;
    private final List<ContainerAction> containerActions;
    private final List<ItemAction> itemActions;
    private final List<InventoryAction> inventoryActions;
    private final List<BlockAction> blockActions;
    private final List<SessionAction> sessionActions;

    private LookupOptions(Builder builder) {
        this.user = builder.user;
        this.time = builder.time;
        this.radius = builder.radius;
        this.location = builder.location;
        this.limitOffset = builder.limitOffset;
        this.limitCount = builder.limitCount;
        this.includeMaterials = builder.includeMaterials;
        this.excludeMaterials = builder.excludeMaterials;
        this.users = builder.users;
        this.excludeUsers = builder.excludeUsers;
        this.containerActions = builder.containerActions;
        this.itemActions = builder.itemActions;
        this.inventoryActions = builder.inventoryActions;
        this.blockActions = builder.blockActions;
        this.sessionActions = builder.sessionActions;
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

    public List<String> getUsers() {
        return users;
    }

    public List<String> getExcludeUsers() {
        return excludeUsers;
    }

    public List<ContainerAction> getContainerActions() {
        return containerActions;
    }

    public List<ItemAction> getItemActions() {
        return itemActions;
    }

    public List<InventoryAction> getInventoryActions() {
        return inventoryActions;
    }

    public List<BlockAction> getBlockActions() {
        return blockActions;
    }

    public List<SessionAction> getSessionActions() {
        return sessionActions;
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
        private List<String> users = List.of();
        private List<String> excludeUsers = List.of();
        private List<ContainerAction> containerActions = List.of();
        private List<ItemAction> itemActions = List.of();
        private List<InventoryAction> inventoryActions = List.of();
        private List<BlockAction> blockActions = List.of();
        private List<SessionAction> sessionActions = List.of();

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

        public Builder users(List<String> users) {
            this.users = List.copyOf(users);
            return this;
        }

        public Builder excludeUsers(List<String> users) {
            this.excludeUsers = List.copyOf(users);
            return this;
        }

        public Builder containerActions(List<ContainerAction> actions) {
            this.containerActions = List.copyOf(actions);
            return this;
        }

        public Builder itemActions(List<ItemAction> actions) {
            this.itemActions = List.copyOf(actions);
            return this;
        }

        public Builder inventoryActions(List<InventoryAction> actions) {
            this.inventoryActions = List.copyOf(actions);
            return this;
        }

        public Builder blockActions(List<BlockAction> actions) {
            this.blockActions = List.copyOf(actions);
            return this;
        }

        public Builder sessionActions(List<SessionAction> actions) {
            this.sessionActions = List.copyOf(actions);
            return this;
        }

        public LookupOptions build() {
            return new LookupOptions(this);
        }
    }
}
