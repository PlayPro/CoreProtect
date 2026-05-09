package net.coreprotect.api.result;

/**
 * Represents a logged player login or logout session event.
 */
public class SessionResult implements CoreProtectResult {
    private final long time;
    private final String username;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int actionId;

    public SessionResult(long time, String username, String world, int x, int y, int z, int actionId) {
        this.time = time;
        this.username = username;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.actionId = actionId;
    }

    public int getActionId() {
        return actionId;
    }

    public String getActionString() {
        switch (actionId) {
            case 0:
                return "logout";
            case 1:
                return "login";
            default:
                return "unknown";
        }
    }

    public String getPlayer() {
        return username;
    }

    public long getTimestamp() {
        return time * 1000L;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String worldName() {
        return world;
    }
}
