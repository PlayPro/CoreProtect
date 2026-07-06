package net.coreprotect.api.result;

/**
 * Common fields shared by typed CoreProtect lookup results.
 */
public interface CoreProtectResult {
    int getActionId();

    String getActionString();

    String getPlayer();

    long getTimestamp();

    int getX();

    int getY();

    int getZ();

    String worldName();
}
