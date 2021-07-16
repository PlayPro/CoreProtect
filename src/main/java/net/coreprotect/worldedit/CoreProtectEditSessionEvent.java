package net.coreprotect.worldedit;

import org.bukkit.Bukkit;

import com.sk89q.worldedit.EditSession.Stage;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.World;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;

public class CoreProtectEditSessionEvent {

    private static boolean initialized = false;
    private static CoreProtectEditSessionEvent event = new CoreProtectEditSessionEvent();

    public static boolean isInitialized() {
        return initialized;
    }

    public static void register() {
        try {
            if (isInitialized()) {
                return;
            }

            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
                try {
                    WorldEdit.getInstance().getEventBus().register(event);
                    initialized = true;
                    ConfigHandler.worldeditEnabled = true;
                    Chat.console(Phrase.build(Phrase.INTEGRATION_SUCCESS, "WorldEdit", Selector.FIRST));
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.INTEGRATION_ERROR, "WorldEdit", Selector.FIRST));
                }
            }, 0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void unregister() {
        try {
            if (!isInitialized()) {
                return;
            }

            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
                try {
                    WorldEdit.getInstance().getEventBus().unregister(event);
                    initialized = false;
                    ConfigHandler.worldeditEnabled = false;
                    Chat.console(Phrase.build(Phrase.INTEGRATION_SUCCESS, "WorldEdit", Selector.SECOND));
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.INTEGRATION_ERROR, "WorldEdit", Selector.SECOND));
                }
            }, 0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    public void wrapForLogging(EditSessionEvent event) {
        Actor actor = event.getActor();
        World world = event.getWorld();
        if (actor != null && event.getStage() == Stage.BEFORE_CHANGE) {
            event.setExtent(new CoreProtectLogger(actor, world, event.getExtent()));
        }
    }
}
