package net.coreprotect.command;

import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class ConsumerCommand {

    private ConsumerCommand() {
        throw new IllegalStateException("Command class");
    }

    protected static void runCommand(final CommandSender player, boolean permission, String[] args) {
        if (!permission) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            return;
        }
        if (!(player instanceof ConsoleCommandSender)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.COMMAND_CONSOLE));
            return;
        }
        if (ConfigHandler.converterRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
            return;
        }
        if (ConfigHandler.purgeRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
            return;
        }

        if (args.length == 2) {
            String action = args[1].toLowerCase(Locale.ROOT);
            boolean pauseCommand = (action.equals("pause") || action.equals("disable") || action.equals("stop"));
            boolean resumeCommand = (action.equals("resume") || action.equals("enable") || action.equals("start"));

            if (pauseCommand || resumeCommand) {
                if (ConfigHandler.pauseConsumer) {
                    if (pauseCommand) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.CONSUMER_ERROR, Selector.FIRST)); // already paused
                    }
                    else {
                        ConfigHandler.pauseConsumer = false;
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.CONSUMER_TOGGLED, Selector.SECOND)); // now started
                    }
                }
                else {
                    if (resumeCommand) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.CONSUMER_ERROR, Selector.SECOND)); // already running
                    }
                    else {
                        ConfigHandler.pauseConsumer = true;
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.CONSUMER_TOGGLED, Selector.FIRST)); // now paused
                    }
                }
                return;
            }
        }

        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, Color.WHITE, "/co consumer <pause|resume>"));
    }

}
