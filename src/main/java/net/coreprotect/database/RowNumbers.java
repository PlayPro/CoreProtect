package net.coreprotect.database;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates auto_increment
 */
public class RowNumbers {
    private final CoreProtect plugin;
    private final Path file;
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Object initializationLock = new Object();

    public RowNumbers(final CoreProtect plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataPath().resolve("row-numbers.json");
    }

    @Deprecated(forRemoval = true)
    public int nextRowId(final String tableName, Connection connection) {
        return Math.toIntExact(nextRowNumber(tableName, connection));
    }

    public long nextRowNumber(final String tableName, Connection connection) {
        AtomicLong counter = this.counters.get(tableName);
        if (counter != null) {
            return counter.getAndIncrement();
        }

        // Select max row from database and cache it
        synchronized (this.initializationLock) {
            // Re-check in case another thread got it first
            counter = this.counters.get(tableName);
            if (counter != null) {
                return counter.getAndIncrement();
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT MAX(rowid) + 1 FROM " + ConfigHandler.prefix + tableName);
                 ResultSet rs = statement.executeQuery()) {

                if (rs.next()) {
                    counter = new AtomicLong(rs.getLong(1));

                    this.counters.put(tableName, counter);
                    return counter.getAndIncrement();
                }
            } catch (SQLException e) {
                plugin.getSLF4JLogger().warn("Failed to read maximum rowid from {}{}", ConfigHandler.prefix, tableName, e);
            }

            // fallback
            this.counters.put(tableName, new AtomicLong());
            return 0;
        }
    }

    public void initialize() {
        if (!Files.exists(this.file)) {
            return;
        }

        try {
            Map<String, AtomicLong> saved = new Gson().fromJson(Files.readString(this.file, StandardCharsets.UTF_8), new TypeToken<Map<String, AtomicLong>>(){}.getType());
            this.counters.putAll(saved);
        } catch (IOException e) {
            plugin.getSLF4JLogger().warn("Failed to read {}", this.file.getFileName(), e);
        }

        try {
            // Delete the file right after loading, so that we do not re-read old counters if the plugin failed to shut down properly
            Files.delete(this.file);
        } catch (IOException e) {
            plugin.getSLF4JLogger().warn("Failed to delete {} after loading", this.file.getFileName(), e);
        }
    }

    public void set(Map<String, Long> tableCounts) {
        synchronized (this.initializationLock) {
            this.counters.clear();

            for (final Map.Entry<String, Long> count : tableCounts.entrySet()) {
                this.counters.put(count.getKey(), new AtomicLong(count.getValue()));
            }
        }
    }

    public void save() {
        try {
            Files.writeString(this.file, new Gson().toJson(counters, new TypeToken<Map<String, AtomicLong>>(){}.getType()), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            plugin.getSLF4JLogger().warn("Failed to save {}", this.file.getFileName(), e);
        }
    }
}
