package net.coreprotect.api.result;

import net.coreprotect.utility.BlockUtils;

/**
 * Represents logged sign text.
 */
public class SignResult implements CoreProtectResult {
    private final long time;
    private final String username;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int actionId;
    private final int color;
    private final int colorSecondary;
    private final int data;
    private final boolean waxed;
    private final boolean front;
    private final String[] lines;

    public SignResult(long time, String username, String world, int x, int y, int z, int actionId, int color, int colorSecondary, int data, boolean waxed, boolean front, String[] lines) {
        this.time = time;
        this.username = username;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.actionId = actionId;
        this.color = color;
        this.colorSecondary = colorSecondary;
        this.data = data;
        this.waxed = waxed;
        this.front = front;
        this.lines = lines.clone();
    }

    public int getActionId() {
        return actionId;
    }

    public String getActionString() {
        switch (actionId) {
            case 0:
                return "break";
            case 1:
                return "place";
            case 2:
                return "edit";
            default:
                return "unknown";
        }
    }

    public int getColor() {
        return color;
    }

    public int getColorSecondary() {
        return colorSecondary;
    }

    public int getData() {
        return data;
    }

    public String getLine(int line) {
        if (line < 0 || line >= lines.length) {
            return "";
        }

        return lines[line];
    }

    public String[] getLines() {
        return lines.clone();
    }

    public String getMessage() {
        StringBuilder message = new StringBuilder();
        int startLine = front ? 0 : 4;
        int endLine = front ? 4 : 8;

        for (int index = startLine; index < endLine; index++) {
            String line = lines[index];
            if (line != null && !line.isEmpty()) {
                message.append(line);
                if (!line.endsWith(" ")) {
                    message.append(" ");
                }
            }
        }

        return message.toString();
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

    public boolean isBackGlowing() {
        return BlockUtils.isSideGlowing(false, data);
    }

    public boolean isFront() {
        return front;
    }

    public boolean isFrontGlowing() {
        return BlockUtils.isSideGlowing(true, data);
    }

    public boolean isWaxed() {
        return waxed;
    }

    public String worldName() {
        return world;
    }
}
