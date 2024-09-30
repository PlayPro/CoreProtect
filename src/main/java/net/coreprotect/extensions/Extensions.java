package net.coreprotect.extensions;

import org.bukkit.command.CommandSender;

public class Extensions {

    public static void runDatabaseMigration(CommandSender user, String[] argumentArray) {
        DatabaseMigration.runCommand(user, argumentArray);
    }

}
