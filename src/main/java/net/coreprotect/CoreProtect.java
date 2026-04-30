package net.coreprotect;

import java.io.File;

import net.coreprotect.database.RowNumbers;
import org.bukkit.plugin.java.JavaPlugin;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.services.PluginInitializationService;
import net.coreprotect.services.ShutdownService;
import net.coreprotect.utility.Chat;

/**
 * Main class for the CoreProtect plugin
 */
public final class CoreProtect extends JavaPlugin {

    private static CoreProtect instance;
    private boolean advancedChestsEnabled = false;
    private final RowNumbers rowNumbers = new RowNumbers(this);

    /**
     * Get the instance of CoreProtect
     *
     * @return This CoreProtect instance
     */
    public static CoreProtect getInstance() {
        return instance;
    }

    private final CoreProtectAPI api = new CoreProtectAPI();

    /**
     * Get the CoreProtect API
     *
     * @return The CoreProtect API
     */
    public CoreProtectAPI getAPI() {
        return api;
    }

    @Override
    public void onEnable() {
        // Set plugin instance and data folder path
        instance = this;
        ConfigHandler.path = this.getDataFolder().getPath() + File.separator;
        this.rowNumbers.initialize();

        advancedChestsEnabled = getServer().getPluginManager().getPlugin("AdvancedChests") != null;
        // Initialize plugin using the initialization service
        boolean initialized = PluginInitializationService.initializePlugin(this);

        // Disable plugin if initialization failed
        if (!initialized) {
            Chat.console(Phrase.build(Phrase.ENABLE_FAILED, ConfigHandler.EDITION_NAME));
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        this.rowNumbers.save();
        ShutdownService.safeShutdown(this);
    }

    public boolean isAdvancedChestsEnabled() {
        return advancedChestsEnabled;
    }

    public RowNumbers rowNumbers() {
        return this.rowNumbers;
    }
}
