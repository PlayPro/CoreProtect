package net.coreprotect.utility;

import java.io.InputStreamReader;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.thread.NetworkHandler;
import net.coreprotect.worldedit.CoreProtectEditSessionEvent;

public class VersionUtils {

    private VersionUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String getPluginVersion() {
        String version = CoreProtect.getInstance().getDescription().getVersion();
        if (version.contains("-")) {
            version = version.split("-")[0];
        }

        return version;
    }

    public static Integer[] getInternalPluginVersion() {
        int major = ConfigHandler.EDITION_VERSION;
        int minor = 0;
        int revision = 0;

        String pluginVersion = getPluginVersion();
        if (pluginVersion.contains(".")) {
            String[] versionSplit = pluginVersion.split("\\.");
            minor = Integer.parseInt(versionSplit[0]);
            revision = Integer.parseInt(versionSplit[1]);
        }
        else {
            minor = Integer.parseInt(pluginVersion);
        }

        return new Integer[] { major, minor, revision };
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

        if (branch.startsWith("-edge")) {
            name = name + " " + branch.substring(1, 2).toUpperCase() + branch.substring(2, 5);
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
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
        }
        catch (Exception e) {
            return false;
        }

        return true;
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

            InputStreamReader reader = new InputStreamReader(instance.getClass().getResourceAsStream("/plugin.yml"));
            branch = YamlConfiguration.loadConfiguration(reader).getString("branch");
            reader.close();

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
            e.printStackTrace();
        }

        return branch;
    }

    public static boolean newVersion(Integer[] oldVersion, Integer[] currentVersion) {
        if (oldVersion[0] < currentVersion[0]) {
            // Major version
            return true;
        }
        else if (oldVersion[0].equals(currentVersion[0]) && oldVersion[1] < currentVersion[1]) {
            // Minor version
            return true;
        }
        else if (oldVersion.length < 3 && currentVersion.length >= 3 && oldVersion[0].equals(currentVersion[0]) && oldVersion[1].equals(currentVersion[1]) && 0 < currentVersion[2]) {
            // Revision version (#.# vs #.#.#)
            return true;
        }
        else if (oldVersion.length >= 3 && currentVersion.length >= 3 && oldVersion[0].equals(currentVersion[0]) && oldVersion[1].equals(currentVersion[1]) && oldVersion[2] < currentVersion[2]) {
            // Revision version (#.#.# vs #.#.#)
            return true;
        }

        return false;
    }

    public static boolean newVersion(Integer[] oldVersion, String currentVersion) {
        String[] currentVersionSplit = currentVersion.split("\\.");
        return newVersion(oldVersion, StringUtils.convertArray(currentVersionSplit));
    }

    public static boolean newVersion(String oldVersion, Integer[] currentVersion) {
        String[] oldVersionSplit = oldVersion.split("\\.");
        return newVersion(StringUtils.convertArray(oldVersionSplit), currentVersion);
    }

    public static boolean newVersion(String oldVersion, String currentVersion) {
        if (!oldVersion.contains(".") || !currentVersion.contains(".")) {
            return false;
        }

        String[] oldVersionSplit = oldVersion.split("\\.");
        String[] currentVersionSplit = currentVersion.split("\\.");
        return newVersion(StringUtils.convertArray(oldVersionSplit), StringUtils.convertArray(currentVersionSplit));
    }

    public static void loadWorldEdit() {
        try {
            boolean validVersion = true;
            String version = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit").getDescription().getVersion();
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
            else if (version.equals("unspecified")) { // FAWE
                validVersion = false;
                Plugin fawe = Bukkit.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");
                if (fawe != null) {
                    String apiVersion = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit").getDescription().getAPIVersion();
                    String faweVersion = fawe.getDescription().getVersion();
                    double apiDouble = Double.parseDouble(apiVersion);
                    double faweDouble = Double.parseDouble(faweVersion);
                    if (apiDouble >= 1.13 && faweDouble >= 1.0) {
                        validVersion = true;
                    }
                }
            }
            else {
                validVersion = false;
            }

            if (validVersion) {
                CoreProtectEditSessionEvent.register();
            }
            else {
                Chat.console(Phrase.build(Phrase.INTEGRATION_VERSION, "WorldEdit"));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void unloadWorldEdit() {
        try {
            CoreProtectEditSessionEvent.unregister();
        }
        catch (Exception e) {
            e.printStackTrace();
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
