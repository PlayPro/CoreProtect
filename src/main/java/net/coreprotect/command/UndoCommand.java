package net.coreprotect.command;

import java.util.List;
import java.util.Locale;

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

public class UndoCommand {
    protected static void runCommand(CommandSender user, Command command, boolean permission, String[] args) {
        if (!permission) {
            Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            return;
        }

        try {
            List<Object> savedRollback = ConfigHandler.lastRollback.get(user.getName());
            if (savedRollback != null) {
                long startTime = (Long) savedRollback.get(0);
                long endTime = (Long) savedRollback.get(1);
                String[] savedArgs = (String[]) savedRollback.get(2);
                Location location = (Location) savedRollback.get(3);
                for (int i = 1; i < savedArgs.length; i++) {
                    if (ActionParser.isPreviewArgument(savedArgs[i])) {
                        CancelCommand.cancelPreview(user, command, savedRollback);
                        return;
                    }
                }
                String[] undoArgs = savedArgs.clone();
                boolean valid = true;
                String subcommand = (undoArgs.length > 0) ? undoArgs[0].toLowerCase(Locale.ROOT) : "";
                if (subcommand.equals("rollback") || subcommand.equals("rb") || subcommand.equals("ro")) {
                    undoArgs[0] = "restore";
                }
                else if (subcommand.equals("restore") || subcommand.equals("rs") || subcommand.equals("re")) {
                    undoArgs[0] = "rollback";
                }
                else {
                    valid = false;
                }
                if (valid) {
                    RollbackRestoreCommand.runCommand(user, command, permission, undoArgs, location, startTime, endTime, savedRollback);
                }
            }
            else {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_ROLLBACK, Selector.SECOND));
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }
}
