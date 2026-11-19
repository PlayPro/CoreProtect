package net.coreprotect.command;

import java.util.Locale;

import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class InspectCommand {
    protected static void runCommand(CommandSender player, boolean permission, String[] args) {
        if (permission) {
            int command = -1;
            String invalidParameter = args.length > 2 ? args[2] : null;
            if (args.length == 2) {
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "on":
                    case "enable":
                    case "true":
                    case "1":
                        command = 1;
                        break;
                    case "off":
                    case "disable":
                    case "false":
                    case "0":
                        command = 0;
                        break;
                    case "toggle":
                        break;
                    default:
                        invalidParameter = args[1];
                }
            }
            if (invalidParameter != null) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_PARAMETER, invalidParameter));
                return;
            }

            ConfigHandler.inspecting.putIfAbsent(player.getName(), false);
            if (!ConfigHandler.inspecting.get(player.getName())) {
                if (command == 0) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INSPECTOR_ERROR, Selector.SECOND)); // already disabled
                }
                else {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INSPECTOR_TOGGLED, Selector.FIRST)); // now enabled
                    ConfigHandler.inspecting.put(player.getName(), true);
                }
            }
            else {
                if (command == 1) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INSPECTOR_ERROR, Selector.FIRST)); // already enabled
                }
                else {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INSPECTOR_TOGGLED, Selector.SECOND)); // now disabled
                    ConfigHandler.inspecting.put(player.getName(), false);
                }
            }
        }
        else {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
        }
    }
}
