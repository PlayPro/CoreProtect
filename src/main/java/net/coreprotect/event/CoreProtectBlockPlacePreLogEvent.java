package net.coreprotect.event;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CoreProtectBlockPlacePreLogEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private String user;
    private final Location location;
    private final BlockState blockState;
    private boolean cancelled = false;

    public CoreProtectBlockPlacePreLogEvent(String user, Location location, BlockState blockState) {
        super(true); // async
        this.user = user;
        this.location = location;
        this.blockState = blockState;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        cancelled = b;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String newUser) {
        if (newUser == null || newUser.isEmpty()) {
            throw new IllegalArgumentException("Invalid user");
        }

        this.user = newUser;
    }

    public Location getLocation() {
        return location;
    }

    public BlockState getBlockState() {
        return blockState;
    }
}
