package net.coreprotect.thread;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigFile;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.config.DatabaseConfigWriter;
import net.coreprotect.language.Language;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.VersionUtils;
import net.coreprotect.utility.ErrorReporter;

public class NetworkHandler extends Language implements Runnable {

    private static final long LOCAL_IO_GRACE_MILLIS = 250L;
    private static final Object LOCK = new Object();
    private static final Map<Thread, NetworkHandler> ACTIVE_HANDLERS = new IdentityHashMap<>();

    private boolean startup = true;
    private boolean background = false;
    private boolean translate = true;
    private volatile boolean cancelled = false;
    private volatile boolean sleeping = false;
    private static String latestVersion = null;
    private static String latestEdgeVersion = null;
    private static String donationKey = null;

    public NetworkHandler(boolean startup, boolean background) {
        this.startup = startup;
        this.background = background;
    }

    public static Thread startThread(NetworkHandler handler) {
        if (handler == null) {
            return null;
        }
        Thread thread = new Thread(() -> {
            try {
                handler.run();
            }
            finally {
                synchronized (LOCK) {
                    ACTIVE_HANDLERS.remove(Thread.currentThread());
                }
            }
        }, "CoreProtect-Network");
        thread.setDaemon(true);
        synchronized (LOCK) {
            if (handler.cancelled) {
                return null;
            }
            ACTIVE_HANDLERS.put(thread, handler);
            thread.start();
        }
        return thread;
    }

    public static void stopThreads(long timeoutMillis) {
        List<Map.Entry<Thread, NetworkHandler>> snapshot;
        synchronized (LOCK) {
            if (ACTIVE_HANDLERS.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(ACTIVE_HANDLERS.entrySet());
        }

        for (Map.Entry<Thread, NetworkHandler> entry : snapshot) {
            entry.getValue().cancelled = true;
            if (entry.getValue().sleeping) {
                entry.getKey().interrupt();
            }
        }

        long boundedTimeout = Math.max(0L, timeoutMillis);
        if (boundedTimeout == 0L) {
            interruptAlive(snapshot);
            return;
        }

        boolean callerInterrupted = Thread.interrupted();
        try {
            long startNanos = System.nanoTime();
            long totalDeadlineNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(boundedTimeout);
            if (!callerInterrupted) {
                long graceDeadlineNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(Math.min(LOCAL_IO_GRACE_MILLIS, boundedTimeout));
                try {
                    joinUntil(snapshot, graceDeadlineNanos);
                }
                catch (InterruptedException e) {
                    callerInterrupted = true;
                }
            }
            interruptAlive(snapshot);
            while (System.nanoTime() < totalDeadlineNanos) {
                try {
                    joinUntil(snapshot, totalDeadlineNanos);
                    break;
                }
                catch (InterruptedException e) {
                    callerInterrupted = true;
                    interruptAlive(snapshot);
                }
            }
        }
        finally {
            if (callerInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isAborted() {
        return cancelled || Thread.currentThread().isInterrupted();
    }

    private boolean sleepInterruptibly(long millis) {
        if (isAborted()) {
            return false;
        }
        sleeping = true;
        try {
            if (isAborted()) {
                return false;
            }
            Thread.sleep(millis);
            return !isAborted();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        finally {
            sleeping = false;
        }
    }

    private static void interruptAlive(List<Map.Entry<Thread, NetworkHandler>> snapshot) {
        for (Map.Entry<Thread, NetworkHandler> entry : snapshot) {
            Thread thread = entry.getKey();
            if (thread.isAlive()) {
                thread.interrupt();
            }
        }
    }

    private static void joinUntil(List<Map.Entry<Thread, NetworkHandler>> snapshot, long deadlineNanos) throws InterruptedException {
        for (Map.Entry<Thread, NetworkHandler> entry : snapshot) {
            Thread thread = entry.getKey();
            if (!thread.isAlive()) {
                continue;
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                break;
            }
            long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
            if (millis > 0L || nanos > 0) {
                thread.join(millis, nanos);
            }
        }
    }

    public static String latestVersion() {
        return latestVersion;
    }

    public static String latestEdgeVersion() {
        return latestEdgeVersion;
    }

    public static String donationKey() {
        return donationKey;
    }

    @Override
    public void run() {
        try {
            if (isAborted() || !ConfigHandler.serverRunning) {
                return;
            }
            try {
                boolean keyValidated = true;
                String keyConfig = Config.getGlobal().DONATION_KEY.trim();
                if (keyConfig.length() > 0) {
                    URL url = new URL("http://coreprotect.net/license/" + keyConfig);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept-Charset", "UTF-8");
                    connection.setRequestProperty("User-Agent", "CoreProtect");
                    connection.setDoOutput(true);
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(5000);
                    connection.connect();
                    int status = connection.getResponseCode();

                    if (status == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String response = reader.readLine();
                        if (response != null && response.length() > 0) {
                            String[] remoteKey = response.replaceAll("[^a-zA-Z0-9;]", "").split(";");
                            if (remoteKey.length > 1 && remoteKey[1].equals("1") && remoteKey[0].length() == 8) {
                                donationKey = remoteKey[0];
                            }
                            else if (remoteKey.length > 1) {
                                donationKey = null;
                            }
                            else {
                                keyValidated = false;
                            }
                        }
                        reader.close();
                    }
                    else {
                        keyValidated = false;
                    }
                }
                else {
                    donationKey = null;
                }

                if (isAborted() || !ConfigHandler.serverRunning) {
                    return;
                }

                try {
                    Path licensePath = Paths.get(ConfigHandler.path + ".license");
                    if (keyValidated && donationKey == null) {
                        if (keyConfig.length() > 0) {
                            Chat.console(Phrase.build(Phrase.INVALID_DONATION_KEY) + " " + Phrase.build(Phrase.CHECK_CONFIG) + ".");
                        }
                        if (!isAborted() && ConfigHandler.serverRunning) {
                            DatabaseConfigWriter.writeAtomically(licensePath, new byte[0]);
                        }
                    }
                    else if (keyValidated) {
                        if (!isAborted() && ConfigHandler.serverRunning) {
                            DatabaseConfigWriter.writeAtomically(licensePath, donationKey.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                    else if (Files.isReadable(licensePath)) {
                        List<String> licenseFile = Files.readAllLines(licensePath);
                        if (licenseFile.size() == 1) {
                            donationKey = licenseFile.get(0);
                            if (donationKey == null || donationKey.length() != 8 || !donationKey.matches("^[A-Z0-9]+$")) {
                                donationKey = null;
                            }
                        }
                    }
                }
                catch (ClosedByInterruptException e) {
                    return;
                }
                catch (Exception e) {
                    if (isAborted()) {
                        return;
                    }
                    ErrorReporter.report(e);
                }
            }
            catch (Exception e) {
                // Unable to connect to coreprotect.net
            }

            if (isAborted() || !ConfigHandler.serverRunning) {
                return;
            }

            if (donationKey != null) {
                // valid donation key, continue initialization
            }

            if (translate) {
                try {
                    String lang = Config.getGlobal().LANGUAGE;
                    String languageCode = lang.trim().toLowerCase();
                    String pluginVersion = VersionUtils.getPluginVersion();

                    if (!languageCode.startsWith("en") && languageCode.length() > 1) {
                        boolean validCache = false;
                        Path languagePath = Paths.get(ConfigHandler.path + ConfigFile.LANGUAGE);
                        Path languageCachePath = Paths.get(ConfigHandler.path + ConfigFile.LANGUAGE_CACHE);

                        // validate that a valid cache file exists
                        if (Files.isReadable(languagePath) && Files.isReadable(languageCachePath)) {
                            try (Stream<String> stream = Files.lines(languageCachePath)) {
                                Optional<String> languageHeader = stream.findFirst();
                                if (languageHeader.isPresent()) {
                                    String headerString = languageHeader.get();
                                    if (headerString.startsWith("# CoreProtect")) { // verify that valid cache file
                                        String[] split = headerString.split(" ");
                                        if (split.length == 6 && split[2].length() > 2 && split[5].length() > 2) {
                                            String cacheVersion = split[2].substring(1);
                                            String cacheLanguage = split[5].substring(1, split[5].length() - 1);
                                            if (cacheVersion.equals(pluginVersion) && cacheLanguage.equals(languageCode)) {
                                                validCache = true;
                                            }
                                            else if (!isAborted() && ConfigHandler.serverRunning) {
                                                ConfigFile.resetCache(ConfigFile.LANGUAGE_CACHE, ConfigFile.LANGUAGE);
                                            }
                                            if (validCache && Files.getLastModifiedTime(languagePath).toMillis() >= Files.getLastModifiedTime(languageCachePath).toMillis()) {
                                                validCache = false;
                                            }
                                        }
                                    }
                                }
                            }
                            catch (Exception e) {
                                if (isAborted()) {
                                    return;
                                }
                                ErrorReporter.report(e);
                            }
                        }

                        if (!validCache && !isAborted() && ConfigHandler.serverRunning) {
                            Set<String> phraseSet = new HashSet<>();
                            Map<String, String> phrases = new HashMap<>();

                            for (Phrase phrase : Phrase.values()) {
                                phraseSet.add(phrase.name());
                                phrases.put(phrase.name(), phrase.getUserPhrase());
                            }

                            phrases.put("DATA_VERSION", pluginVersion);
                            phrases.put("DATA_LANGUAGE", languageCode);

                            String mapString = "data=" + JSONObject.toJSONString(phrases);
                            mapString = mapString.replaceAll("\\+", "{PLUS_SIGN}");
                            byte[] postData = mapString.getBytes(StandardCharsets.UTF_8);
                            int postDataLength = postData.length;

                            try {
                                URL url = new URL("http://coreprotect.net/translate/");
                                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                                connection.setRequestMethod("POST");
                                connection.setRequestProperty("Accept-Charset", "UTF-8");
                                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
                                connection.setRequestProperty("User-Agent", "CoreProtect");
                                connection.setRequestProperty("Content-Length", Integer.toString(postDataLength));
                                connection.setDoOutput(true);
                                connection.setInstanceFollowRedirects(true);
                                connection.setUseCaches(false);
                                connection.setConnectTimeout(5000);

                                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
                                outputStream.write(postData);
                                outputStream.close();

                                int status = connection.getResponseCode();
                                if (status == 200 && !isAborted() && ConfigHandler.serverRunning) {
                                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
                                    StringBuilder responseBuilder = new StringBuilder();
                                    String responseLine = null;
                                    while ((responseLine = reader.readLine()) != null) {
                                        responseBuilder.append(responseLine.trim());
                                    }
                                    reader.close();

                                    String response = responseBuilder.toString();
                                    if (!isAborted() && ConfigHandler.serverRunning && response.length() > 0 && response.startsWith("{") && response.endsWith("}")) {
                                        TreeMap<Phrase, String> translatedPhrases = new TreeMap<>();
                                        JSONParser parser = new JSONParser();
                                        JSONObject json = (JSONObject) parser.parse(response);
                                        for (Object jsonKey : json.keySet()) {
                                            String key = (String) jsonKey;
                                            String value = ((String) json.get(jsonKey)).trim();
                                            if (phraseSet.contains(key) && value.length() > 0) {
                                                Phrase phrase = Phrase.valueOf(key);
                                                translatedPhrases.put(phrase, value);
                                                Language.setTranslatedPhrase(phrase, value);
                                            }
                                        }

                                        Path cachePath = CoreProtect.getInstance().getDataFolder().toPath().resolve(ConfigFile.LANGUAGE_CACHE);
                                        StringBuilder cacheContent = new StringBuilder();
                                        cacheContent.append("# CoreProtect v").append(pluginVersion).append(" Language Cache (").append(languageCode).append(")");
                                        cacheContent.append(Config.LINE_SEPARATOR);

                                        for (final Entry<Phrase, String> entry : translatedPhrases.entrySet()) {
                                            String key = entry.getKey().name();
                                            String value = entry.getValue().replace("\\", "\\\\").replace("\"", "\\\"");

                                            cacheContent.append(Config.LINE_SEPARATOR);
                                            cacheContent.append(key);
                                            cacheContent.append(": ");
                                            cacheContent.append('"').append(value).append('"');
                                        }

                                        if (!isAborted() && ConfigHandler.serverRunning) {
                                            DatabaseConfigWriter.writeAtomically(cachePath, cacheContent.toString().getBytes(StandardCharsets.UTF_8));
                                        }
                                    }
                                }

                                connection.disconnect();
                            }
                            catch (ClosedByInterruptException e) {
                                return;
                            }
                            catch (Exception e) {
                                // Unable to connect to coreprotect.net
                            }
                        }
                    }
                    else if (!isAborted() && ConfigHandler.serverRunning) {
                        ConfigFile.resetCache(ConfigFile.LANGUAGE_CACHE, ConfigFile.LANGUAGE);
                    }

                    // optionally clear user phrases here
                    translate = false;
                }
                catch (Exception e) {
                    if (isAborted()) {
                        return;
                    }
                    ErrorReporter.report(e);
                }
            }

            if (isAborted() || !ConfigHandler.serverRunning || !Config.getGlobal().CHECK_UPDATES) {
                return;
            }

            if (startup && !sleepInterruptibly(1000L)) {
                return;
            }

            while (!isAborted() && ConfigHandler.serverRunning) {
                int status = 0;
                int statusEdge = 0;
                HttpURLConnection connection = null;
                HttpURLConnection connectionEdge = null;
                String version = VersionUtils.getPluginVersion();

                try {
                    // CoreProtect Community Edition
                    URL url = new URL("http://update.coreprotect.net/version/");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept-Charset", "UTF-8");
                    connection.setRequestProperty("User-Agent", "CoreProtect/v" + version + " (by Intelli)");
                    connection.setDoOutput(true);
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(5000);
                    connection.connect();
                    status = connection.getResponseCode();

                    // CoreProtect Edge
                    url = new URL("http://update.coreprotect.net/version-edge/");
                    connectionEdge = (HttpURLConnection) url.openConnection();
                    connectionEdge.setRequestMethod("GET");
                    connectionEdge.setRequestProperty("Accept-Charset", "UTF-8");
                    connectionEdge.setRequestProperty("User-Agent", "CoreProtect/v" + version + " (by Intelli)");
                    connectionEdge.setDoOutput(true);
                    connectionEdge.setInstanceFollowRedirects(true);
                    connectionEdge.setConnectTimeout(5000);
                    connectionEdge.connect();
                    statusEdge = connectionEdge.getResponseCode();
                }
                catch (Exception e) {
                    // Unable to connect to update.coreprotect.net
                }

                if (isAborted() || !ConfigHandler.serverRunning) {
                    return;
                }

                if (status == 200) {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String response = reader.readLine();

                        if (response.length() > 0 && response.length() < 10) {
                            String remoteVersion = response.replaceAll("[^0-9.]", "");
                            if (remoteVersion.contains(".")) {
                                boolean newVersion = VersionUtils.newVersion(version, remoteVersion);
                                if (newVersion) {
                                    latestVersion = remoteVersion;
                                    if (startup) {
                                        Chat.console("--------------------");
                                        Chat.console(Phrase.build(Phrase.VERSION_NOTICE, remoteVersion));
                                        Chat.console(Phrase.build(Phrase.LINK_DOWNLOAD, "www.coreprotect.net/download/"));
                                        Chat.console("--------------------");
                                        startup = false;
                                    }
                                }
                                else {
                                    latestVersion = null;
                                }
                            }
                        }

                        reader.close();
                    }
                    catch (Exception e) {
                        if (isAborted()) {
                            return;
                        }
                        ErrorReporter.report(e);
                    }
                }

                if (statusEdge == 200) {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connectionEdge.getInputStream()));
                        String response = reader.readLine();

                        if (response.length() > 0 && response.length() < 10) {
                            String remoteVersion = response.replaceAll("[^0-9.]", "");
                            if (remoteVersion.contains(".")) {
                                boolean newVersion = VersionUtils.newVersion(version, remoteVersion);
                                if (newVersion) {
                                    latestEdgeVersion = remoteVersion;
                                }
                                else {
                                    latestEdgeVersion = null;
                                }
                            }
                        }

                        reader.close();
                    }
                    catch (Exception e) {
                        if (isAborted()) {
                            return;
                        }
                        ErrorReporter.report(e);
                    }
                }

                if (isAborted() || !ConfigHandler.serverRunning) {
                    return;
                }

                try {
                    /* Stat gathering */
                    int port = Bukkit.getServer().getPort();
                    String stats = port + ":" + (donationKey != null ? donationKey : "") + ":" + version + ConfigHandler.EDITION_BRANCH;
                    URL url = new URL("http://stats.coreprotect.net/u/?data=" + stats);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept-Charset", "UTF-8");
                    connection.setRequestProperty("User-Agent", "CoreProtect");
                    connection.setConnectTimeout(5000);
                    connection.connect();
                    connection.getResponseCode();
                    connection.disconnect();
                }
                catch (Exception e) {
                    // Unable to connect to stats.coreprotect.net
                }

                if (background && !isAborted() && ConfigHandler.serverRunning) {
                    long time = System.currentTimeMillis();
                    long sleepTime = time + 3600000; // 1 hour

                    while (!isAborted() && ConfigHandler.serverRunning && (time < sleepTime)) {
                        time = System.currentTimeMillis();
                        if (!sleepInterruptibly(1000L)) {
                            return;
                        }
                    }
                }
                else {
                    break;
                }
            }
        }
        catch (Exception e) {
            if (e instanceof ClosedByInterruptException || e instanceof InterruptedException || isAborted()) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            Chat.console(Phrase.build(Phrase.UPDATE_ERROR));
            ErrorReporter.report(e);
        }
    }
}
