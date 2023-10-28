package net.coreprotect.command;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.language.Phrase;
import net.coreprotect.thread.NetworkHandler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;
import org.bukkit.command.CommandSender;

public class ReloadCommand {
    protected static void runCommand(final CommandSender player, boolean permission, String[] args) {
        if (permission) {
            if (ConfigHandler.converterRunning) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
                return;
            }
            if (ConfigHandler.purgeRunning) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
                return;
            }
            if (ConfigHandler.lookupThrottle.get(player.getName()) != null) {
                Object[] lookupThrottle = ConfigHandler.lookupThrottle.get(player.getName());
                if ((boolean) lookupThrottle[0] || ((System.currentTimeMillis() - (long) lookupThrottle[1])) < 100) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                    return;
                }
            }
            ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });

            class BasicThread implements Runnable {
                @Override
                public void run() {
                    try {
                        if (Consumer.isPaused) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.RELOAD_STARTED));
                        }
                        while (Consumer.isPaused) {
                            Thread.sleep(1);
                        }
                        Consumer.isPaused = true;

                        ConfigHandler.performInitialization(false);
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.RELOAD_SUCCESS));

                        Thread networkHandler = Util.THREAD_FACTORY.newThread(new NetworkHandler(false, false));
                        networkHandler.start();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }

                    Consumer.isPaused = false;
                    ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
                }
            }
            Runnable runnable = new BasicThread();
            Thread thread = Util.THREAD_FACTORY.newThread(runnable);
            thread.start();
        }
        else {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
        }
    }
}
