package net.coreprotect.utility;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.json.simple.JSONObject;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;

public final class ErrorReporter {

    private static final String REPORT_URL = "https://error-reporting.coreprotect.net/";
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;
    private static final int MAX_QUEUE_SIZE = 100;
    private static final int MAX_FRAMES = 32;
    private static final int MAX_CAUSE_DEPTH = 4;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final Set<String> SENT_FINGERPRINTS = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final LinkedBlockingQueue<JSONObject> QUEUE = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private static final AtomicBoolean WORKER_RUNNING = new AtomicBoolean(false);
    private static volatile ReportSender sender = ErrorReporter::sendHttp;

    private ErrorReporter() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean report(Throwable throwable) {
        return reportInternal(throwable, true);
    }

    public static boolean report(Throwable throwable, boolean printStackTrace) {
        return reportInternal(throwable, printStackTrace);
    }

    static void setSender(ReportSender reportSender) {
        sender = reportSender == null ? ErrorReporter::sendHttp : reportSender;
    }

    static void reset() {
        SENT_FINGERPRINTS.clear();
        QUEUE.clear();
        WORKER_RUNNING.set(false);
        sender = ErrorReporter::sendHttp;
    }

    private static boolean reportInternal(Throwable throwable, boolean printStackTrace) {
        if (throwable == null) {
            return false;
        }

        if (printStackTrace) {
            throwable.printStackTrace();
        }

        if (!isEnabled()) {
            return false;
        }

        String fingerprint = fingerprint(throwable);
        if (!SENT_FINGERPRINTS.add(fingerprint)) {
            return false;
        }

        JSONObject report = buildReport(throwable, fingerprint);
        boolean queued = QUEUE.offer(report);
        if (!queued) {
            SENT_FINGERPRINTS.remove(fingerprint);
            return false;
        }

        startWorker();
        return true;
    }

    private static boolean isEnabled() {
        try {
            return Config.getGlobal().ERROR_REPORTING;
        }
        catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static JSONObject buildReport(Throwable throwable, String fingerprint) {
        JSONObject json = new JSONObject();
        json.put("fingerprint", fingerprint);
        json.put("plugin_version", safePluginVersion());
        json.put("branch", ConfigHandler.EDITION_BRANCH);
        json.put("java_version", System.getProperty("java.version", ""));
        json.put("server_version", safeServerVersion());
        json.put("bukkit_version", safeBukkitVersion());
        json.put("spigot", ConfigHandler.isSpigot);
        json.put("paper", ConfigHandler.isPaper);
        json.put("folia", ConfigHandler.isFolia);
        json.put("throwable", throwableJson(throwable, 0, Collections.newSetFromMap(new IdentityHashMap<>())));
        return json;
    }

    @SuppressWarnings("unchecked")
    private static JSONObject throwableJson(Throwable throwable, int depth, Set<Throwable> seen) {
        JSONObject json = new JSONObject();
        if (throwable == null || depth >= MAX_CAUSE_DEPTH || !seen.add(throwable)) {
            return json;
        }

        json.put("type", throwable.getClass().getName());
        json.put("message", normalizeMessage(throwable.getMessage()));
        json.put("stack", filteredStack(throwable.getStackTrace()));

        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            json.put("cause", throwableJson(cause, depth + 1, seen));
        }

        return json;
    }

    private static List<String> filteredStack(StackTraceElement[] stackTrace) {
        List<String> frames = new ArrayList<>();
        int firstCoreProtectFrame = -1;
        for (int i = 0; i < stackTrace.length; i++) {
            if (isCoreProtectFrame(stackTrace[i])) {
                firstCoreProtectFrame = i;
                break;
            }
        }

        if (firstCoreProtectFrame >= 0) {
            for (int i = 0; i < firstCoreProtectFrame && frames.size() < 4; i++) {
                if (!isNoiseFrame(stackTrace[i])) {
                    frames.add(stackTrace[i].toString());
                }
            }

            for (int i = firstCoreProtectFrame; i < stackTrace.length && frames.size() < MAX_FRAMES; i++) {
                if (isCoreProtectFrame(stackTrace[i])) {
                    frames.add(stackTrace[i].toString());
                }
            }
        }
        else {
            for (StackTraceElement frame : stackTrace) {
                if (frames.size() >= MAX_FRAMES) {
                    break;
                }
                if (!isNoiseFrame(frame)) {
                    frames.add(frame.toString());
                }
            }
        }

        return frames;
    }

    private static boolean isCoreProtectFrame(StackTraceElement frame) {
        return frame.getClassName().startsWith("net.coreprotect.");
    }

    private static boolean isNoiseFrame(StackTraceElement frame) {
        String className = frame.getClassName();
        return className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("jdk.") || className.startsWith("sun.");
    }

    private static String fingerprint(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        builder.append(throwable.getClass().getName()).append('|');
        builder.append(normalizeMessage(throwable.getMessage())).append('|');
        appendCoreProtectFrames(builder, throwable.getStackTrace(), 3);

        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            builder.append("|cause:").append(cause.getClass().getName()).append('|');
            builder.append(normalizeMessage(cause.getMessage())).append('|');
            appendCoreProtectFrames(builder, cause.getStackTrace(), 2);
        }

        return hash(builder.toString());
    }

    private static void appendCoreProtectFrames(StringBuilder builder, StackTraceElement[] stackTrace, int limit) {
        int added = 0;
        for (StackTraceElement frame : stackTrace) {
            if (!isCoreProtectFrame(frame)) {
                continue;
            }

            if (added > 0) {
                builder.append(',');
            }
            builder.append(frame.getClassName()).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
            added++;
            if (added >= limit) {
                break;
            }
        }
    }

    private static String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }

        String normalized = message.replaceAll("@[0-9a-fA-F]{4,16}", "@");
        normalized = normalized.replaceAll("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b", "<uuid>");
        normalized = normalized.replaceAll("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", "<ip>");
        normalized = normalized.replaceAll("([A-Za-z]:)?[/\\\\][^\\s:;]+", "<path>");
        normalized = normalized.replaceAll("\\b\\d{10,}\\b", "<number>");
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            normalized = normalized.substring(0, MAX_MESSAGE_LENGTH);
        }

        return normalized;
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static void startWorker() {
        if (!WORKER_RUNNING.compareAndSet(false, true)) {
            return;
        }

        Thread worker = new Thread(() -> {
            try {
                while (ConfigHandler.serverRunning || !QUEUE.isEmpty()) {
                    JSONObject report = QUEUE.poll(60, TimeUnit.SECONDS);
                    if (report != null) {
                        send(report);
                    }
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finally {
                WORKER_RUNNING.set(false);
                if (!QUEUE.isEmpty()) {
                    startWorker();
                }
            }
        }, "CoreProtect Error Reporter");
        worker.setDaemon(true);
        worker.start();
    }

    private static void send(JSONObject report) {
        try {
            sender.send(report);
        }
        catch (Exception e) {
            // Error reporting must never create secondary console noise.
        }
    }

    private static void sendHttp(JSONObject report) throws Exception {
        String postData = "data=" + URLEncoder.encode(report.toJSONString(), StandardCharsets.UTF_8.name());
        byte[] bytes = postData.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = null;

        try {
            URL url = new URL(REPORT_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept-Charset", "UTF-8");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            connection.setRequestProperty("User-Agent", "CoreProtect/v" + safePluginVersion() + " (by Intelli)");
            connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);

            try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                outputStream.write(bytes);
            }

            connection.getResponseCode();
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String safePluginVersion() {
        try {
            return VersionUtils.getPluginVersion() + ConfigHandler.EDITION_BRANCH;
        }
        catch (Exception e) {
            return "";
        }
    }

    private static String safeServerVersion() {
        try {
            return Bukkit.getServer().getVersion();
        }
        catch (Exception e) {
            return "";
        }
    }

    private static String safeBukkitVersion() {
        try {
            return Bukkit.getServer().getBukkitVersion();
        }
        catch (Exception e) {
            return "";
        }
    }

    @FunctionalInterface
    interface ReportSender {
        void send(JSONObject report) throws Exception;
    }
}
