package net.coreprotect.utility;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.thread.NetworkHandler;
import net.coreprotect.worldedit.CoreProtectEditSessionEvent;

public class VersionUtils {

    private VersionUtils() {
        throw new IllegalStateException("Utility class");
    }

    private static String stripPreReleaseAndBuild(String version) {
        if (version == null) {
            return "";
        }
        String trimmed = version.trim();
        int dashIndex = trimmed.indexOf('-');
        int plusIndex = trimmed.indexOf('+');
        int cutIndex = -1;
        if (dashIndex >= 0 && plusIndex >= 0) {
            cutIndex = Math.min(dashIndex, plusIndex);
        }
        else if (dashIndex >= 0) {
            cutIndex = dashIndex;
        }
        else if (plusIndex >= 0) {
            cutIndex = plusIndex;
        }
        return (cutIndex >= 0 ? trimmed.substring(0, cutIndex) : trimmed).trim();
    }

    private static Integer[] parseVersionSegments(String version) {
        String clean = stripPreReleaseAndBuild(version);
        if (clean.isEmpty()) {
            return null;
        }
        String[] parts = clean.split("\\.");
        for (String part : parts) {
            if (part.isEmpty() || part.charAt(0) < '0' || part.charAt(0) > '9') {
                return null;
            }
        }
        return StringUtils.convertArray(parts);
    }

    public static String getPluginVersion() {
        CoreProtect instance = CoreProtect.getInstance();
        if (instance == null || instance.getDescription() == null) {
            return "";
        }
        return stripPreReleaseAndBuild(instance.getDescription().getVersion());
    }

    public static Integer[] getInternalPluginVersion() {
        String pluginVersion = getPluginVersion();
        Integer[] parsed = parseVersionSegments(pluginVersion);
        if (parsed == null || parsed.length == 0) {
            return new Integer[] { ConfigHandler.EDITION_VERSION, 0, 0 };
        }
        Integer[] result = new Integer[Math.max(3, parsed.length + 1)];
        result[0] = ConfigHandler.EDITION_VERSION;
        for (int i = 1; i < result.length; i++) {
            result[i] = (i - 1 < parsed.length) ? parsed[i - 1] : 0;
        }
        return result;
    }

    public static String formatInternalVersion(Integer[] version) {
        if (version == null || version.length == 0) {
            return "0.0.0";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < version.length; i++) {
            if (i > 0) {
                builder.append('.');
            }
            builder.append(version[i] != null ? version[i].intValue() : 0);
        }
        return builder.toString();
    }

    public static String getPluginName() {
        CoreProtect instance = CoreProtect.getInstance();
        // Return default name if instance is null
        if (instance == null) {
            return "CoreProtect";
        }

        // Return default name if description is null
        if (instance.getDescription() == null) {
            return "CoreProtect";
        }

        String name = instance.getDescription().getName();
        String branch = ConfigHandler.EDITION_BRANCH;

        if (branch.startsWith("-edge") && branch.length() >= 5) {
            name = name + " " + branch.substring(1, 2).toUpperCase(Locale.ROOT) + branch.substring(2, 5);
        }
        else if (isCommunityEdition()) {
            name = name + " " + ConfigHandler.COMMUNITY_EDITION;
        }

        return name;
    }

    public static boolean isSpigot() {
        try {
            Class.forName("org.spigotmc.SpigotConfig");
        }
        catch (Exception e) {
            return false;
        }

        return true;
    }

    public static boolean isPaper() {
        return classExists("com.destroystokyo.paper.PaperConfig")
                || classExists("io.papermc.paper.configuration.ServerConfiguration");
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
        }
        catch (Exception e) {
            return false;
        }

        return true;
    }

    public static boolean isCommunityEdition() {
        return !isBranch("edge") && !isBranch("coreprotect") && !validDonationKey();
    }

    public static boolean isBranch(String branch) {
        return ConfigHandler.EDITION_BRANCH.contains("-" + branch);
    }

    public static boolean validDonationKey() {
        return NetworkHandler.donationKey() != null;
    }

    public static String getBranch() {
        String branch = "";
        try {
            CoreProtect instance = CoreProtect.getInstance();
            if (instance == null) {
                return "";
            }

            try (InputStream stream = instance.getClass().getResourceAsStream("/plugin.yml")) {
                if (stream == null) {
                    return "";
                }
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    branch = YamlConfiguration.loadConfiguration(reader).getString("branch");
                }
            }

            if (branch == null || branch.equals("${project.branch}")) {
                branch = "";
            }
            if (branch.startsWith("-")) {
                branch = branch.substring(1);
            }
            if (branch.length() > 0) {
                branch = "-" + branch;
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return branch;
    }

    public static boolean newVersion(Integer[] oldVersion, Integer[] currentVersion) {
        if (oldVersion == null || currentVersion == null || oldVersion.length == 0 || currentVersion.length == 0) {
            return false;
        }
        int maxLength = Math.max(oldVersion.length, currentVersion.length);
        for (int i = 0; i < maxLength; i++) {
            int oldSegment = (i < oldVersion.length && oldVersion[i] != null) ? oldVersion[i] : 0;
            int currentSegment = (i < currentVersion.length && currentVersion[i] != null) ? currentVersion[i] : 0;
            if (oldSegment < currentSegment) {
                return true;
            }
            if (oldSegment > currentSegment) {
                return false;
            }
        }
        return false;
    }

    public static boolean newVersion(Integer[] oldVersion, String currentVersion) {
        Integer[] parsed = parseVersionSegments(currentVersion);
        return parsed != null && newVersion(oldVersion, parsed);
    }

    public static boolean newVersion(String oldVersion, Integer[] currentVersion) {
        Integer[] parsed = parseVersionSegments(oldVersion);
        return parsed != null && newVersion(parsed, currentVersion);
    }

    public static boolean newVersion(String oldVersion, String currentVersion) {
        Integer[] oldParsed = parseVersionSegments(oldVersion);
        Integer[] currentParsed = parseVersionSegments(currentVersion);
        return oldParsed != null && currentParsed != null && newVersion(oldParsed, currentParsed);
    }

    public static void loadWorldEdit() {
        try {
            Plugin worldEdit = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
            if (worldEdit == null) {
                return;
            }
            PluginDescriptionFile description = worldEdit.getDescription();
            if (description == null) {
                return;
            }
            String version = description.getVersion();
            if (version == null) {
                return;
            }
            boolean validVersion;
            if (isFastAsyncWorldEdit(worldEdit)) {
                validVersion = validFastAsyncWorldEditVersion(description.getAPIVersion(), version);
            }
            else if (version.equals("unspecified")) { // Legacy FAWE
                Plugin fawe = Bukkit.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");
                PluginDescriptionFile faweDescription = fawe != null ? fawe.getDescription() : null;
                validVersion = faweDescription != null && faweDescription.getVersion() != null
                        && validFastAsyncWorldEditVersion(description.getAPIVersion(), faweDescription.getVersion());
            }
            else {
                validVersion = validWorldEditVersion(version);
            }

            if (validVersion) {
                CoreProtectEditSessionEvent.register();
            }
            else {
                Chat.console(Phrase.build(Phrase.INTEGRATION_VERSION, "WorldEdit"));
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static boolean validWorldEditVersion(String version) {
        boolean validVersion = true;
        try {
            if (version.contains(";") || version.contains("+")) {
                if (version.contains("-beta-")) {
                    version = version.split(";")[0];
                    version = version.split("-beta-")[1];
                    long value = Long.parseLong(version.replaceAll("[^0-9]", ""));
                    if (value < 6) {
                        validVersion = false;
                    }
                }
                else {
                    if (version.contains("+")) {
                        version = version.split("\\+")[1];
                    }
                    else {
                        version = version.split(";")[1];
                    }

                    if (version.contains("-")) {
                        long value = Long.parseLong(((version.split("-"))[0]).replaceAll("[^0-9]", ""));
                        if (value > 0 && value < 4268) {
                            validVersion = false;
                        }
                    }
                }
            }
            else if (version.contains(".")) {
                String[] worldEditVersion = version.split("-|\\.");
                if (worldEditVersion.length >= 2) {
                    worldEditVersion[0] = worldEditVersion[0].replaceAll("[^0-9]", "");
                    worldEditVersion[1] = worldEditVersion[1].replaceAll("[^0-9]", "");
                    if (worldEditVersion[0].length() == 0 || worldEditVersion[1].length() == 0 || newVersion(worldEditVersion[0] + "." + worldEditVersion[1], "7.1")) {
                        validVersion = false;
                    }
                }
            }
            else {
                validVersion = false;
            }
        }
        catch (Exception e) {
            validVersion = false;
        }

        return validVersion;
    }

    private static boolean isFastAsyncWorldEdit(Plugin plugin) {
        return plugin != null && plugin.getDescription() != null && "FastAsyncWorldEdit".equals(plugin.getDescription().getName());
    }

    private static boolean validFastAsyncWorldEditVersion(String apiVersion, String pluginVersion) {
        String[] versions = { apiVersion, pluginVersion };
        String[] minimumVersions = { "1.13", "1.0" };
        for (int i = 0; i < versions.length; i++) {
            if (versions[i] == null) {
                return false;
            }

            String major = null;
            boolean validVersion = false;
            for (String part : versions[i].split("[^0-9]+")) {
                if (part.length() == 0) {
                    continue;
                }

                if (major == null) {
                    major = part;
                }
                else {
                    validVersion = !newVersion(major + "." + part, minimumVersions[i]);
                    break;
                }
            }

            if (!validVersion) {
                return false;
            }
        }

        return true;
    }

    public static void unloadWorldEdit() {
        try {
            CoreProtectEditSessionEvent.unregister();
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static boolean checkWorldEdit() {
        boolean result = false;
        for (org.bukkit.World world : Bukkit.getServer().getWorlds()) {
            if (net.coreprotect.config.Config.getConfig(world).WORLDEDIT) {
                result = true;
                break;
            }
        }

        return result;
    }
}
