package net.coreprotect.patch;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Database;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.VersionUtils;
import net.coreprotect.utility.ErrorReporter;

public class Patch {

    private static final Pattern STORED_VERSION = Pattern.compile("([0-9]+(?:[.,][0-9]+)*)(?:-[A-Za-z0-9][A-Za-z0-9._-]*)?(?:\\+[A-Za-z0-9][A-Za-z0-9._-]*)?");
    private static boolean patching = false;
    private static boolean patchNotification = false;
    private static Integer[] firstVersion = null;

    public static boolean continuePatch() {
        return patching && ConfigHandler.serverRunning;
    }

    public static String getFirstVersion() {
        String result = "";
        if (firstVersion != null && firstVersion.length >= 3) {
            if (firstVersion[0] == 0 && firstVersion[1] == 0 && firstVersion[2] == 0) {
                result = VersionUtils.getPluginVersion();
            }
            else {
                StringBuilder builder = new StringBuilder();
                for (int i = 1; i < firstVersion.length; i++) {
                    if (i > 1) {
                        builder.append('.');
                    }
                    builder.append(firstVersion[i] != null ? firstVersion[i] : 0);
                }
                result = builder.toString();
            }
        }
        return result;
    }

    protected static String getClassVersion(String version) {
        return (version.split(".__"))[1].replaceAll("_", ".");
    }

    public static Integer[] getDatabaseVersion(Connection connection, boolean lastVersion) throws SQLException {
        return getDatabaseVersion(connection, ConfigHandler.prefix, lastVersion);
    }

    public static Integer[] getDatabaseVersion(Connection connection, String tablePrefix, boolean lastVersion) throws SQLException {
        String query = "SELECT version FROM " + tablePrefix + "version ORDER BY rowid " + (lastVersion ? "DESC" : "ASC") + " LIMIT 1";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(query)) {
            return rows.next() ? parseDatabaseVersion(rows.getString("version")) : new Integer[] { 0, 0, 0 };
        }
    }

    private static Integer[] parseDatabaseVersion(String raw) throws SQLException {
        Matcher matcher = STORED_VERSION.matcher(raw == null ? "" : raw.trim());
        if (!matcher.matches()) {
            throw new SQLException("Invalid recorded database version");
        }
        String[] components = matcher.group(1).split("[.,]");
        Integer[] version = new Integer[Math.max(3, components.length)];
        try {
            if (components.length == 1) { // v200–205
                int compactVersion = Integer.parseInt(components[0]);
                version = new Integer[] { compactVersion / 100, compactVersion % 10, 0 };
            }
            else if (components.length == 2) { // v2.00–2.09
                String revision = components[1];
                version = new Integer[] { Integer.parseInt(components[0]), revision.length() > 1 ? Integer.parseInt(revision.substring(1)) : 0, 0 };
            }
            else {
                for (int index = 0; index < components.length; index++) {
                    version[index] = Integer.parseInt(components[index]);
                }
            }
        }
        catch (NumberFormatException exception) {
            throw new SQLException("Invalid recorded database version", exception);
        }
        if (version[0] == 0) {
            throw new SQLException("Invalid recorded database version");
        }
        return version;
    }

    private static List<String> getPatches() throws Exception {
        File pluginFile = new File(CoreProtect.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return getPatches(pluginFile);
    }

    static List<String> getPatches(File pluginFile) throws Exception {
        List<String> patches = new ArrayList<>();

        if (pluginFile.getPath().endsWith(".jar")) {
            try (JarInputStream jarInputStream = new JarInputStream(new FileInputStream(pluginFile))) {
                while (true) {
                    JarEntry jarEntry = jarInputStream.getNextJarEntry();
                    if (jarEntry == null) {
                        break;
                    }
                    String className = jarEntry.getName();
                    if (className.startsWith("net/coreprotect/patch/script/__") && className.endsWith(".class")) {
                        String patchVersion = getClassVersion(className.substring(0, className.length() - 6).replace('/', '.'));
                        if (!VersionUtils.newVersion(VersionUtils.getInternalPluginVersion(), patchVersion)) {
                            patches.add(patchVersion);
                        }
                    }
                }
            }
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

        return patches;
    }

    public static void processConsumer() {
        try {
            Chat.console(Phrase.build(Phrase.PATCH_PROCESSING));
            Consumer.setMaintenancePaused(false);
            Thread.sleep(1000);
            while (Consumer.isPaused) {
                Thread.sleep(500);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
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
                if ((ConfigHandler.databaseType.isDuckDB() || ConfigHandler.databaseType.isClickHouse())
                        && VersionUtils.newVersion(patchVersion, new Integer[] { 2, 24, 0 })) {
                    continue;
                }

                boolean performPatch = VersionUtils.newVersion(newVersion, patchVersion);
                if (performPatch) {
                    boolean success = false;
                    try {
                        Chat.console("-----");
                        Chat.console(Phrase.build(Phrase.PATCH_STARTED, "v" + patchMinor + "." + patchRevision));
                        Chat.console("-----");

                        if (continuePatch()) {
                            Class<?> patchClass = Class.forName("net.coreprotect.patch.script.__" + patchData.replaceAll("\\.", "_"));
                            Method patchMethod = getPatchMethod(patchClass);
                            patchMethod.setAccessible(true);
                            success = (Boolean) patchMethod.invoke(null, statement);
                        }
                    }
                    catch (Exception e) {
                        ErrorReporter.report(e);
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
            if (result >= 0) {
                Database.recordDatabaseVersion(statement, VersionUtils.formatInternalVersion(version));
            }
            else if (patched) {
                Database.recordDatabaseVersion(statement, VersionUtils.formatInternalVersion(newVersion));
            }

            statement.close();
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        patching = false;
        return result;
    }

    public static boolean versionCheck(Statement statement) {
        try {
            Integer[] currentVersion = VersionUtils.getInternalPluginVersion();
            Connection connection = statement.getConnection();
            Integer[] oldestVersion = getDatabaseVersion(connection, false);
            Integer[] lastVersion = getDatabaseVersion(connection, true);
            firstVersion = oldestVersion;

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
                Consumer.setMaintenancePaused(true);
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
                            ErrorReporter.report(e);
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
                                Consumer.setMaintenancePaused(false);
                            }
                            else if (finished == -1) {
                                processConsumer();
                                Chat.console(Phrase.build(Phrase.PATCH_INTERRUPTED));
                            }
                        }
                        catch (Exception e) {
                            ErrorReporter.report(e);
                        }
                    }
                }
                (new Thread(new runPatch())).start();
                (new Thread(new patchStatus())).start();
            }
            else if (lastVersion[0] == 0) {
                Integer[] initialVersion = currentVersion.clone();
                if (ConfigHandler.EDITION_BRANCH.contains("-dev") && initialVersion.length > 1) {
                    initialVersion[1] = initialVersion[1] - 1;
                }
                Database.recordDatabaseVersion(statement, VersionUtils.formatInternalVersion(initialVersion));
            }
            else {
                Integer[] comparableCurrent = new Integer[] { currentVersion[0], currentVersion[1], 0 };
                Integer[] comparableLast = new Integer[] { lastVersion[0], lastVersion[1], 0 };
                if (VersionUtils.newVersion(comparableCurrent, comparableLast)) {
                    Chat.sendConsoleMessage(Color.RED + "[CoreProtect] " + Phrase.build(Phrase.VERSION_REQUIRED, "CoreProtect", "v" + lastVersion[1] + "." + lastVersion[2]));
                    return false;
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }

        return true;
    }

    private static Method getPatchMethod(Class<?> patchClass) throws NoSuchMethodException {
        String methodName = ConfigHandler.databaseType.isClickHouse() ? "patchClickHouse"
                : ConfigHandler.databaseType.isDuckDB() ? "patchDuckDB" : "patch";
        return patchClass.getDeclaredMethod(methodName, Statement.class);
    }
}
