package net.coreprotect.api.result;

/**
 * Represents a logged player chat message or command.
 */
public class MessageResult implements CoreProtectResult {
    private final long time;
    private final String username;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final String message;
    private final int actionId;

    public MessageResult(long time, String username, String world, int x, int y, int z, String message, int actionId) {
        this.time = time;
        this.username = username;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.message = message;
        this.actionId = actionId;
    }

    public int getActionId() {
        return actionId;
    }

    public String getActionString() {
        switch (actionId) {
            case 6:
                return "chat";
            case 7:
                return "command";
            default:
                return "unknown";
        }
    }

    public String getMessage() {
        return message;
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
