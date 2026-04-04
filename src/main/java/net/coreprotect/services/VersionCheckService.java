package net.coreprotect.services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern MINECRAFT_VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

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
            String bukkitVersion = Bukkit.getServer().getBukkitVersion();
            String minecraftVersion = parseMinecraftVersion(bukkitVersion);
            if (minecraftVersion == null || VersionUtils.newVersion(minecraftVersion, ConfigHandler.MINECRAFT_VERSION)) {
                Chat.console(Phrase.build(Phrase.VERSION_REQUIRED, "Minecraft", ConfigHandler.MINECRAFT_VERSION));
                return false;
            }

            if (VersionUtils.newVersion(ConfigHandler.LATEST_VERSION, minecraftVersion) && VersionUtils.isCommunityEdition()) {
                Chat.console(Phrase.build(Phrase.VERSION_INCOMPATIBLE, "Minecraft", minecraftVersion));
                return false;
            }

            String requiredJavaVersion = parseCompatibilityVersion(minecraftVersion) >= 26 ? ConfigHandler.JAVA_VERSION_26 : ConfigHandler.JAVA_VERSION;

            // Check Java version compatibility
            String[] javaVersion = (System.getProperty("java.version").replaceAll("[^0-9.]", "") + ".0").split("\\.");
            if (VersionUtils.newVersion(javaVersion[0] + "." + javaVersion[1], requiredJavaVersion)) {
                Chat.console(Phrase.build(Phrase.VERSION_REQUIRED, "Java", requiredJavaVersion));
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

            // Support both legacy 1.x versioning and the new year-based 26.x/26.1.x format.
            ConfigHandler.SERVER_VERSION = parseCompatibilityVersion(minecraftVersion);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private static String parseMinecraftVersion(String bukkitVersion) {
        Matcher matcher = MINECRAFT_VERSION_PATTERN.matcher(bukkitVersion);
        if (!matcher.find()) {
            return null;
        }

        StringBuilder version = new StringBuilder(matcher.group(1)).append('.').append(matcher.group(2));
        if (matcher.group(3) != null) {
            version.append('.').append(matcher.group(3));
        }

        return version.toString();
    }

    private static int parseCompatibilityVersion(String minecraftVersion) {
        String[] versionParts = minecraftVersion.split("\\.");
        if (versionParts.length < 2) {
            throw new IllegalArgumentException("Unsupported Minecraft version: " + minecraftVersion);
        }

        if ("1".equals(versionParts[0])) {
            return Integer.parseInt(versionParts[1]);
        }

        return Integer.parseInt(versionParts[0]);
    }
}
