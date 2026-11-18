package net.coreprotect.command;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import net.coreprotect.command.parser.ActionParser;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ErrorReporter;

public class CancelCommand {
    protected static void runCommand(CommandSender user, Command command, boolean permission, String[] args) {
        if (!permission) {
            Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            return;
        }

        try {
            if (ConfigHandler.lastRollback.get(user.getName()) != null) {
                List<Object> list = ConfigHandler.lastRollback.get(user.getName());
                long startTime = (Long) list.get(0);
                long endTime = (Long) list.get(1);
                String[] cancelArgs = ((String[]) list.get(2)).clone();
                Location location = (Location) list.get(3);
                boolean valid = false;
                for (int i = 1; i < cancelArgs.length; i++) {
                    if (ActionParser.isPreviewArgument(cancelArgs[i])) {
                        valid = true;
                        cancelArgs[i] = "#preview_cancel";
                    }
                }
                if (!valid) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_ROLLBACK, Selector.FIRST));
                }
                else {
                    ConfigHandler.lastRollback.remove(user.getName());
                    RollbackRestoreCommand.runCommand(user, command, permission, cancelArgs, location, startTime, endTime);
                }
            }
            else {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_ROLLBACK, Selector.FIRST));
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }
}
