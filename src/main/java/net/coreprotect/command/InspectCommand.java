package net.coreprotect.command;

import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public class InspectCommand {
    protected static void runCommand(CommandSender player, boolean permission, String[] args) {
        if (permission) {

            int command = -1;
            ConfigHandler.inspecting.putIfAbsent(player.getName(), false);

            if (args.length > 1) {
                String action = args[1];
                if (action.equalsIgnoreCase("on")) {
                    command = 1;
                }
                else if (action.equalsIgnoreCase("off")) {
                    command = 0;
                }
            }

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
