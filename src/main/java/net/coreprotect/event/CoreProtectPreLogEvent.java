package net.coreprotect.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import net.coreprotect.utility.StringUtils;

public class CoreProtectPreLogEvent extends Event implements Cancellable {

    public enum Action {
        UNKNOWN,
        BLOCK_BREAK,
        BLOCK_PLACE,
        PLAYER_INTERACTION,
        ENTITY_KILL,
        PLAYER_KILL,
        SIGN_TEXT,
        CONTAINER_TRANSACTION,
        ITEM_TRANSACTION,
        PLAYER_COMMAND,
        ENTITY_SPAWN,
        ENTITY_INTERACTION,
        PLAYER_CHAT,
        PLAYER_SESSION
    }

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private String user;
    private Location location;
    private final Action action;
    private final int actionId;
    private final Material material;
    private final EntityType entityType;
    private final String message;

    public CoreProtectPreLogEvent(String user, Location location) {
        this(user, location, Action.UNKNOWN, -1, null, null, null);
    }

    public CoreProtectPreLogEvent(String user, Location location, Action action, int actionId, Material material, EntityType entityType, String message) {
        super(Bukkit.getServer() == null || !Bukkit.isPrimaryThread());
        this.user = StringUtils.normalizeUsername(user);
        this.location = location == null ? null : location.clone();
        this.action = action == null ? Action.UNKNOWN : action;
        this.actionId = actionId;
        this.material = material;
        this.entityType = entityType;
        this.message = message;
    }

    /**
     * Backwards-compatible constructor for API v10 and earlier.
     * Defaults location to null; listeners expecting v10 signature can still subscribe.
     */
    @Deprecated
    public CoreProtectPreLogEvent(String user) {
        this(user, null);
    }

    public String getUser() {
        return user;
    }

    /** Returns a copy. Use {@link #setLocation(Location)} to relocate the event. */
    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    public Action getAction() {
        return action;
    }

    public int getActionId() {
        return actionId;
    }

    public Material getMaterial() {
        return material;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    public void setUser(String newUser) {
        if (newUser == null || newUser.isEmpty()) {
            throw new IllegalArgumentException("Invalid user");
        }

        this.user = StringUtils.normalizeUsername(newUser);
    }

    public void setLocation(Location newLocation) {
        if (newLocation == null) {
            throw new IllegalArgumentException("Invalid location");
        }

        Location copy = newLocation.clone();
        if (copy.getWorld() == null) {
            throw new IllegalArgumentException("Invalid location");
        }

        this.location = copy;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
