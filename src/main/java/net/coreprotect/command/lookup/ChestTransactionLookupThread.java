package net.coreprotect.command.lookup;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.ChestTransactionLookup;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class ChestTransactionLookupThread implements Runnable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final CoreProtect plugin;
    private final CommandSender player;
    private final Command command;
    private final Location location;
    private final int page;
    private final int limit;
    private final boolean exportMode;

    public ChestTransactionLookupThread(CoreProtect plugin, CommandSender player, Command command, Location location, int page, int limit, boolean exportMode) {
        this.plugin = plugin;
        this.player = player;
        this.command = command;
        this.location = location;
        this.page = page;
        this.limit = limit;
        this.exportMode = exportMode;
    }

    @Override
    public void run() {
        try (Connection connection = Database.getConnection(true)) {
            ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });
            if (connection != null) {
                Statement statement = connection.createStatement();

                if (exportMode) {
                    exportContainerTransactions(statement);
                }
                else {
                    List<String> blockData = ChestTransactionLookup.performLookup(command.getName(), statement, location, player, page, limit, false);
                    for (String data : blockData) {
                        Chat.sendComponent(player, data);
                    }
                }

                statement.close();
            }
            else {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
    }

    private void exportContainerTransactions(Statement statement) {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.ITALIC + Phrase.build(Phrase.EXPORT_GENERATING));
        List<String> allContainerData = new ArrayList<>();
        int currentPage = 1;
        boolean dataFound = false;
        try {
            while (true) {
                // Note: ChestTransactionLookup already handles pagination internally somewhat differently.
                // We request pages until it returns an empty list or a list signifying the end.
                List<String> pageData = ChestTransactionLookup.performLookup(command.getName(), statement, location, player, currentPage, limit, true); // Set finalPage=true for export loop

                if (pageData == null || pageData.isEmpty() || (pageData.size() == 1 && pageData.get(0).contains(Phrase.build(Phrase.NO_RESULTS_PAGE)))) {
                     if (currentPage == 1 && !dataFound) { // Check if first page might just have header/footer
                        if (pageData != null && !pageData.isEmpty() && !pageData.get(0).contains(Phrase.build(Phrase.NO_RESULTS))) {
                           // Contains header/footer
                        } else {
                           break; // Truly empty
                        }
                    } else {
                        break; // No more data
                    }
                }
                dataFound = true;

                // Filter out header/footer/page navigation
                for (String line : pageData) {
                    if (!line.startsWith("---") && !line.contains("Page ")) { // Basic filtering
                        allContainerData.add(ChatColor.stripColor(line));
                    }
                }
                currentPage++;

                // Safety break
                if (currentPage > 1000) {
                     Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.RED + "Export limit reached (1000 pages).");
                    break;
                }
            }

            if (!dataFound) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
            }
            else {
                saveExport("container", String.join("\n", allContainerData));
            }
        }
        catch (Exception e) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.RED + Phrase.build(Phrase.EXPORT_ERROR));
            e.printStackTrace();
        }
    }

    private void saveExport(String exportType, String rawData) {
        // Ensure exports directory exists
        File exportDir = new File(plugin.getDataFolder(), "exports");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        // Generate filename
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = "export_" + timestamp + "_" + player.getName().replaceAll("[^a-zA-Z0-9_]", "") + "_" + exportType + ".json";
        File exportFile = new File(exportDir, filename);

        // Create JSON structure
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("type", exportType);
        exportData.put("raw_data", rawData);

        // Write JSON to file
        try (FileWriter writer = new FileWriter(exportFile)) {
            GSON.toJson(exportData, writer);
            int lineCount = rawData.isEmpty() ? 0 : rawData.split("\n").length;
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.EXPORT_SUCCESS, String.valueOf(lineCount), "exports/" + filename));
        }
        catch (IOException e) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.RED + Phrase.build(Phrase.EXPORT_FAILURE));
            e.printStackTrace();
        }
    }
}
