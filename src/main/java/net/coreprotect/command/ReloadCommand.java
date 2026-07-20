package net.coreprotect.command;

import java.util.concurrent.CancellationException;

import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Database;
import net.coreprotect.language.Phrase;
import net.coreprotect.thread.NetworkHandler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.ErrorReporter;

public class ReloadCommand {

    private static final long CONNECTION_DRAIN_TIMEOUT_MILLIS = 60_000L;

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
                        performReload(player);
                    }
                    catch (Exception e) {
                        ErrorReporter.report(e);
                    }
                    finally {
                        ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
                    }
                }
            }
            Runnable runnable = new BasicThread();
            Thread thread = new Thread(runnable);
            thread.start();
        }
        else {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
        }
    }

    static synchronized boolean performReload(CommandSender player) throws InterruptedException {
        final boolean resumePersistenceAfterCancellation = !Consumer.isDatabaseReloadPaused();
        boolean resumePersistence = resumePersistenceAfterCancellation;
        Consumer.OperationStartResult startResult = Consumer.beginDatabaseReload();
        if (startResult != Consumer.OperationStartResult.STARTED) {
            Phrase phrase = startResult == Consumer.OperationStartResult.PURGE_RUNNING ? Phrase.PURGE_IN_PROGRESS
                    : startResult == Consumer.OperationStartResult.ROLLBACK_RUNNING ? Phrase.ROLLBACK_IN_PROGRESS
                            : startResult == Consumer.OperationStartResult.PERSISTENCE_HALTED ? Phrase.DATABASE_PERSISTENCE_HALTED : Phrase.DATABASE_BUSY;
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(phrase));
            return false;
        }
        long deadline = System.nanoTime() + CONNECTION_DRAIN_TIMEOUT_MILLIS * 1_000_000L;
        try {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.RELOAD_STARTED));
            if (!Consumer.lockDatabaseReload(CONNECTION_DRAIN_TIMEOUT_MILLIS)) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.RELOAD_FAILED));
                return false;
            }
            if (!Database.awaitConnectionDrain(remainingMillis(deadline))) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.RELOAD_FAILED));
                return false;
            }
            resumePersistence = false;
            boolean initialized;
            try {
                initialized = ConfigHandler.performInitialization(false);
            }
            catch (CancellationException e) {
                resumePersistence = resumePersistenceAfterCancellation;
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.RELOAD_FAILED));
                return false;
            }
            if (!initialized) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.RELOAD_FAILED));
                return false;
            }
            EntitySpawnTracking.invalidateDatabaseVerification();
            resumePersistence = true;
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.RELOAD_SUCCESS));

            Thread networkHandler = new Thread(new NetworkHandler(false, false));
            networkHandler.start();
            return true;
        }
        finally {
            Consumer.endDatabaseReload(resumePersistence);
        }
    }

    private static long remainingMillis(long deadline) {
        return Math.max(0L, (deadline - System.nanoTime()) / 1_000_000L);
    }
}
