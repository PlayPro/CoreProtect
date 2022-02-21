package net.coreprotect.config;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import net.coreprotect.CoreProtect;
import net.coreprotect.language.Language;
import net.coreprotect.language.Phrase;

public class ConfigFile extends Config {

    public static final String CONFIG = "config.yml";
    public static final String LANGUAGE = "language.yml";
    public static final String LANGUAGE_CACHE = ".language";

    private static final TreeMap<String, String> DEFAULT_VALUES = new TreeMap<>();
    private static final TreeMap<String, String> USER_VALUES = new TreeMap<>();
    private static final String DEFAULT_FILE_HEADER = "# CoreProtect Language File (en)";
    private final HashMap<String, String> lang;

    public static void init(String fileName) throws IOException {
        for (Phrase phrase : Phrase.values()) {
            DEFAULT_VALUES.put(phrase.name(), phrase.getPhrase());
            USER_VALUES.put(phrase.name(), phrase.getUserPhrase());
        }

        boolean isCache = fileName.startsWith(".");
        loadFiles(fileName, isCache);
    }

    public ConfigFile() {
        this.lang = new LinkedHashMap<>();
    }

    public void load(final InputStream in, String fileName, boolean isCache) throws IOException {
        // if we fail reading, we will not corrupt our current config.
        final Map<String, String> newConfig = new LinkedHashMap<>(this.lang.size());
        ConfigFile.load(in, newConfig, true);

        this.lang.clear();
        this.lang.putAll(newConfig);

        for (final Entry<String, String> entry : this.lang.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (DEFAULT_VALUES.containsKey(key) && value.length() > 0 && (!isCache || DEFAULT_VALUES.get(key).equals(USER_VALUES.get(key)))) {
                Phrase phrase = Phrase.valueOf(key);
                if (!isCache) {
                    Language.setUserPhrase(phrase, value);
                }

                Language.setTranslatedPhrase(phrase, value);
            }
        }
    }

    // this function will close in
    public static void load(final InputStream in, final Map<String, String> config, boolean forceCase) throws IOException {
        try (final InputStream in0 = in) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.startsWith("#")) {
                    continue;
                }

                final int split = line.indexOf(':');

                if (split == -1) {
                    continue;
                }

                String key = line.substring(0, split).trim();
                String value = line.substring(split + 1).trim();

                // Strip out single and double quotes from the start/end of the value
                if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
                    value = value.replaceAll("^'|'$", "");
                    value = value.replace("''", "'");
                    value = value.replace("\\'", "'");
                    value = value.replace("\\\\", "\\");
                }
                else if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.replaceAll("^\"|\"$", "");
                    value = value.replace("\\\"", "\"");
                    value = value.replace("\\\\", "\\");
                }

                if (forceCase) {
                    key = key.toUpperCase(Locale.ROOT);
                }
                config.put(key, value);
            }

            reader.close();
        }
    }

    private static Map<String, byte[]> loadFiles(String fileName, boolean isCache) throws IOException {
        final CoreProtect plugin = CoreProtect.getInstance();
        final File configFolder = plugin.getDataFolder();
        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }

        final Map<String, byte[]> map = new HashMap<>();
        final File globalFile = new File(configFolder, fileName);

        if (globalFile.exists()) {
            // we always add options to the global config
            final byte[] data = Files.readAllBytes(globalFile.toPath());
            map.put("config", data);

            final ConfigFile temp = new ConfigFile();
            temp.load(new ByteArrayInputStream(data), fileName, isCache);
            temp.addMissingOptions(globalFile);
        }
        else {
            final ConfigFile temp = new ConfigFile();
            temp.addMissingOptions(globalFile);
        }

        return map;
    }

    @Override
    public void addMissingOptions(final File file) throws IOException {
        if (file.getName().startsWith(".")) {
            return;
        }

        final boolean writeHeader = !file.exists() || file.length() == 0;
        try (final FileOutputStream fout = new FileOutputStream(file, true)) {
            OutputStreamWriter out = new OutputStreamWriter(new BufferedOutputStream(fout), StandardCharsets.UTF_8);
            if (writeHeader) {
                out.append(DEFAULT_FILE_HEADER);
                out.append(Config.LINE_SEPARATOR);
            }

            for (final Entry<String, String> entry : DEFAULT_VALUES.entrySet()) {
                final String key = entry.getKey();
                final String defaultValue = entry.getValue().replaceAll("\"", "\\\\\"");

                final String configuredValue = this.lang.get(key);
                if (configuredValue != null) {
                    continue;
                }

                out.append(Config.LINE_SEPARATOR);
                out.append(key);
                out.append(": ");
                out.append("\"" + defaultValue + "\"");
            }

            out.close();
        }
    }

    public static void modifyLine(String fileName, String oldLine, String newLine) {
        try {
            Path path = Paths.get(ConfigHandler.path + fileName);
            List<String> lines = Files.readAllLines(path);

            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).equalsIgnoreCase(oldLine)) {
                    if (newLine != null && newLine.length() > 0) {
                        lines.set(i, newLine);
                    }
                    else {
                        lines.remove(i);
                    }

                    break;
                }
            }

            if (lines.size() > 0) {
                String lastLine = lines.get(lines.size() - 1); // append the final line to prevent a line separator from being added
                Files.write(path, (lines.remove(lines.size() - 1).isEmpty() ? lines : lines), StandardCharsets.UTF_8);
                Files.write(path, lastLine.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
                lines.clear();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sortFile(String fileName) {
        try {
            Path path = Paths.get(ConfigHandler.path + fileName);
            List<String> lines = Files.readAllLines(path);
            List<String> sort = lines.subList(2, lines.size());
            Collections.sort(sort);
            lines = lines.subList(0, 2);
            lines.addAll(sort);

            if (lines.size() > 0) {
                String lastLine = lines.get(lines.size() - 1); // append the final line to prevent a line separator from being added
                Files.write(path, (lines.remove(lines.size() - 1).isEmpty() ? lines : lines), StandardCharsets.UTF_8);
                Files.write(path, lastLine.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
                lines.clear();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void resetCache(String cacheName, String fileName) throws IOException {
        File file = new File(CoreProtect.getInstance().getDataFolder(), cacheName);
        if (file.length() > 0) {
            new FileOutputStream(file).close();
            init(fileName);
        }
    }
}
