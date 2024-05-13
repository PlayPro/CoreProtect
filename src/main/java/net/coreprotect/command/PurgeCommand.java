package net.coreprotect.command;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.config.DatabaseType;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Database;
import net.coreprotect.database.StatementUtils;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.patch.Patch;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatMessage;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public class PurgeCommand extends Consumer {

    protected static void runCommand(final CommandSender player, boolean permission, String[] args) {
        int resultc = args.length;
        Location location = CommandHandler.parseLocation(player, args);
        final Integer[] argRadius = CommandHandler.parseRadius(args, player, location);
        final long[] argTime = CommandHandler.parseTime(args);
        final int argWid = CommandHandler.parseWorld(args, false, false);
        final List<Integer> argAction = CommandHandler.parseAction(args);
        final List<Integer> supportedActions = Arrays.asList();
        long startTime = argTime[1] > 0 ? argTime[0] : 0;
        long endTime = argTime[1] > 0 ? argTime[1] : argTime[0];

        if (ConfigHandler.converterRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
            return;
        }
        if (ConfigHandler.purgeRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
            return;
        }
        if (!permission) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            return;
        }
        if (resultc <= 1) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co purge t:<time>"));
            return;
        }
        if (endTime <= 0) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co purge t:<time>"));
            return;
        }
        if (argRadius != null) {
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.INVALID_WORLD)).build());
            return;
        }
        if (argWid == -1) {
            String worldName = CommandHandler.parseWorldName(args, false);
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, worldName)).build());
            return;
        }
        for (int action : argAction) {
            if (!supportedActions.contains(action)) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ACTION_NOT_SUPPORTED));
                // Functions.sendMessage(player, new ChatMessage("Please specify a valid purge action.").build());
                return;
            }
        }
        if (player instanceof Player && endTime < 2592000) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_MINIMUM_TIME, "30", Selector.FIRST)); // 30 days
            return;
        }
        else if (endTime < 86400) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_MINIMUM_TIME, "24", Selector.SECOND)); // 24 hours
            return;
        }

        boolean optimizeCheck = false;
        for (String arg : args) {
            if (arg.trim().equalsIgnoreCase("#optimize")) {
                optimizeCheck = true;
                break;
            }
        }
        final boolean optimize = optimizeCheck;

        class BasicThread implements Runnable {

            @Override
            public void run() {
                try {
                    long timestamp = (System.currentTimeMillis() / 1000L);
                    long timeStart = startTime > 0 ? (timestamp - startTime) : 0;
                    long timeEnd = timestamp - endTime;
                    long removed = 0;

                    Connection connection = null;
                    for (int i = 0; i <= 5; i++) {
                        connection = Database.getConnection(false, 500);
                        if (connection != null) {
                            break;
                        }
                        Thread.sleep(1000);
                    }

                    if (connection == null) {
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.DATABASE_BUSY));
                        return;
                    }

                    if (argWid > 0) {
                        String worldName = CommandHandler.parseWorldName(args, false);
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_STARTED, worldName));
                    }
                    else {
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_STARTED, "#global"));
                    }
                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_NOTICE_1));
                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_NOTICE_2));

                    ConfigHandler.purgeRunning = true;
                    while (!Consumer.pausedSuccess) {
                        Thread.sleep(1);
                    }
                    Consumer.isPaused = true;

                    boolean abort = false;
                    String purgePrefix = "tmp_" + ConfigHandler.prefix;

                    if (Config.getGlobal().DB_TYPE == DatabaseType.SQLITE) {
                        try (PreparedStatement ps = connection.prepareStatement("ATTACH DATABASE '" + ConfigHandler.path + ConfigHandler.sqlite + ".tmp' AS tmp_db")) {
                            ps.execute();
                        }
                        purgePrefix = "tmp_db." + ConfigHandler.prefix;
                    }

                    Integer[] lastVersion = Patch.getDatabaseVersion(connection, true);
                    boolean newVersion = Util.newVersion(lastVersion, Util.getInternalPluginVersion());
                    if (newVersion && !ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_FAILED));
                        Consumer.isPaused = false;
                        ConfigHandler.purgeRunning = false;
                        return;
                    }

                    if (Config.getGlobal().DB_TYPE == DatabaseType.SQLITE) {
                        for (String table : ConfigHandler.databaseTables) {
                            try (PreparedStatement ps = connection.prepareStatement("DROP TABLE IF EXISTS " + purgePrefix + table)) {
                                ps.execute();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        Database.createDatabaseTables(purgePrefix, true);
                    }

                    List<String> purgeTables = Arrays.asList("sign", "container", "item", "skull", "session", "chat", "command", "entity", "block");
                    List<String> worldTables = Arrays.asList("sign", "container", "item", "session", "chat", "command", "block");
                    List<String> excludeTables = Arrays.asList("database_lock"); // don't insert data into these tables
                    for (String table : ConfigHandler.databaseTables) {
                        String tableName = table.replaceAll("_", " ");
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_PROCESSING, tableName));

                        if (Config.getGlobal().DB_TYPE == DatabaseType.SQLITE) {
                            String columns = "";
                            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + purgePrefix + table);
                                    ResultSet rs = ps.executeQuery();) {
                                ResultSetMetaData resultSetMetaData = rs.getMetaData();
                                int columnCount = resultSetMetaData.getColumnCount();
                                for (int i = 1; i <= columnCount; i++) {
                                    String name = resultSetMetaData.getColumnName(i);
                                    if (columns.length() == 0) {
                                        columns = name;
                                    }
                                    else {
                                        columns = columns + "," + name;
                                    }
                                }
                            }

                            boolean error = false;
                            if (!excludeTables.contains(table)) {
                                try {
                                    String timeLimit = "";
                                    if (purgeTables.contains(table)) {
                                        if (argWid > 0 && worldTables.contains(table)) {
                                            timeLimit = " WHERE (wid = '" + argWid + "' AND (time >= '" + timeEnd + "' OR time < '" + timeStart + "')) OR wid != '" + argWid + "'";
                                        }
                                        else if (argWid == 0) {
                                            timeLimit = " WHERE (time >= '" + timeEnd + "' OR time < '" + timeStart + "')";
                                        }
                                    }
                                    try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + purgePrefix + table + " SELECT " + columns + " FROM " + StatementUtils.getTableName(table) + timeLimit)) {
                                        ps.execute();
                                    }
                                }
                                catch (Exception e) {
                                    error = true;
                                    e.printStackTrace();
                                }
                            }

                            if (error) {
                                Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_ERROR, tableName));
                                Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_REPAIRING));

                                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + purgePrefix + table)) {
                                    ps.execute();
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }

                                try (PreparedStatement ps = connection.prepareStatement("REINDEX " + StatementUtils.getTableName(table))) {
                                    ps.execute();
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }

                                try {
                                    String index = " NOT INDEXED";
                                    try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + purgePrefix + table + " SELECT " + columns + " FROM " + StatementUtils.getTableName(table) + index)) {
                                        ps.execute();
                                    }
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                    abort = true;
                                    break;
                                }

                                try {
                                    boolean purge = purgeTables.contains(table);

                                    String worldRestriction = "";
                                    if (argWid > 0 && worldTables.contains(table)) {
                                        worldRestriction = " AND wid = '" + argWid + "'";
                                    }
                                    else if (argWid > 0) {
                                        purge = false;
                                    }

                                    if (purge) {
                                        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + purgePrefix + table + " WHERE time < '" + timeEnd + "' AND time >= '" + timeStart + "'" + worldRestriction)) {
                                            ps.execute();
                                        }
                                    }
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            if (purgeTables.contains(table)) {
                                int oldCount = 0;
                                try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) as count FROM " + StatementUtils.getTableName(table) + " LIMIT 1");
                                        ResultSet resultSet = ps.executeQuery();) {
                                    while (resultSet.next()) {
                                        oldCount = resultSet.getInt("count");
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                int new_count = 0;
                                try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) as count FROM " + purgePrefix + table + " LIMIT 1");
                                        ResultSet resultSet = ps.executeQuery();) {
                                    while (resultSet.next()) {
                                        new_count = resultSet.getInt("count");
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                removed = removed + (oldCount - new_count);
                            }
                        }

                        if (Config.getGlobal().DB_TYPE != DatabaseType.SQLITE) {
                            try {
                                boolean purge = purgeTables.contains(table);

                                String worldRestriction = "";
                                if (argWid > 0 && worldTables.contains(table)) {
                                    worldRestriction = " AND wid = ?";
                                }
                                else if (argWid > 0) {
                                    purge = false;
                                }

                                if (purge) {
                                    try (PreparedStatement preparedStmt = connection.prepareStatement("DELETE FROM " + StatementUtils.getTableName(table) + " WHERE time < ? AND time >= ?" + worldRestriction)) {
                                        preparedStmt.setLong(1, timeEnd);
                                        preparedStmt.setLong(2, timeStart);
                                        if (!worldRestriction.isEmpty()) {
                                            preparedStmt.setInt(3, argWid);
                                        }
                                        preparedStmt.execute();
                                        removed = removed + preparedStmt.getUpdateCount();
                                    }
                                }
                            }
                            catch (Exception e) {
                                if (!ConfigHandler.serverRunning) {
                                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_FAILED));
                                    return;
                                }

                                e.printStackTrace();
                            }
                        }
                    }

                    if (optimize) {
                        switch (Config.getGlobal().DB_TYPE) {
                            case MYSQL: {
                                Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_OPTIMIZING));
                                for (String table : ConfigHandler.databaseTables) {
                                    try (PreparedStatement preparedStmt = connection.prepareStatement("OPTIMIZE LOCAL TABLE " + StatementUtils.getTableName(table))) {
                                        preparedStmt.execute();
                                    }
                                }
                                break;
                            }
                            case PGSQL: {
                                Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_OPTIMIZING));
                                for (String table : ConfigHandler.databaseTables) {
                                    try (PreparedStatement preparedStmt = connection.prepareStatement("VACUUM ANALYZE " + StatementUtils.getTableName(table))) {
                                        preparedStmt.execute();
                                    }
                                }
                                break;
                            }
                            default: {
                                // no optimization options for SQLite
                                break;
                            }
                        }
                    }

                    connection.close();

                    if (abort) {
                        if (Config.getGlobal().DB_TYPE == DatabaseType.SQLITE) {
                            (new File(ConfigHandler.path + ConfigHandler.sqlite + ".tmp")).delete();
                        }
                        ConfigHandler.loadDatabase();
                        Chat.sendGlobalMessage(player, Color.RED + Phrase.build(Phrase.PURGE_ABORTED));
                        Consumer.isPaused = false;
                        ConfigHandler.purgeRunning = false;
                        return;
                    }

                    if (Config.getGlobal().DB_TYPE == DatabaseType.SQLITE) {
                        (new File(ConfigHandler.path + ConfigHandler.sqlite)).delete();
                        (new File(ConfigHandler.path + ConfigHandler.sqlite + ".tmp")).renameTo(new File(ConfigHandler.path + ConfigHandler.sqlite));
                    }

                    ConfigHandler.loadDatabase();

                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_SUCCESS));
                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_ROWS, NumberFormat.getInstance().format(removed), (removed == 1 ? Selector.FIRST : Selector.SECOND)));
                }
                catch (Exception e) {
                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_FAILED));
                    e.printStackTrace();
                }

                Consumer.isPaused = false;
                ConfigHandler.purgeRunning = false;
            }
        }

        Runnable runnable = new BasicThread();
        Thread thread = new Thread(runnable);
        thread.start();
    }
}
