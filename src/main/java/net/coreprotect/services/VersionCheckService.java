package net.coreprotect.services;

import org.bukkit.Bukkit;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.VersionUtils;

/**
 * Service responsible for checking compatibility of Minecraft, Java versions,
 * and plugin branch validation.
 */
public class VersionCheckService {

    private VersionCheckService() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Performs all necessary version checks during plugin startup
     *
     * @return true if all version checks pass, false otherwise
     */
    public static boolean performVersionChecks() {
        try {
            // Check Minecraft version compatibility
            String[] bukkitVersion = Bukkit.getServer().getBukkitVersion().split("[-.]");
            if (VersionUtils.newVersion(bukkitVersion[0] + "." + bukkitVersion[1], ConfigHandler.MINECRAFT_VERSION)) {
                Chat.console(Phrase.build(Phrase.VERSION_REQUIRED, "Minecraft", ConfigHandler.MINECRAFT_VERSION));
                return false;
            }

            if (VersionUtils.newVersion(ConfigHandler.LATEST_VERSION, bukkitVersion[0] + "." + bukkitVersion[1] + (bukkitVersion.length > 2 && bukkitVersion[2].matches("\\d+") ? "." + bukkitVersion[2] : "")) && VersionUtils.isCommunityEdition()) {
                Chat.console(Phrase.build(Phrase.VERSION_INCOMPATIBLE, "Minecraft", bukkitVersion[0] + "." + bukkitVersion[1] + (bukkitVersion.length > 2 ? "." + bukkitVersion[2] : "")));
                return false;
            }

            // Check Java version compatibility
            String[] javaVersion = (System.getProperty("java.version").replaceAll("[^0-9.]", "") + ".0").split("\\.");
            if (VersionUtils.newVersion(javaVersion[0] + "." + javaVersion[1], ConfigHandler.JAVA_VERSION)) {
                Chat.console(Phrase.build(Phrase.VERSION_REQUIRED, "Java", ConfigHandler.JAVA_VERSION));
                return false;
            }

            // Branch validation
            if (ConfigHandler.EDITION_BRANCH.length() == 0) {
                Chat.sendConsoleMessage(Color.RED + "[CoreProtect] " + Phrase.build(Phrase.INVALID_BRANCH_1));
                Chat.sendConsoleMessage(Color.GREY + "[CoreProtect] " + Phrase.build(Phrase.INVALID_BRANCH_2));
                Chat.sendConsoleMessage(Color.GREY + "[CoreProtect] " + Phrase.build(Phrase.INVALID_BRANCH_3));
                return false;
            }

            // Store Minecraft server version for later use
            ConfigHandler.SERVER_VERSION = Integer.parseInt(bukkitVersion[1]);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
