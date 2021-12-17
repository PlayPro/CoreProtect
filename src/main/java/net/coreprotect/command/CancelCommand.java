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

public class CancelCommand {
    protected static void runCommand(CommandSender user, Command command, boolean permission, String[] args) {
        try {
            if (ConfigHandler.lastRollback.get(user.getName()) != null) {
                List<Object> list = ConfigHandler.lastRollback.get(user.getName());
                long time = (Long) list.get(0);
                args = (String[]) list.get(1);
                Location location = (Location) list.get(2);
                boolean valid = false;
                for (int i = 0; i < args.length; i++) {
                    if (args[i].equals("#preview")) {
                        valid = true;
                        args[i] = args[i].replaceAll("#preview", "#preview_cancel");
                    }
                }
                if (!valid) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_ROLLBACK, Selector.FIRST));
                }
                else {
                    ConfigHandler.lastRollback.remove(user.getName());
                    RollbackRestoreCommand.runCommand(user, command, permission, args, location, time);
                }
            }
            else {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_ROLLBACK, Selector.FIRST));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
