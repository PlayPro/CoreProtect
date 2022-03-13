package net.coreprotect.command;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class UndoCommand {
    protected static void runCommand(CommandSender user, Command command, boolean permission, String[] args) {
        try {
            if (ConfigHandler.lastRollback.get(user.getName()) != null) {
                List<Object> list = ConfigHandler.lastRollback.get(user.getName());
                long startTime = (Long) list.get(0);
                long endTime = (Long) list.get(1);
                args = (String[]) list.get(2);
                Location location = (Location) list.get(3);
                for (String arg : args) {
                    if (arg.equals("#preview")) {
                        CancelCommand.runCommand(user, command, permission, args);
                        return;
                    }
                }
                boolean valid = true;
                if (args[0].equals("rollback") || args[0].equals("rb") || args[0].equals("ro")) {
                    args[0] = "restore";
                }
                else if (args[0].equals("restore") || args[0].equals("rs") || args[0].equals("re")) {
                    args[0] = "rollback";
                }
                else {
                    valid = false;
                }
                if (valid) {
                    ConfigHandler.lastRollback.remove(user.getName());
                    RollbackRestoreCommand.runCommand(user, command, permission, args, location, startTime, endTime);
                }
            }
            else {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_ROLLBACK, Selector.SECOND));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
