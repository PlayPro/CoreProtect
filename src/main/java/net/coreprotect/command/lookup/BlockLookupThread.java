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

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.BlockLookup;
import net.coreprotect.database.lookup.InteractionLookup;
import net.coreprotect.database.lookup.SignMessageLookup;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class BlockLookupThread implements Runnable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final CoreProtect plugin;
    private final CommandSender player;
    private final Command command;
    private final Block block;
    private final BlockState blockState;
    private final int page;
    private final int limit;
    private final int type;
    private final boolean exportMode;

    public BlockLookupThread(CoreProtect plugin, CommandSender player, Command command, Block block, BlockState blockState, int page, int limit, int type, boolean exportMode) {
        this.plugin = plugin;
        this.player = player;
        this.command = command;
        this.block = block;
        this.blockState = blockState;
        this.page = page;
        this.limit = limit;
        this.type = type;
        this.exportMode = exportMode;
    }

    @Override
    public void run() {
        try (Connection connection = Database.getConnection(true)) {
            ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });
            if (connection != null) {
                Statement statement = connection.createStatement();
                if (type == 8) {
                    if (exportMode) {
                        exportSignMessages(statement);
                    }
                    else {
                        java.util.List<String> signData = SignMessageLookup.performLookup(command.getName(), statement, blockState.getLocation(), player, page, limit);
                        for (String signMessage : signData) {
                            String bypass = null;

                            if (signMessage.contains("\n")) {
                                String[] split = signMessage.split("\n");
                                signMessage = split[0];
                                bypass = split[1];
                            }

                            if (signMessage.length() > 0) {
                                Chat.sendComponent(player, signMessage, bypass);
                            }
                        }
                    }
                }
                else {
                    if (exportMode) {
                        exportBlockInteractions(statement);
                    }
                    else {
                        String blockdata = null;
                        if (type == 7) {
                            blockdata = InteractionLookup.performLookup(command.getName(), statement, block, player, 0, page, limit);
                        }
                        else {
                            blockdata = BlockLookup.performLookup(command.getName(), statement, blockState, player, 0, page, limit);
                        }
                        if (blockdata.contains("\n")) {
                            for (String b : blockdata.split("\n")) {
                                Chat.sendComponent(player, b);
                            }
                        }
                        else if (blockdata.length() > 0) {
                            Chat.sendComponent(player, blockdata);
                        }
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

    private void exportSignMessages(Statement statement) {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.ITALIC + Phrase.build(Phrase.EXPORT_GENERATING));
        List<String> allSignData = new ArrayList<>();
        int currentPage = 1;
        boolean dataFound = false;
        try {
            while (true) {
                List<String> pageData = SignMessageLookup.performLookup(command.getName(), statement, blockState.getLocation(), player, currentPage, limit);
                if (pageData == null || pageData.isEmpty() || (pageData.size() == 1 && pageData.get(0).isEmpty())) {
                    if (currentPage == 1 && !dataFound) {
                        pageData = SignMessageLookup.performLookup(command.getName(), statement, blockState.getLocation(), player, currentPage, limit);
                        if (pageData == null || pageData.isEmpty() || (pageData.size() == 1 && pageData.get(0).isEmpty())) {
                            break;
                        }
                    } else {
                         break;
                    }
                }
                dataFound = true;

                for (String line : pageData) {
                    if (!line.startsWith("---") && !line.contains("Page ")) {
                        allSignData.add(ChatColor.stripColor(line.replace("\n", " | ")));
                    }
                }
                currentPage++;

                if (currentPage > 1000) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.RED + "Export limit reached (1000 pages).");
                    break;
                }
            }

            if (!dataFound) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
            }
            else {
                saveExport("sign", String.join("\n", allSignData));
            }
        }
        catch (Exception e) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.RED + Phrase.build(Phrase.EXPORT_ERROR));
            e.printStackTrace();
        }
    }

    private void exportBlockInteractions(Statement statement) {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.ITALIC + Phrase.build(Phrase.EXPORT_GENERATING));
        List<String> allBlockData = new ArrayList<>();
        int currentPage = 1;
        boolean dataFound = false;
        try {
            while (true) {
                String blockdataPage = null;
                if (type == 7) {
                    blockdataPage = InteractionLookup.performLookup(command.getName(), statement, block, player, 0, currentPage, limit);
                }
                else {
                    blockdataPage = BlockLookup.performLookup(command.getName(), statement, blockState, player, 0, currentPage, limit);
                }

                if (blockdataPage == null || blockdataPage.isEmpty() || blockdataPage.contains(Phrase.build(Phrase.NO_RESULTS)) || blockdataPage.contains(Phrase.build(Phrase.NO_RESULTS_PAGE))) {
                     if (currentPage == 1 && !dataFound) {
                         if (blockdataPage != null && !blockdataPage.isEmpty() && !blockdataPage.contains(Phrase.build(Phrase.NO_RESULTS))) {
                         } else {
                            break;
                         }
                    } else {
                        break;
                    }
                }
                dataFound = true;

                String[] lines = blockdataPage.split("\n");
                for (String line : lines) {
                    if (!line.startsWith("---") && !line.contains("Page ")) {
                       allBlockData.add(ChatColor.stripColor(line));
                    }
                }
                currentPage++;

                if (currentPage > 1000) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.RED + "Export limit reached (1000 pages).");
                    break;
                }
            }

            if (!dataFound) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
            }
            else {
                saveExport(type == 7 ? "interaction" : "block", String.join("\n", allBlockData));
            }
        }
        catch (Exception e) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.RED + Phrase.build(Phrase.EXPORT_ERROR));
            e.printStackTrace();
        }
    }

    private void saveExport(String exportType, String rawData) {
        File exportDir = new File(plugin.getDataFolder(), "exports");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = "export_" + timestamp + "_" + player.getName().replaceAll("[^a-zA-Z0-9_]", "") + "_" + exportType + ".json";
        File exportFile = new File(exportDir, filename);

        Map<String, Object> exportData = new HashMap<>();
        exportData.put("type", exportType);
        exportData.put("raw_data", rawData);

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
