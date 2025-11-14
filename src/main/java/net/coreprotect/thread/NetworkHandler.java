package net.coreprotect.thread;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigFile;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Language;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.VersionUtils;

public class NetworkHandler extends Language implements Runnable {

    private boolean startup = true;
    private boolean background = false;
    private boolean translate = true;
    private static String latestVersion = null;
    private static String latestEdgeVersion = null;
    private static String donationKey = null;

    public NetworkHandler(boolean startup, boolean background) {
        this.startup = startup;
        this.background = background;
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

                try {
                    Path licensePath = Paths.get(ConfigHandler.path + ".license");
                    if (keyValidated && donationKey == null) {
                        if (keyConfig.length() > 0) {
                            Chat.console(Phrase.build(Phrase.INVALID_DONATION_KEY) + " " + Phrase.build(Phrase.CHECK_CONFIG) + ".");
                        }
                        Files.write(licensePath, "".getBytes());
                    }
                    else if (keyValidated) {
                        Files.write(licensePath, donationKey.getBytes());
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
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            catch (Exception e) {
                // Unable to connect to coreprotect.net
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
                                            else {
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
                                e.printStackTrace();
                            }
                        }

                        if (!validCache) {
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
                                if (status == 200) {
                                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
                                    StringBuilder responseBuilder = new StringBuilder();
                                    String responseLine = null;
                                    while ((responseLine = reader.readLine()) != null) {
                                        responseBuilder.append(responseLine.trim());
                                    }
                                    reader.close();

                                    String response = responseBuilder.toString();
                                    if (response.length() > 0 && response.startsWith("{") && response.endsWith("}")) {
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

                                        File file = new File(CoreProtect.getInstance().getDataFolder(), ConfigFile.LANGUAGE_CACHE);
                                        try (final FileOutputStream fout = new FileOutputStream(file, false)) {
                                            OutputStreamWriter out = new OutputStreamWriter(new BufferedOutputStream(fout), StandardCharsets.UTF_8);
                                            out.append("# CoreProtect v" + pluginVersion + " Language Cache (" + languageCode + ")");
                                            out.append(Config.LINE_SEPARATOR);

                                            for (final Entry<Phrase, String> entry : translatedPhrases.entrySet()) {
                                                String key = entry.getKey().name();
                                                String value = entry.getValue().replaceAll("\"", "\\\\\"");

                                                out.append(Config.LINE_SEPARATOR);
                                                out.append(key);
                                                out.append(": ");
                                                out.append("\"" + value + "\"");
                                            }

                                            out.close();
                                        }
                                    }
                                }

                                connection.disconnect();
                            }
                            catch (Exception e) {
                                // Unable to connect to coreprotect.net
                            }
                        }
                    }
                    else {
                        ConfigFile.resetCache(ConfigFile.LANGUAGE_CACHE, ConfigFile.LANGUAGE);
                    }

                    // optionally clear user phrases here
                    translate = false;
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (!Config.getGlobal().CHECK_UPDATES) {
                return;
            }

            if (startup) {
                Thread.sleep(1000);
            }

            while (ConfigHandler.serverRunning) {
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
                        e.printStackTrace();
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
                        e.printStackTrace();
                    }
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

                if (background) {
                    long time = System.currentTimeMillis();
                    long sleepTime = time + 3600000; // 1 hour

                    while (ConfigHandler.serverRunning && (time < sleepTime)) {
                        time = System.currentTimeMillis();
                        Thread.sleep(1000);
                    }
                }
                else {
                    break;
                }
            }
        }
        catch (Exception e) {
            Chat.console(Phrase.build(Phrase.UPDATE_ERROR));
            e.printStackTrace();
        }
    }
}
