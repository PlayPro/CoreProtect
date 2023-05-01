package net.coreprotect.command;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.channel.PluginChannelHandshakeListener;
import net.coreprotect.utility.Chat;
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
                ConfigHandler.isNetworkCommand.put(player.getName(), true);
                LookupCommand.runCommand(player, command, permission, args);
                break;
            case "rollback":
            case "restore":
                RollbackRestoreCommand.runCommand(player, command, permission, args, null, 0, 0);
                break;
            case "purge":
                PurgeCommand.runCommand(player, permission, args);
                break;
            default:
                Chat.sendPluginChatResponseMessage(player, Phrase.build(Phrase.ACTION_NOT_SUPPORTED) + " - " + args[0], "coreprotect:networking");
        }
    }
}
