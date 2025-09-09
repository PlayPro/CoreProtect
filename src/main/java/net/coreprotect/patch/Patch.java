package net.coreprotect.patch;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Database;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.VersionUtils;

public class Patch {

    private static boolean patching = false;
    private static boolean patchNotification = false;
    private static Integer[] firstVersion = null;

    public static boolean continuePatch() {
        return patching && ConfigHandler.serverRunning;
    }

    public static String getFirstVersion() {
        String result = "";
        if (firstVersion != null) {
            if ((firstVersion[0] + "." + firstVersion[1] + "." + firstVersion[2]).equals("0.0.0")) {
                result = VersionUtils.getPluginVersion();
            }
            else {
                result = firstVersion[1] + "." + firstVersion[2];
            }
        }
        return result;
    }

    protected static String getClassVersion(String version) {
        return (version.split(".__"))[1].replaceAll("_", ".");
    }

    public static Integer[] getDatabaseVersion(Connection connection, boolean lastVersion) {
        Integer[] last_version = new Integer[] { 0, 0, 0 };
        try {
            String query = "SELECT version FROM " + ConfigHandler.prefix + "version ORDER BY rowid " + (lastVersion ? "DESC" : "ASC") + " LIMIT 0, 1";
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(query);
            while (rs.next()) {
                String version = rs.getString("version");
                if (!version.contains(".")) { // v200-205
                    int version_int = Integer.parseInt(version);
                    version = String.format(Locale.ROOT, "%3.2f", version_int / 100.0);
                }
                version = version.replaceAll(",", ".");
                String[] old_version_split = version.split("\\.");
                if (old_version_split.length > 2) { // #.#.#
                    last_version[0] = Integer.parseInt(old_version_split[0]);
                    last_version[1] = Integer.parseInt(old_version_split[1]);
                    last_version[2] = Integer.parseInt(old_version_split[2]);
                }
                else { // #.#
                    int revision = 0;
                    String parse = old_version_split[1];
                    if (parse.length() > 1) {
                        revision = Integer.parseInt(parse.substring(1));
                    }
                    last_version[0] = Integer.parseInt(old_version_split[0]);
                    last_version[1] = revision;
                    last_version[2] = 0;
                }
            }
            rs.close();
            statement.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return last_version;
    }

    private static List<String> getPatches() {
        List<String> patches = new ArrayList<>();

        try {
            File pluginFile = new File(CoreProtect.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (pluginFile.getPath().endsWith(".jar")) {
                JarInputStream jarInputStream = new JarInputStream(new FileInputStream(pluginFile));
                while (true) {
                    JarEntry jarEntry = jarInputStream.getNextJarEntry();
                    if (jarEntry == null) {
                        break;
                    }
                    String className = jarEntry.getName();
                    if (className.startsWith("net/coreprotect/patch/script/__") && className.endsWith(".class")) {
                        Class<?> patchClass = Class.forName(className.substring(0, className.length() - 6).replaceAll("/", "."));
                        String patchVersion = getClassVersion(patchClass.getName());
                        if (!VersionUtils.newVersion(VersionUtils.getInternalPluginVersion(), patchVersion)) {
                            patches.add(patchVersion);
                        }
                    }
                }
                jarInputStream.close();
            }

            Collections.sort(patches, (o1, o2) -> {
                if (VersionUtils.newVersion(o1, o2)) {
                    return -1;
                }
                else if (VersionUtils.newVersion(o2, o1)) {
                    return 1;
                }
                return 0;
            });
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return patches;
    }

    public static void processConsumer() {
        try {
            Chat.console(Phrase.build(Phrase.PATCH_PROCESSING));
            boolean isRunning = ConfigHandler.serverRunning;
            ConfigHandler.serverRunning = true;
            Consumer.isPaused = false;
            Thread.sleep(1000);
            while (Consumer.isPaused) {
                Thread.sleep(500);
            }
            ConfigHandler.serverRunning = isRunning;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int runPatcher(Integer[] lastVersion, Integer[] version) {
        int result = -1;
        patching = true;

        try (Connection connection = Database.getConnection(true, 0)) {
            boolean patched = false;
            boolean allPatches = true;
            Statement statement = connection.createStatement();
            Integer[] newVersion = lastVersion;

            // Fix for versions 2.0.0 through 2.0.9
            if (newVersion[1] == 0 && newVersion[2] > 0) {
                newVersion[1] = newVersion[2];
                newVersion[2] = 0;
            }

            List<String> patches = getPatches();
            for (String patchData : patches) {
                String[] thePatch = patchData.split("\\.");
                int patchMajor = Integer.parseInt(thePatch[0]);
                int patchMinor = Integer.parseInt(thePatch[1]);
                int patchRevision = Integer.parseInt(thePatch[2]);
                Integer[] patchVersion = new Integer[] { patchMajor, patchMinor, patchRevision };

                boolean performPatch = VersionUtils.newVersion(newVersion, patchVersion);
                if (performPatch) {
                    boolean success = false;
                    try {
                        Chat.console("-----");
                        Chat.console(Phrase.build(Phrase.PATCH_STARTED, "v" + patchMinor + "." + patchRevision));
                        Chat.console("-----");

                        if (continuePatch()) {
                            Class<?> patchClass = Class.forName("net.coreprotect.patch.script.__" + patchData.replaceAll("\\.", "_"));
                            Method patchMethod = patchClass.getDeclaredMethod("patch", Statement.class);
                            patchMethod.setAccessible(true);
                            success = (Boolean) patchMethod.invoke(null, statement);
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (success) {
                        patched = true;
                        newVersion = patchVersion;
                    }
                    else {
                        allPatches = false;
                        break;
                    }
                }
            }

            if (allPatches) { // all patches completed
                if (patched) { // actually performed a patch
                    result = 1;
                }
                else { // no patches necessary
                    result = 0;
                }
            }

            // mark as being up to date
            int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
            if (result >= 0) {
                statement.executeUpdate("INSERT INTO " + ConfigHandler.prefix + "version (time,version) VALUES ('" + unixtimestamp + "', '" + version[0] + "." + version[1] + "." + version[2] + "')");
            }
            else if (patched) {
                statement.executeUpdate("INSERT INTO " + ConfigHandler.prefix + "version (time,version) VALUES ('" + unixtimestamp + "', '" + newVersion[0] + "." + newVersion[1] + "." + newVersion[2] + "')");
            }

            statement.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        patching = false;
        return result;
    }

    public static boolean versionCheck(Statement statement) {
        try {
            Integer[] currentVersion = VersionUtils.getInternalPluginVersion();
            firstVersion = getDatabaseVersion(statement.getConnection(), false);
            Integer[] lastVersion = getDatabaseVersion(statement.getConnection(), true);

            boolean newVersion = VersionUtils.newVersion(lastVersion, currentVersion);
            if (newVersion && lastVersion[0] > 0 && !ConfigHandler.converterRunning) {
                Integer[] minimumVersion = new Integer[] { 2, 0, 0 };
                if (VersionUtils.newVersion(lastVersion, minimumVersion)) {
                    Chat.sendConsoleMessage("§c[CoreProtect] " + Phrase.build(Phrase.PATCH_OUTDATED_1, "v" + minimumVersion[0] + "." + minimumVersion[1] + "." + minimumVersion[2]));
                    Chat.sendConsoleMessage("§c[CoreProtect] " + Phrase.build(Phrase.PATCH_OUTDATED_2));
                    return false;
                }

                if (ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                    Chat.sendConsoleMessage("§e[CoreProtect] " + Phrase.build(Phrase.DEVELOPMENT_BRANCH));
                    return true;
                }

                ConfigHandler.converterRunning = true;
                Consumer.isPaused = true;
                final Integer[] oldVersion = lastVersion;
                final Integer[] newVersionFinal = currentVersion;
                class patchStatus implements Runnable {
                    @Override
                    public void run() {
                        try {
                            int time_start = (int) (System.currentTimeMillis() / 1000L);
                            int alertTime = time_start + 10;
                            if (patchNotification) {
                                alertTime = alertTime + 20;
                            }
                            while (ConfigHandler.converterRunning) {
                                int time = (int) (System.currentTimeMillis() / 1000L);
                                if (time >= alertTime) {
                                    Chat.console(Phrase.build(Phrase.PATCH_UPGRADING));
                                    alertTime = alertTime + 30;
                                    patchNotification = true;
                                }
                                Thread.sleep(1000);
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                class runPatch implements Runnable {
                    @Override
                    public void run() {
                        try {
                            int finished = runPatcher(oldVersion, newVersionFinal);
                            ConfigHandler.converterRunning = false;
                            if (finished == 1) {
                                processConsumer();
                                Chat.console("-----");
                                Chat.console(Phrase.build(Phrase.PATCH_SUCCESS, "v" + CoreProtect.getInstance().getDescription().getVersion()));
                                Chat.console("-----");
                            }
                            else if (finished == 0) {
                                Consumer.isPaused = false;
                            }
                            else if (finished == -1) {
                                processConsumer();
                                Chat.console(Phrase.build(Phrase.PATCH_INTERRUPTED));
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                (new Thread(new runPatch())).start();
                (new Thread(new patchStatus())).start();
            }
            else if (lastVersion[0] == 0) {
                int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                statement.executeUpdate("INSERT INTO " + ConfigHandler.prefix + "version (time,version) VALUES ('" + unixtimestamp + "', '" + currentVersion[0] + "." + (ConfigHandler.EDITION_BRANCH.contains("-dev") ? (currentVersion[1] - 1) : currentVersion[1]) + "." + currentVersion[2] + "')");
            }
            else {
                currentVersion[2] = 0;
                lastVersion[2] = 0;
                if (VersionUtils.newVersion(currentVersion, lastVersion)) {
                    Chat.sendConsoleMessage(Color.RED + "[CoreProtect] " + Phrase.build(Phrase.VERSION_REQUIRED, "CoreProtect", "v" + lastVersion[1] + "." + lastVersion[2]));
                    return false;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }
}
