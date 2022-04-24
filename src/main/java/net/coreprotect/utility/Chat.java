package net.coreprotect.utility;

import java.util.logging.Level;

import net.coreprotect.command.PurgeCommand;
import net.coreprotect.listener.channel.PluginChannelResponseListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.spigot.SpigotAdapter;

public final class Chat {

    public static final String COMPONENT_TAG_OPEN = "<COMPONENT>";
    public static final String COMPONENT_TAG_CLOSE = "</COMPONENT>";
    public static final String COMPONENT_COMMAND = "COMMAND";
    public static final String COMPONENT_POPUP = "POPUP";

    private Chat() {
        throw new IllegalStateException("Utility class");
    }

    public static void sendComponent(CommandSender sender, String string, String bypass) {
        SpigotAdapter.ADAPTER.sendComponent(sender, string, bypass);
    }

    public static void sendComponent(CommandSender sender, String string) {
        sendComponent(sender, string, null);
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (sender instanceof ConsoleCommandSender) {
            message = message.replace(Color.DARK_AQUA, ChatColor.DARK_AQUA.toString());
        }

        sender.sendMessage(message);
    }

    public static void sendConsoleMessage(String string) {
        Bukkit.getServer().getConsoleSender().sendMessage(string);
    }

    public static void console(String string) {
        if (string.startsWith("-") || string.startsWith("[")) {
            Bukkit.getLogger().log(Level.INFO, string);
        }
        else {
            Bukkit.getLogger().log(Level.INFO, "[CoreProtect] " + string);
        }
    }

    public static void sendGlobalMessage(CommandSender user, String string, boolean silent) {
        if (user instanceof ConsoleCommandSender) {
            sendMessage(user, Color.DARK_AQUA + "[CoreProtect] " + Color.WHITE + string);
            return;
        }

        Server server = Bukkit.getServer();
        server.getConsoleSender().sendMessage("[CoreProtect] " + string);
        for (Player player : server.getOnlinePlayers()) {
            if (player.isOp() && !player.getName().equals(user.getName())) {
                sendResponse(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + string, PurgeCommand.typePurgePacket, silent);
            }
        }
        if (user instanceof Player) {
            if (((Player) user).isOnline()) {
                sendResponse(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + string, PurgeCommand.typePurgePacket, silent);
            }
        }
    }

    public static void sendResponse(CommandSender user, String message, String type, boolean silent) {
        sendMessageSilent(user, message, silent);
        sendPluginChatResponseMessage(user, message, type);
    }

    public static void sendPluginChatResponseMessage(CommandSender user, String message, String type) {
        PluginChannelResponseListener.getInstance().sendData(user, message, type);
    }

    public static void sendMessageSilent(CommandSender user, String message, boolean silent) {
        if (silent) {
            return;
        }

        sendMessage(user, message);
    }

    public static void sendComponentSilent(CommandSender user, String message, boolean silent) {
        if (silent) {
            return;
        }

        sendComponent(user, message);
    }

    public static void sendComponentSilent(CommandSender user, String message, String bypass, boolean silent) {
        if (silent) {
            return;
        }

        sendComponent(user, message, bypass);
    }
}
