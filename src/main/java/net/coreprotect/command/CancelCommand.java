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
            cancelPreview(user, command, ConfigHandler.lastRollback.get(user.getName()));
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    static void cancelPreview(CommandSender user, Command command, List<Object> savedRollback) {
        if (savedRollback != null) {
            long startTime = (Long) savedRollback.get(0);
            long endTime = (Long) savedRollback.get(1);
            String[] cancelArgs = ((String[]) savedRollback.get(2)).clone();
            Location location = (Location) savedRollback.get(3);
            boolean preview = false;
            for (int i = 1; i < cancelArgs.length; i++) {
                if (ActionParser.isPreviewArgument(cancelArgs[i])) {
                    preview = true;
                    cancelArgs[i] = "#preview_cancel";
                }
            }
            if (preview) {
                RollbackRestoreCommand.runCommand(user, command, true, cancelArgs, location, startTime, endTime, savedRollback);
                return;
            }
        }
        Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_ROLLBACK, Selector.FIRST));
    }
}
