package net.coreprotect.command;

import net.coreprotect.language.Phrase;
import net.coreprotect.listener.channel.PluginChannelHandshakeListener;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class NetworkingCommand {
    protected static void runCommand(CommandSender player, boolean permission, String[] args) {
        if (!permission) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            return;
        }

        try {
            StringBuilder players = new StringBuilder();
            for (UUID user : PluginChannelHandshakeListener.getInstance().getPluginChannelPlayers()) {
                players.append(player.getServer().getPlayer(user).getName()).append(",");
            }
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NETWORK_CONNECTED_USERS, players.toString()));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
