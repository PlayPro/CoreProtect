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
        try {
            if (ConfigHandler.lastRollback.get(user.getName()) != null) {
                List<Object> list = ConfigHandler.lastRollback.get(user.getName());
                long startTime = (Long) list.get(0);
                long endTime = (Long) list.get(1);
                String[] undoArgs = (String[]) list.get(2);
                Location location = (Location) list.get(3);
                for (int i = 1; i < undoArgs.length; i++) {
                    if (ActionParser.isPreviewArgument(undoArgs[i])) {
                        CancelCommand.runCommand(user, command, permission, undoArgs);
                        return;
                    }
                }
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
                    ConfigHandler.lastRollback.remove(user.getName());
                    RollbackRestoreCommand.runCommand(user, command, permission, undoArgs, location, startTime, endTime);
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
