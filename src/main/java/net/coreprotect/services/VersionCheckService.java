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
            String numericVersion = Bukkit.getServer().getBukkitVersion().split("-", 2)[0];
            String[] bukkitVersion = numericVersion.split("\\.");
            if (bukkitVersion.length < 2 || !bukkitVersion[0].matches("\\d+") || !bukkitVersion[1].matches("\\d+")) {
                Chat.console(Phrase.build(Phrase.VERSION_INCOMPATIBLE, "Minecraft", numericVersion));
                return false;
            }

            String minimumVersion = bukkitVersion[0] + "." + bukkitVersion[1];
            String currentVersion = minimumVersion + (bukkitVersion.length > 2 && bukkitVersion[2].matches("\\d+") ? "." + bukkitVersion[2] : "");

            if (VersionUtils.newVersion(minimumVersion, ConfigHandler.MINECRAFT_VERSION)) {
                Chat.console(Phrase.build(Phrase.VERSION_REQUIRED, "Minecraft", ConfigHandler.MINECRAFT_VERSION));
                return false;
            }

            if (VersionUtils.newVersion(ConfigHandler.LATEST_VERSION, currentVersion) && VersionUtils.isCommunityEdition()) {
                Chat.console(Phrase.build(Phrase.VERSION_INCOMPATIBLE, "Minecraft", currentVersion));
                return false;
            }

            // Check Java version compatibility
            String[] javaVersion = (System.getProperty("java.version").replaceAll("[^0-9.]", "") + ".0").split("\\.");
            if (VersionUtils.newVersion(javaVersion[0] + "." + javaVersion[1], ConfigHandler.JAVA_VERSION)) {
                Chat.console(Phrase.build(Phrase.VERSION_REQUIRED, "Java", ConfigHandler.JAVA_VERSION));
                return false;
            }

            // Patch version validation
            if (VersionUtils.newVersion(ConfigHandler.PATCH_VERSION, VersionUtils.getPluginVersion()) && !VersionUtils.isBranch("dev")) {
                Chat.console(Phrase.build(Phrase.VERSION_INCOMPATIBLE, "CoreProtect", "v" + VersionUtils.getPluginVersion()));
                Chat.sendConsoleMessage(Color.GREY + "[CoreProtect] " + Phrase.build(Phrase.INVALID_BRANCH_2));
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
            int major = Integer.parseInt(bukkitVersion[0]);
            int minor = Integer.parseInt(bukkitVersion[1]);
            ConfigHandler.SERVER_VERSION = (major == 1 ? minor : major);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
