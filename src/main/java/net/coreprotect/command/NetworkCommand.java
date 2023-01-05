package net.coreprotect.command;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.channel.PluginChannelHandshakeListener;
import org.bukkit.entity.Player;

public class NetworkCommand extends Queue {
    public static void runServerSideCommand(final Player player, String command, boolean permission, String[] args) {
        if (!PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(player)) {
            return;
        }

        if (Config.getConfig(player.getWorld()).PLAYER_COMMANDS) {
            long timestamp = System.currentTimeMillis() / 1000L;
            Queue.queuePlayerCommand(player, "/" + command + " " + String.join(" ", args), timestamp);
        }

        switch (args[0]) {
            case "lookup":
                LookupCommand.runCommand(player, command, permission, args);
                break;
            case "rollback":
            case "restore":
                RollbackRestoreCommand.runCommand(player, command, permission, args, null, 0, 0);
                break;
            case "purge":
                PurgeCommand.runCommand(player, permission, args);
                break;
        }
    }
}
