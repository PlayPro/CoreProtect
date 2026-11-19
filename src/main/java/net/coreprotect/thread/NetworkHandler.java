package net.coreprotect.thread;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
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
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

public class NetworkHandler implements Runnable {

    private static final long LOCAL_IO_GRACE_MILLIS = 250L;
    private static final int HTTP_TIMEOUT_MILLIS = 5000;
    private static final int MAX_HTTP_REDIRECTS = 5;
    private static final Pattern VERSION_BASE_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+)+$");
    private static final Object LOCK = new Object();
    private static final Object LICENSE_LOCK = new Object();
    private static final AtomicLong LICENSE_EPOCH = new AtomicLong();
    private static final Map<Thread, NetworkHandler> ACTIVE_HANDLERS = new IdentityHashMap<>();

    private static volatile String licenseBaseUrl = "https://coreprotect.net/license/";
    private static volatile String translateUrl = "https://coreprotect.net/translate/";
    private static volatile String updateVersionUrl = "https://update.coreprotect.net/version/";
    private static volatile String updateEdgeVersionUrl = "https://update.coreprotect.net/version-edge/";
    private static volatile String statsBaseUrl = "https://stats.coreprotect.net/u/?data=";

    private boolean startup = true;
    private boolean background = false;
    private boolean translate = true;
    private volatile boolean cancelled = false;
    private volatile boolean sleeping = false;
    private static volatile String latestVersion = null;
    private static volatile String latestEdgeVersion = null;
    private static volatile String donationKey = null;
    private static volatile int serverPort = 0;

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

    public static void setServerPort(int port) {
        serverPort = port;
    }

    public static void loadCachedLicense() {
        synchronized (LICENSE_LOCK) {
            if (donationKey != null) {
                return;
            }
            Path licensePath = Paths.get(ConfigHandler.path + ".license");
            if (Files.isReadable(licensePath)) {
                try {
                    String cached = readLicenseFile(licensePath);
                    if (cached != null) {
                        donationKey = cached;
                    }
                }
                catch (Exception e) {
                    // Ignore unreadable cached license during early startup
                }
            }
        }
    }

    static void setEndpointsForTesting(String licenseBase, String translate, String updateVersion, String updateEdgeVersion, String statsBase) {
        licenseBaseUrl = licenseBase;
        translateUrl = translate;
        updateVersionUrl = updateVersion;
        updateEdgeVersionUrl = updateEdgeVersion;
        statsBaseUrl = statsBase;
    }

    static void resetEndpoints() {
        licenseBaseUrl = "https://coreprotect.net/license/";
        translateUrl = "https://coreprotect.net/translate/";
        updateVersionUrl = "https://update.coreprotect.net/version/";
        updateEdgeVersionUrl = "https://update.coreprotect.net/version-edge/";
        statsBaseUrl = "https://stats.coreprotect.net/u/?data=";
    }

    static void resetStateForTesting() {
        LICENSE_EPOCH.incrementAndGet();
        latestVersion = null;
        latestEdgeVersion = null;
        donationKey = null;
        serverPort = 0;
        resetEndpoints();
    }

    private static String readLicenseFile(Path licensePath) throws IOException {
        List<String> licenseFile = Files.readAllLines(licensePath, StandardCharsets.UTF_8);
        if (licenseFile.size() == 1) {
            String value = licenseFile.get(0);
            if (value != null && value.length() == 8 && value.matches("^[A-Z0-9]+$")) {
                return value;
            }
        }
        return null;
    }

    private static final class HttpResult {
        private final int status;
        private final String body;

        private HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static HttpResult executeHttpRequest(String initialUrl, String method, String userAgent, String contentType, byte[] postBody, boolean readFullBody) throws IOException {
        String currentUrl = initialUrl;
        for (int redirectCount = 0; redirectCount <= MAX_HTTP_REDIRECTS; redirectCount++) {
            URL url = URI.create(currentUrl).toURL();
            String currentScheme = url.getProtocol() == null ? "" : url.getProtocol().toLowerCase(Locale.ROOT);
            if (!"http".equals(currentScheme) && !"https".equals(currentScheme)) {
                throw new IOException("Unsupported HTTP scheme: " + currentScheme);
            }

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setRequestMethod(method);
                connection.setRequestProperty("Accept-Charset", "UTF-8");
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setConnectTimeout(HTTP_TIMEOUT_MILLIS);
                connection.setReadTimeout(HTTP_TIMEOUT_MILLIS);
                if (postBody != null && "POST".equals(method)) {
                    connection.setRequestProperty("Content-Type", contentType);
                    connection.setRequestProperty("Content-Length", Integer.toString(postBody.length));
                    connection.setDoOutput(true);
                    try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                        outputStream.write(postBody);
                    }
                }
                else {
                    connection.connect();
                }

                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_SEE_OTHER
                        || status == 307
                        || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty() || redirectCount == MAX_HTTP_REDIRECTS) {
                        return new HttpResult(status, null);
                    }
                    URI nextUri;
                    try {
                        nextUri = url.toURI().resolve(location.trim());
                    }
                    catch (Exception e) {
                        throw new IOException("Invalid redirect URI", e);
                    }
                    String nextScheme = nextUri.getScheme() == null ? "" : nextUri.getScheme().toLowerCase(Locale.ROOT);
                    if (!"http".equals(nextScheme) && !"https".equals(nextScheme)) {
                        throw new IOException("Unsupported redirect scheme: " + nextScheme);
                    }
                    if ("https".equals(currentScheme) && "http".equals(nextScheme)) {
                        throw new IOException("Refusing HTTPS to HTTP downgrade redirect");
                    }
                    currentUrl = nextUri.toString();
                    continue;
                }

                String body = null;
                if (status == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        if (readFullBody) {
                            StringBuilder responseBuilder = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                responseBuilder.append(line.trim());
                            }
                            body = responseBuilder.toString();
                        }
                        else {
                            body = reader.readLine();
                        }
                    }
                }
                return new HttpResult(status, body);
            }
            finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    static String parseRemoteVersion(String response) {
        if (response == null) {
            return null;
        }
        String trimmed = response.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1).trim();
        }
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
        if (cutIndex >= 0) {
            String suffix = trimmed.substring(cutIndex + 1);
            for (int i = 0; i < suffix.length(); i++) {
                char c = suffix.charAt(i);
                if (Character.isWhitespace(c) || c == '<' || c == '>') {
                    return null;
                }
            }
            trimmed = trimmed.substring(0, cutIndex).trim();
        }
        if (!VERSION_BASE_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }

    @Override
    public void run() {
        try {
            if (isAborted() || !ConfigHandler.serverRunning) {
                return;
            }
            try {
                final long licenseEpoch;
                final String keyConfig;
                synchronized (LICENSE_LOCK) {
                    licenseEpoch = LICENSE_EPOCH.incrementAndGet();
                    String rawKey = Config.getGlobal().DONATION_KEY;
                    keyConfig = rawKey == null ? "" : rawKey.trim();
                }

                boolean keyValidated = true;
                String resolvedKey = null;
                if (keyConfig.length() > 0) {
                    String encodedKey = URLEncoder.encode(keyConfig, StandardCharsets.UTF_8).replace("+", "%20");
                    HttpResult result = executeHttpRequest(licenseBaseUrl + encodedKey, "GET", "CoreProtect", null, null, false);
                    if (result.status == HttpURLConnection.HTTP_OK) {
                        String response = result.body;
                        if (response != null && response.length() > 0) {
                            String[] remoteKey = response.replaceAll("[^a-zA-Z0-9;]", "").split(";");
                            if (remoteKey.length > 1 && remoteKey[1].equals("1") && remoteKey[0].length() == 8) {
                                resolvedKey = remoteKey[0];
                            }
                            else if (remoteKey.length > 1) {
                                resolvedKey = null;
                            }
                            else {
                                keyValidated = false;
                            }
                        }
                        else {
                            keyValidated = false;
                        }
                    }
                    else {
                        keyValidated = false;
                    }
                }

                if (isAborted() || !ConfigHandler.serverRunning) {
                    return;
                }

                try {
                    synchronized (LICENSE_LOCK) {
                        if (LICENSE_EPOCH.get() != licenseEpoch || isAborted() || !ConfigHandler.serverRunning) {
                            return;
                        }
                        Path licensePath = Paths.get(ConfigHandler.path + ".license");
                        if (keyValidated && resolvedKey == null) {
                            donationKey = null;
                            if (keyConfig.length() > 0) {
                                Chat.console(Phrase.build(Phrase.INVALID_DONATION_KEY) + " " + Phrase.build(Phrase.CHECK_CONFIG) + ".");
                            }
                            if (!isAborted() && ConfigHandler.serverRunning) {
                                DatabaseConfigWriter.writeAtomically(licensePath, new byte[0]);
                            }
                        }
                        else if (keyValidated) {
                            donationKey = resolvedKey;
                            if (!isAborted() && ConfigHandler.serverRunning) {
                                DatabaseConfigWriter.writeAtomically(licensePath, resolvedKey.getBytes(StandardCharsets.UTF_8));
                            }
                        }
                        else if (Files.isReadable(licensePath)) {
                            donationKey = readLicenseFile(licensePath);
                        }
                        else {
                            donationKey = null;
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
                    String languageCode = lang == null ? "en" : lang.trim().toLowerCase(Locale.ROOT);
                    String pluginVersion = VersionUtils.getPluginVersion();

                    if (!languageCode.startsWith("en") && languageCode.length() > 1) {
                        boolean validCache = false;
                        boolean shouldTranslate = false;
                        long epoch = 0L;
                        Set<String> phraseSet = null;
                        Map<String, String> phrases = null;

                        synchronized (ConfigFile.LANGUAGE_LOCK) {
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
                                epoch = ConfigFile.getLanguageEpoch();
                                phraseSet = new HashSet<>();
                                phrases = new HashMap<>();

                                for (Phrase phrase : Phrase.values()) {
                                    phraseSet.add(phrase.name());
                                    phrases.put(phrase.name(), phrase.getUserPhrase());
                                }

                                phrases.put("DATA_VERSION", pluginVersion);
                                phrases.put("DATA_LANGUAGE", languageCode);
                                shouldTranslate = true;
                            }
                        }

                        if (shouldTranslate && !isAborted() && ConfigHandler.serverRunning) {
                            String mapString = "data=" + URLEncoder.encode(JSONObject.toJSONString(phrases), StandardCharsets.UTF_8);
                            byte[] postData = mapString.getBytes(StandardCharsets.UTF_8);

                            try {
                                HttpResult result = executeHttpRequest(translateUrl, "POST", "CoreProtect", "application/x-www-form-urlencoded; charset=utf-8", postData, true);
                                if (result.status == HttpURLConnection.HTTP_OK && result.body != null && !isAborted() && ConfigHandler.serverRunning) {
                                    String response = result.body;
                                    if (response.length() > 0 && response.startsWith("{") && response.endsWith("}")) {
                                        TreeMap<Phrase, String> translatedPhrases = new TreeMap<>();
                                        JSONParser parser = new JSONParser();
                                        Object parsedObject = parser.parse(response);
                                        if (parsedObject instanceof JSONObject) {
                                            JSONObject json = (JSONObject) parsedObject;
                                            for (Object jsonKey : json.keySet()) {
                                                if (!(jsonKey instanceof String)) {
                                                    continue;
                                                }
                                                String key = (String) jsonKey;
                                                Object rawVal = json.get(jsonKey);
                                                if (!(rawVal instanceof String)) {
                                                    continue;
                                                }
                                                String value = ((String) rawVal).trim();
                                                if (phraseSet.contains(key) && value.length() > 0) {
                                                    Phrase phrase = Phrase.valueOf(key);
                                                    translatedPhrases.put(phrase, value);
                                                }
                                            }
                                        }

                                        synchronized (ConfigFile.LANGUAGE_LOCK) {
                                            if (!isAborted() && ConfigHandler.serverRunning && ConfigFile.getLanguageEpoch() == epoch) {
                                                for (final Entry<Phrase, String> entry : translatedPhrases.entrySet()) {
                                                    Language.setTranslatedPhrase(entry.getKey(), entry.getValue());
                                                }

                                                Path cachePath = CoreProtect.getInstance().getDataFolder().toPath().resolve(ConfigFile.LANGUAGE_CACHE);
                                                StringBuilder cacheContent = new StringBuilder();
                                                cacheContent.append("# CoreProtect v").append(pluginVersion).append(" Language Cache (").append(languageCode).append(")");
                                                cacheContent.append(Config.LINE_SEPARATOR);

                                                for (final Entry<Phrase, String> entry : translatedPhrases.entrySet()) {
                                                    String key = entry.getKey().name();
                                                    String value = ConfigFile.escapeQuotedValue(entry.getValue());

                                                    cacheContent.append(Config.LINE_SEPARATOR);
                                                    cacheContent.append(key);
                                                    cacheContent.append(": ");
                                                    cacheContent.append('"').append(value).append('"');
                                                }

                                                DatabaseConfigWriter.writeAtomically(cachePath, cacheContent.toString().getBytes(StandardCharsets.UTF_8));
                                            }
                                        }
                                    }
                                }
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

            if (isAborted() || !ConfigHandler.serverRunning) {
                return;
            }
            if (!Config.getGlobal().CHECK_UPDATES) {
                latestVersion = null;
                latestEdgeVersion = null;
                return;
            }

            if (startup && !sleepInterruptibly(1000L)) {
                return;
            }

            while (!isAborted() && ConfigHandler.serverRunning) {
                if (!Config.getGlobal().CHECK_UPDATES) {
                    latestVersion = null;
                    latestEdgeVersion = null;
                    return;
                }

                String version = VersionUtils.getPluginVersion();
                String userAgent = "CoreProtect/v" + version + " (by Intelli)";

                HttpResult releaseResult = null;
                HttpResult edgeResult = null;
                try {
                    releaseResult = executeHttpRequest(updateVersionUrl, "GET", userAgent, null, null, false);
                }
                catch (Exception e) {
                    // Unable to connect to update.coreprotect.net
                }
                if (!isAborted() && ConfigHandler.serverRunning && Config.getGlobal().CHECK_UPDATES) {
                    try {
                        edgeResult = executeHttpRequest(updateEdgeVersionUrl, "GET", userAgent, null, null, false);
                    }
                    catch (Exception e) {
                        // Unable to connect to update.coreprotect.net
                    }
                }

                if (isAborted() || !ConfigHandler.serverRunning || !Config.getGlobal().CHECK_UPDATES) {
                    if (!Config.getGlobal().CHECK_UPDATES) {
                        latestVersion = null;
                        latestEdgeVersion = null;
                    }
                    return;
                }

                if (releaseResult != null && releaseResult.status == HttpURLConnection.HTTP_OK) {
                    try {
                        String remoteVersion = parseRemoteVersion(releaseResult.body);
                        if (remoteVersion != null) {
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
                    catch (Exception e) {
                        if (isAborted()) {
                            return;
                        }
                        ErrorReporter.report(e);
                    }
                }

                if (edgeResult != null && edgeResult.status == HttpURLConnection.HTTP_OK) {
                    try {
                        String remoteVersion = parseRemoteVersion(edgeResult.body);
                        if (remoteVersion != null) {
                            boolean newVersion = VersionUtils.newVersion(version, remoteVersion);
                            if (newVersion) {
                                latestEdgeVersion = remoteVersion;
                            }
                            else {
                                latestEdgeVersion = null;
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

                if (isAborted() || !ConfigHandler.serverRunning) {
                    return;
                }

                try {
                    /* Stat gathering */
                    int port = serverPort;
                    String currentKey = donationKey;
                    String stats = port + ":" + (currentKey != null ? currentKey : "") + ":" + version + ConfigHandler.EDITION_BRANCH;
                    String encodedStats = URLEncoder.encode(stats, StandardCharsets.UTF_8);
                    executeHttpRequest(statsBaseUrl + encodedStats, "GET", "CoreProtect", null, null, false);
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
