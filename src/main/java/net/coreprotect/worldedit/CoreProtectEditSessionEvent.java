package net.coreprotect.worldedit;

import org.bukkit.Bukkit;

import com.sk89q.worldedit.EditSession.Stage;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.util.eventbus.Subscribe;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;

public class CoreProtectEditSessionEvent {
    private static boolean initialized = false;
    private static boolean isFAWE = false;
    private static CoreProtectEditSessionEvent event = new CoreProtectEditSessionEvent();

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isFAWE() {
        return isFAWE;
    }

    public static void register() {
        if (isInitialized()) {
            return;
        }

        try {
            WorldEdit.getInstance().getEventBus().register(new Object() {
                @Subscribe
                public void onEditSessionEvent(EditSessionEvent event) {
                    if (event.getActor() != null && event.getStage() == Stage.BEFORE_CHANGE) {
                        event.setExtent(new CoreProtectLogger(event.getActor(), event.getWorld(), event.getExtent()));
                    }
                }
            });
            initialized = true;
            ConfigHandler.worldeditEnabled = true;
            isFAWE = (Bukkit.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null);
        }
        catch (Exception e) {
            // Failed to initialize WorldEdit logging
        }

        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            try {
                if (isInitialized()) {
                    Chat.console(Phrase.build(Phrase.INTEGRATION_SUCCESS, isFAWE() ? "FastAsyncWorldEdit" : "WorldEdit", Selector.FIRST));
                }
                else {
                    Chat.console(Phrase.build(Phrase.INTEGRATION_ERROR, isFAWE() ? "FastAsyncWorldEdit" : "WorldEdit", Selector.FIRST));
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void unregister() {
        if (!isInitialized()) {
            return;
        }

        try {
            WorldEdit.getInstance().getEventBus().unregister(event);
            initialized = false;
            ConfigHandler.worldeditEnabled = false;
            Chat.console(Phrase.build(Phrase.INTEGRATION_SUCCESS, isFAWE() ? "FastAsyncWorldEdit" : "WorldEdit", Selector.SECOND));
        }
        catch (Exception e) {
            Chat.console(Phrase.build(Phrase.INTEGRATION_ERROR, isFAWE() ? "FastAsyncWorldEdit" : "WorldEdit", Selector.SECOND));
        }
    }
}
