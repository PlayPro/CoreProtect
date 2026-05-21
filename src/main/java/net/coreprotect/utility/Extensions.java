package net.coreprotect.utility;

import java.lang.reflect.Method;

import org.bukkit.command.CommandSender;

import net.coreprotect.language.Phrase;

public class Extensions {

    public static void startBackgroundService() {
        invokeBackgroundService("start");
    }

    public static void stopBackgroundService() {
        invokeBackgroundService("stop");
    }

    public static void runDatabaseMigration(String command, CommandSender user, String[] argumentArray) {
        try {
            Class<?> patchClass = Class.forName("net.coreprotect.utility.extensions.DatabaseMigration");
            Method patchMethod = patchClass.getDeclaredMethod("runCommand", CommandSender.class, String[].class);
            patchMethod.invoke(null, user, argumentArray);
        }
        catch (Exception e) {
            // plugin not compiled with extension
            Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.COMMAND_NOT_FOUND, Color.WHITE, "/co " + command));
        }
    }

    private static void invokeBackgroundService(String methodName) {
        try {
            Class<?> serviceClass = Class.forName("net.coreprotect.utility.extensions.BackgroundService");
            Method serviceMethod = serviceClass.getDeclaredMethod(methodName);
            serviceMethod.invoke(null);
        }
        catch (ClassNotFoundException e) {
            // plugin not compiled with extension
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

}
