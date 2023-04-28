package net.coreprotect.utility;

import java.util.logging.Level;

import net.coreprotect.listener.channel.PluginChannelInputListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.command.PurgeCommand;
import net.coreprotect.listener.channel.PluginChannelResponseListener;
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
        if (PluginChannelInputListener.getInstance().isNotSilentChatPlayer(sender)) {
            SpigotAdapter.ADAPTER.sendComponent(sender, string, bypass);
        }
    }

    public static void sendComponent(CommandSender sender, String string) {
        sendComponent(sender, string, null);
    }

    public static void sendMessage(CommandSender sender, String message) {
        sendMessage(sender, message, null);
    }
    public static void sendMessage(CommandSender sender, String message, String type) {
        if (sender instanceof ConsoleCommandSender) {
            message = message.replace(Color.DARK_AQUA, ChatColor.DARK_AQUA.toString());
        }

        if (PluginChannelInputListener.getInstance().isNotSilentChatPlayer(sender)) {
            sender.sendMessage(message);
        }

        if (type != null && !type.isEmpty()) {
            sendPluginChatResponseMessage(sender, message, type);
        }
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

    public static void sendGlobalMessage(CommandSender user, String string) {
        if (user instanceof ConsoleCommandSender) {
            sendMessage(user, Color.DARK_AQUA + "[CoreProtect] " + Color.WHITE + string);
            return;
        }

        Server server = Bukkit.getServer();
        server.getConsoleSender().sendMessage("[CoreProtect] " + string);
        for (Player player : server.getOnlinePlayers()) {
            if (player.isOp() && !player.getName().equals(user.getName())) {
                sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + string, PurgeCommand.typePurgePacket);
            }
        }
        if (user instanceof Player) {
            if (((Player) user).isOnline()) {
                sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + string, PurgeCommand.typePurgePacket);
            }
        }
    }

    public static void sendPluginChatResponseMessage(CommandSender user, String message, String type) {
        PluginChannelResponseListener.getInstance().sendData(user, message, type);
    }
}
