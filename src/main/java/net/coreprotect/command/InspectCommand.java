package net.coreprotect.command;

import java.util.Locale;
import java.util.Map;

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

            Map<String, Boolean> inspecting = ConfigHandler.inspecting;
            String playerName = player.getName();
            boolean wasEnabled;
            boolean enabled;
            synchronized (inspecting) {
                wasEnabled = Boolean.TRUE.equals(inspecting.get(playerName));
                enabled = command < 0 ? !wasEnabled : command == 1;
                inspecting.put(playerName, enabled);
            }
            Phrase phrase = enabled == wasEnabled ? Phrase.INSPECTOR_ERROR : Phrase.INSPECTOR_TOGGLED;
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(phrase, enabled ? Selector.FIRST : Selector.SECOND));
        }
        else {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
        }
    }
}
