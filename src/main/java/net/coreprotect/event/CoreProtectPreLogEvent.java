package net.coreprotect.event;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CoreProtectPreLogEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private String user;
    private Location location;

    public CoreProtectPreLogEvent(String user, Location location) {
        super(true); // async
        this.user = user;
        this.location = location;
    }

    /**
     * Backwards-compatible constructor for API v10 and earlier.
     * Defaults location to null; listeners expecting v10 signature can still subscribe.
     */
    @Deprecated
    public CoreProtectPreLogEvent(String user) {
        super(true); // async
        this.user = user;
        this.location = null;
    }

    public String getUser() {
        return user;
    }

    public Location getLocation() {
        return location;
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

        this.user = newUser;
    }

    public void setLocation(Location newLocation) {
        if (newLocation == null) {
            throw new IllegalArgumentException("Invalid location");
        }

        this.location = newLocation;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
