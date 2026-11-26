package net.coreprotect.utility;

import java.util.logging.Level;

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
    public static final String COMPONENT_PIPE = "<PIPE/>";
    public static final String COMPONENT_TAG_OPEN_ESCAPED = "<COMPONENT_OPEN/>";
    public static final String COMPONENT_TAG_CLOSE_ESCAPED = "<COMPONENT_CLOSE/>";

    private Chat() {
        throw new IllegalStateException("Utility class");
    }

    public static String escapeComponentTags(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return text.replace(COMPONENT_TAG_OPEN, COMPONENT_TAG_OPEN_ESCAPED)
                .replace(COMPONENT_TAG_CLOSE, COMPONENT_TAG_CLOSE_ESCAPED);
    }

    public static String unescapeComponentTags(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return text.replace(COMPONENT_TAG_OPEN_ESCAPED, COMPONENT_TAG_OPEN)
                .replace(COMPONENT_TAG_CLOSE_ESCAPED, COMPONENT_TAG_CLOSE);
    }

    public static String escapeComponent(String text) {
        return escapeComponentTags(text).replace("|", COMPONENT_PIPE);
    }

    public static String unescapeComponent(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return unescapeComponentTags(text.replace(COMPONENT_PIPE, "|"));
    }

    public static String translateColorCodes(String text) {
        if (text == null) {
            return null;
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static void sendComponent(CommandSender sender, String string, String bypass) {
        SpigotAdapter.ADAPTER.sendComponent(sender, string, bypass);
    }

    public static void sendComponent(CommandSender sender, String string) {
        sendComponent(sender, string, null);
    }

    public static void sendLookupComponents(CommandSender sender, String result) {
        if (result.indexOf('\n') >= 0) {
            for (String line : result.split("\n")) {
                sendComponent(sender, line);
            }
        }
        else if (!result.isEmpty()) {
            sendComponent(sender, result);
        }
    }

    public static void sendLookupComponents(CommandSender sender, Iterable<String> results) {
        for (String line : results) {
            sendComponent(sender, line);
        }
    }

    public static void sendSignLookupComponents(CommandSender sender, Iterable<String> results) {
        for (String row : results) {
            String[] parts = row.split("\n", 2);
            if (!parts[0].isEmpty()) {
                sendComponent(sender, parts[0], parts.length == 2 ? parts[1] : null);
            }
        }
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

    public static void sendGlobalMessage(CommandSender user, String string) {
        if (user instanceof ConsoleCommandSender) {
            sendMessage(user, Color.DARK_AQUA + "[CoreProtect] " + Color.WHITE + string);
            return;
        }

        Server server = Bukkit.getServer();
        server.getConsoleSender().sendMessage("[CoreProtect] " + string);
        for (Player player : server.getOnlinePlayers()) {
            if (player.isOp() && !player.getName().equals(user.getName())) {
                sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + string);
            }
        }
        if (user instanceof Player) {
            if (((Player) user).isOnline()) {
                sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + string);
            }
        }
    }

}
