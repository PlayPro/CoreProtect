package net.coreprotect.api.result;

import net.coreprotect.model.action.LookupActions;

/**
 * Represents a logged username used by a player UUID.
 */
public class UsernameResult implements CoreProtectResult {
    private final long time;
    private final String uuid;
    private final String username;
    private final String player;

    public UsernameResult(long time, String uuid, String username, String player) {
        this.time = time;
        this.uuid = uuid;
        this.username = username;
        this.player = player;
    }

    public int getActionId() {
        return LookupActions.USERNAME;
    }

    public String getActionString() {
        return LookupActions.getActionString(getActionId());
    }

    public String getPlayer() {
        return player;
    }

    public long getTimestamp() {
        return time * 1000L;
    }

    public String getUsername() {
        return username;
    }

    public String getUuid() {
        return uuid;
    }

    public int getX() {
        return 0;
    }

    public int getY() {
        return 0;
    }

    public int getZ() {
        return 0;
    }

    public String worldName() {
        return "";
    }
}
