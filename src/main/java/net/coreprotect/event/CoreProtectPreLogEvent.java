package net.coreprotect.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CoreProtectPreLogEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private String user;

    public CoreProtectPreLogEvent(String user) {
        super(true); // async
        this.user = user;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String newUser) {
        this.user = newUser;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
