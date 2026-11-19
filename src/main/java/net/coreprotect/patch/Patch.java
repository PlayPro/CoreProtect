package net.coreprotect.patch;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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

    private static final String PATCH_PATH = "net/coreprotect/patch/script/";
    private static final Pattern PATCH_FILENAME = Pattern.compile("__([0-9]+_[0-9]+_[0-9]+)\\.class");
    private static final Pattern STORED_VERSION = Pattern.compile("([0-9]+(?:[.,][0-9]+)*)(?:-[A-Za-z0-9][A-Za-z0-9._-]*)?(?:\\+[A-Za-z0-9][A-Za-z0-9._-]*)?");
    private static final long CONNECTION_DRAIN_TIMEOUT_MILLIS = 10_000L;
    private static volatile boolean patching = false;
    private static volatile boolean patchNotification = false;
    private static volatile Integer[] firstVersion = null;

    public static boolean continuePatch() {
        return patching && ConfigHandler.serverRunning && !Thread.currentThread().isInterrupted();
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

    static List<String> getPatches(File pluginFile) throws IOException {
        List<String> patches = new ArrayList<>();

        if (pluginFile.isDirectory()) {
            try (var files = Files.list(pluginFile.toPath().resolve(PATCH_PATH))) {
                files.filter(Files::isRegularFile).forEach(file -> addPatch(patches, file.getFileName().toString()));
            }
        }
        else {
            try (JarFile jar = new JarFile(pluginFile)) {
                jar.stream().map(JarEntry::getName).filter(name -> name.startsWith(PATCH_PATH))
                        .forEach(name -> addPatch(patches, name.substring(PATCH_PATH.length())));
            }
        }
        if (patches.isEmpty()) {
            throw new IOException("No bundled CoreProtect database patches found");
        }

        Integer[] currentVersion = VersionUtils.getInternalPluginVersion();
        patches.removeIf(patch -> VersionUtils.newVersion(currentVersion, patch));
        if (patches.isEmpty()) {
            throw new IOException("No bundled CoreProtect database patches are compatible with this build");
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

    private static void addPatch(List<String> patches, String filename) {
        if (filename.startsWith("__") && filename.endsWith(".class") && !filename.contains("$")) {
            Matcher matcher = PATCH_FILENAME.matcher(filename);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid bundled database patch: " + filename);
            }
            patches.add(matcher.group(1).replace('_', '.'));
        }
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
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static int runPatcher(Integer[] lastVersion, Integer[] version) {
        patching = true;
        try {
            Consumer.lockDatabaseMaintenanceInterruptibly();
            try {
                if (!continuePatch() || !Database.awaitConnectionDrain(CONNECTION_DRAIN_TIMEOUT_MILLIS)) {
                    return -1;
                }
                try (Connection connection = Database.getConnection(true, 0)) {
                    if (connection == null) {
                        return -1;
                    }
                    try (Statement statement = connection.createStatement()) {
                        return runPatches(statement, lastVersion, version);
                    }
                }
            }
            finally {
                Consumer.unlockDatabaseMaintenance();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return -1;
        }
        finally {
            patching = false;
        }
    }

    private static int runPatches(Statement statement, Integer[] lastVersion, Integer[] version) throws Exception {
        boolean patched = false;
        Integer[] newVersion = lastVersion.clone();

        // Fix for versions 2.0.0 through 2.0.9
        if (newVersion[1] == 0 && newVersion[2] > 0) {
            newVersion[1] = newVersion[2];
            newVersion[2] = 0;
        }

        for (String patchData : getPatches()) {
            String[] thePatch = patchData.split("\\.");
            int patchMajor = Integer.parseInt(thePatch[0]);
            int patchMinor = Integer.parseInt(thePatch[1]);
            int patchRevision = Integer.parseInt(thePatch[2]);
            Integer[] patchVersion = new Integer[] { patchMajor, patchMinor, patchRevision };
            if ((ConfigHandler.databaseType.isDuckDB() || ConfigHandler.databaseType.isClickHouse())
                    && VersionUtils.newVersion(patchVersion, new Integer[] { 2, 24, 0 })) {
                continue;
            }

            if (!VersionUtils.newVersion(newVersion, patchVersion)) {
                continue;
            }
            if (!continuePatch()) {
                return -1;
            }

            boolean success = false;
            try {
                Chat.console("-----");
                Chat.console(Phrase.build(Phrase.PATCH_STARTED, "v" + patchMinor + "." + patchRevision));
                Chat.console("-----");
                Class<?> patchClass = Class.forName("net.coreprotect.patch.script.__" + patchData.replace('.', '_'));
                Method patchMethod = getPatchMethod(patchClass);
                patchMethod.setAccessible(true);
                success = (Boolean) patchMethod.invoke(null, statement);
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }

            if (!continuePatch()) {
                return -1;
            }
            if (!success) {
                if (patched) { // actually performed a patch
                    Database.recordDatabaseVersion(statement, VersionUtils.formatInternalVersion(newVersion));
                }
                return -1;
            }
            patched = true;
            newVersion = patchVersion;
        }

        if (!continuePatch()) {
            return -1;
        }
        Database.recordDatabaseVersion(statement, VersionUtils.formatInternalVersion(version));
        return patched ? 1 : 0;
    }

    public static synchronized boolean versionCheck(boolean startup) {
        if (ConfigHandler.converterRunning) {
            return false;
        }
        try {
            Integer[] currentVersion = VersionUtils.getInternalPluginVersion();
            Integer[] lastVersion;
            try (Connection connection = Database.getConnection(true, 0)) {
                if (connection == null) {
                    return false;
                }
                Integer[] oldestVersion = getDatabaseVersion(connection, false);
                lastVersion = getDatabaseVersion(connection, true);
                firstVersion = oldestVersion;
                if (lastVersion[0] == 0) {
                    Integer[] initialVersion = currentVersion.clone();
                    if (ConfigHandler.EDITION_BRANCH.contains("-dev") && initialVersion.length > 1) {
                        initialVersion[1] = initialVersion[1] - 1;
                    }
                    try (Statement statement = connection.createStatement()) {
                        Database.recordDatabaseVersion(statement, VersionUtils.formatInternalVersion(initialVersion));
                    }
                    return true;
                }
            }

            if (VersionUtils.newVersion(lastVersion, currentVersion)) {
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

                return startPatch(lastVersion, currentVersion, startup);
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

    private static boolean startPatch(Integer[] lastVersion, Integer[] currentVersion, boolean startup) {
        ConfigHandler.converterRunning = true;
        boolean handedOff = false;
        try {
            Consumer.setMaintenancePaused(true);
            if (!startup) {
                handedOff = true;
                return completePatch(lastVersion, currentVersion, false);
            }
            new Thread(() -> completePatch(lastVersion, currentVersion, true), "CoreProtect-Patch").start();
            handedOff = true;
            return true;
        }
        finally {
            if (!handedOff) {
                finishPatch(false);
            }
        }
    }

    private static boolean completePatch(Integer[] lastVersion, Integer[] currentVersion, boolean startup) {
        int completed = -1;
        Thread statusThread = null;
        try {
            statusThread = new Thread(Patch::reportProgress, "CoreProtect-PatchStatus");
            statusThread.setDaemon(true);
            statusThread.start();
            completed = runPatcher(lastVersion, currentVersion);
            if (completed < 0) {
                Chat.console(Phrase.build(Phrase.PATCH_INTERRUPTED));
                return false;
            }
            if (completed > 0) {
                if (startup) {
                    processConsumer();
                }
                Chat.console("-----");
                Chat.console(Phrase.build(Phrase.PATCH_SUCCESS, "v" + VersionUtils.getPluginVersion()));
                Chat.console("-----");
            }
            return true;
        }
        finally {
            try {
                if (statusThread != null) {
                    statusThread.interrupt();
                }
            }
            finally {
                finishPatch(completed >= 0);
            }
        }
    }

    private static void finishPatch(boolean succeeded) {
        try {
            if (!succeeded) {
                Consumer.requireDatabaseReload();
            }
            Consumer.setMaintenancePaused(false);
        }
        finally {
            ConfigHandler.converterRunning = false;
        }
    }

    private static void reportProgress() {
        try {
            long nextAlert = System.nanoTime() + TimeUnit.SECONDS.toNanos(patchNotification ? 30L : 10L);
            while (!Thread.currentThread().isInterrupted()) {
                if (System.nanoTime() >= nextAlert) {
                    Chat.console(Phrase.build(Phrase.PATCH_UPGRADING));
                    patchNotification = true;
                    nextAlert = System.nanoTime() + TimeUnit.SECONDS.toNanos(30L);
                }
                Thread.sleep(1000L);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Method getPatchMethod(Class<?> patchClass) throws NoSuchMethodException {
        String methodName = ConfigHandler.databaseType.isClickHouse() ? "patchClickHouse"
                : ConfigHandler.databaseType.isDuckDB() ? "patchDuckDB" : "patch";
        return patchClass.getDeclaredMethod(methodName, Statement.class);
    }
}
