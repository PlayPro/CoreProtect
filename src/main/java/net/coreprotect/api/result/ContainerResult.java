package net.coreprotect.api.result;

import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;
import org.bukkit.Material;

/**
 * Represents the result of a container action lookup with typed fields.
 */
public class ContainerResult {
    private final long time;
    private final String username;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int type;
    private final int data;
    private final int amount;
    private final byte[] metadata;
    private final int action;
    private final int rolledBack;

    /**
     * Creates a new ContainerResult with the specified parameters.
     *
     * @param time       The timestamp in seconds
     * @param username   The username of the player who performed the action
     * @param world      The world name where the action occurred
     * @param x          The X coordinate
     * @param y          The Y coordinate
     * @param z          The Z coordinate
     * @param type       The material type ID
     * @param data       The data value
     * @param amount     The amount of items
     * @param metadata   The item metadata
     * @param action     The action ID
     * @param rolledBack Whether the action has been rolled back
     * @throws IllegalArgumentException if validation fails
     */
    public ContainerResult(long time, String username, String world, int x, int y, int z,
                           int type, int data, int amount, byte[] metadata, int action, int rolledBack) {
        // Validation
        if (time < 0) {
            throw new IllegalArgumentException("Time cannot be negative");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (type < 0) {
            throw new IllegalArgumentException("Type cannot be negative");
        }
        if (data < 0) {
            throw new IllegalArgumentException("Data cannot be negative");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (action < 0) {
            throw new IllegalArgumentException("Action cannot be negative");
        }
        if (rolledBack < 0) {
            throw new IllegalArgumentException("Rolled back cannot be negative");
        }

        this.time = time;
        this.username = username.trim();
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.data = data;
        this.amount = amount;
        this.metadata = metadata != null ? metadata.clone() : null;
        this.action = action;
        this.rolledBack = rolledBack;
    }

    /**
     * Gets the action ID.
     *
     * @return The action ID
     */
    public int getActionId() {
        return action;
    }

    /**
     * Gets the action as a human-readable string.
     *
     * @return "remove", "add", or "unknown"
     */
    public String getActionString() {
        if (action == 0) {
            return "remove";
        } else if (action == 1) {
            return "add";
        }
        return "unknown";
    }

    /**
     * Gets the data value.
     *
     * @return The data value
     */
    public int getData() {
        return data;
    }

    /**
     * Gets the amount of items.
     *
     * @return The amount
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Gets the username of the player who performed the action.
     *
     * @return The player username
     */
    public String getPlayer() {
        return username;
    }

    /**
     * Gets the timestamp in milliseconds.
     *
     * @return The timestamp in milliseconds
     */
    public long getTimestamp() {
        return time * 1000L;
    }

    /**
     * Gets the material type of the item.
     *
     * @return The Material, or Material.AIR if unknown
     */
    public Material getType() {
        Material material = MaterialUtils.getType(type);
        return material != null ? material : Material.AIR;
    }

    /**
     * Gets the X coordinate.
     *
     * @return The X coordinate
     */
    public int getX() {
        return x;
    }

    /**
     * Gets the Y coordinate.
     *
     * @return The Y coordinate
     */
    public int getY() {
        return y;
    }

    /**
     * Gets the Z coordinate.
     *
     * @return The Z coordinate
     */
    public int getZ() {
        return z;
    }

    /**
     * Gets the item metadata.
     *
     * @return A copy of the metadata array, or null if no metadata
     */
    public byte[] getMetadata() {
        return metadata != null ? metadata.clone() : null;
    }

    /**
     * Checks if this action has been rolled back.
     *
     * @return true if rolled back, false otherwise
     */
    public boolean isRolledBack() {
        return rolledBack == 1 || rolledBack == 3;
    }

    /**
     * Gets the world name where the action occurred.
     *
     * @return The world name
     */
    public String worldName() {
        return world;
    }
}
