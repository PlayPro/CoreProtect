package net.coreprotect.command;

import org.bukkit.command.CommandSender;

import net.coreprotect.config.Config;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class NetworkDebugCommand {
    protected static void runCommand(CommandSender player, boolean permission, String[] args) {
        if (!permission || !Config.getGlobal().NETWORK_DEBUG) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            return;
        }

        try {
            PluginChannelListener.getInstance().sendTest(player, args.length == 2 ? args[1] : "");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
