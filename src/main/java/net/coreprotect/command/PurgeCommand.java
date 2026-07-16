package net.coreprotect.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Database;
import net.coreprotect.database.PurgePolicy;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.patch.Patch;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatMessage;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.VersionUtils;
import net.coreprotect.utility.ErrorReporter;

public class PurgeCommand extends Consumer {

    private static final long CONNECTION_DRAIN_TIMEOUT_MILLIS = 60000L;
    private static volatile Thread activePurgeThread;
    private static volatile Statement activePurgeStatement;
    private static volatile boolean shutdownCancellationRequested;

    public static void resetShutdownCancellation() {
        shutdownCancellationRequested = false;
    }

    public static void cancelForShutdown() {
        shutdownCancellationRequested = true;
        Database.cancelClickHousePurge();
        Statement statement = activePurgeStatement;
        if (statement != null) {
            try {
                statement.cancel();
            }
            catch (Exception ignored) {
            }
        }

        Thread thread = activePurgeThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public static boolean isPurgeWorkerRunning() {
        Thread thread = activePurgeThread;
        return thread != null && thread.isAlive();
    }

    private static void requirePurgeNotCancelled() throws InterruptedException {
        if (shutdownCancellationRequested || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Purge interrupted during shutdown");
        }
    }

    private static PreparedStatement preparePurgeStatement(Connection connection, String query) throws SQLException, InterruptedException {
        requirePurgeNotCancelled();
        PreparedStatement statement = connection.prepareStatement(query);
        activePurgeStatement = statement;
        requirePurgeNotCancelled();
        return statement;
    }

    private static Statement createPurgeStatement(Connection connection) throws SQLException, InterruptedException {
        requirePurgeNotCancelled();
        Statement statement = connection.createStatement();
        activePurgeStatement = statement;
        requirePurgeNotCancelled();
        return statement;
    }

    private static void reportPurgeFailure(Exception exception) throws Exception {
        if (shutdownCancellationRequested || Thread.currentThread().isInterrupted()) {
            throw exception;
        }
        ErrorReporter.report(exception);
    }

    private static String findUnsupportedPurgeArgument(String[] args) {
        boolean includeContinuation = false;
        for (int i = 1; i < args.length; i++) {
            String token = args[i].trim();
            if (token.length() == 0) {
                continue;
            }

            String argument = token.toLowerCase(Locale.ROOT);
            argument = argument.replaceAll("\\\\", "");
            argument = argument.replaceAll("'", "");

            if (includeContinuation) {
                includeContinuation = argument.endsWith(",");
                continue;
            }

            if (argument.equals("#optimize")) {
                continue;
            }

            if (argument.startsWith("i:") || argument.startsWith("include:") || argument.startsWith("item:") || argument.startsWith("items:") || argument.startsWith("b:") || argument.startsWith("block:") || argument.startsWith("blocks:")) {
                String includeValues = argument.replaceAll("include:", "").replaceAll("i:", "").replaceAll("items:", "").replaceAll("item:", "").replaceAll("blocks:", "").replaceAll("block:", "").replaceAll("b:", "");
                includeContinuation = includeValues.length() == 0 || includeValues.endsWith(",");
                continue;
            }

            if (argument.startsWith("t:") || argument.startsWith("time:")) {
                continue;
            }

            if (argument.startsWith("r:") || argument.startsWith("radius:")) {
                continue;
            }

            if (argument.contains(":")) {
                return token;
            }
        }

        return null;
    }

    protected static void runCommand(final CommandSender player, boolean permission, String[] args) {
        int resultc = args.length;
        Location location = CommandParser.parseLocation(player, args);
        final Integer[] argRadius = CommandParser.parseRadius(args, player, location);
        final List<Integer> argAction = CommandParser.parseAction(args);
        final List<Object> argBlocks = CommandParser.parseRestricted(player, args, argAction);
        final Map<Object, Boolean> argExclude = CommandParser.parseExcluded(player, args, argAction);
        final List<String> argExcludeUsers = CommandParser.parseExcludedUsers(player, args);
        final long[] argTime = CommandParser.parseTime(args);
        final int argWid = CommandParser.parseWorld(args, false, false);
        final List<Integer> supportedActions = Arrays.asList();
        long startTime = argTime[1] > 0 ? argTime[0] : 0;
        long endTime = argTime[1] > 0 ? argTime[1] : argTime[0];

        if (argBlocks == null || argExclude == null || argExcludeUsers == null) {
            return;
        }

        if (ConfigHandler.converterRunning || ConfigHandler.migrationRunning) {
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
        String unsupportedArgument = findUnsupportedPurgeArgument(args);
        if (unsupportedArgument != null) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_PARAMETER, unsupportedArgument));
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co help purge"));
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
            String worldName = CommandParser.parseWorldName(args, false);
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, worldName)).build());
            return;
        }
        if (player instanceof Player && endTime < 2592000) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_MINIMUM_TIME, "30", Selector.FIRST)); // 30 days
            return;
        }
        else if (endTime < 86400) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_MINIMUM_TIME, "24", Selector.SECOND)); // 24 hours
            return;
        }
        for (int action : argAction) {
            if (!supportedActions.contains(action)) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ACTION_NOT_SUPPORTED));
                // Functions.sendMessage(player, new ChatMessage("Please specify a valid purge action.").build());
                return;
            }
        }

        StringBuilder restrict = new StringBuilder();
        String includeBlock = "";
        List<Integer> includeBlockIds = new ArrayList<>();
        String includeEntity = "";
        boolean hasBlock = false;
        boolean item = false;
        boolean entity = false;
        int restrictCount = 0;

        if (argBlocks.size() > 0) {
            StringBuilder includeListMaterial = new StringBuilder();
            StringBuilder includeListEntity = new StringBuilder();

            for (Object restrictTarget : argBlocks) {
                String targetName = "";

                if (restrictTarget instanceof Material) {
                    targetName = ((Material) restrictTarget).name();
                    int blockId = MaterialUtils.getBlockId(targetName, false);
                    includeBlockIds.add(blockId);
                    if (includeListMaterial.length() == 0) {
                        includeListMaterial = includeListMaterial.append(blockId);
                    }
                    else {
                        includeListMaterial.append(",").append(blockId);
                    }

                    /* Include legacy IDs */
                    int legacyId = BukkitAdapter.ADAPTER.getLegacyBlockId((Material) restrictTarget);
                    if (legacyId > 0) {
                        includeListMaterial.append(",").append(legacyId);
                        includeBlockIds.add(legacyId);
                    }

                    targetName = ((Material) restrictTarget).name().toLowerCase(Locale.ROOT);
                    item = (!item ? !(((Material) restrictTarget).isBlock()) : item);
                    hasBlock = true;
                }
                else if (restrictTarget instanceof EntityType) {
                    targetName = ((EntityType) restrictTarget).name();
                    if (includeListEntity.length() == 0) {
                        includeListEntity = includeListEntity.append(EntityUtils.getEntityId(targetName, false));
                    }
                    else {
                        includeListEntity.append(",").append(EntityUtils.getEntityId(targetName, false));
                    }

                    targetName = ((EntityType) restrictTarget).name().toLowerCase(Locale.ROOT);
                    entity = true;
                }
                else if (restrictTarget instanceof String) {
                    int blockId = MaterialUtils.getBlockId((String) restrictTarget, false);
                    includeBlockIds.add(blockId);
                    if (includeListMaterial.length() == 0) {
                        includeListMaterial = includeListMaterial.append(blockId);
                    }
                    else {
                        includeListMaterial.append(",").append(blockId);
                    }

                    targetName = ((String) restrictTarget).toLowerCase(Locale.ROOT);
                    hasBlock = true;
                }

                if (restrictCount == 0) {
                    restrict = restrict.append("" + targetName + "");
                }
                else {
                    restrict.append(", ").append(targetName);
                }

                restrictCount++;
            }

            includeBlock = includeListMaterial.toString();
            includeEntity = includeListEntity.toString();
        }

        if (entity) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ACTION_NOT_SUPPORTED));
            return;
        }

        boolean optimizeCheck = false;
        for (String arg : args) {
            if (arg.trim().equalsIgnoreCase("#optimize")) {
                optimizeCheck = true;
                break;
            }
        }

        final StringBuilder restrictTargets = restrict;
        final String includeBlockFinal = includeBlock;
        final List<Integer> includeBlockIdsFinal = List.copyOf(includeBlockIds);
        final boolean optimize = optimizeCheck;
        final boolean hasBlockRestriction = hasBlock;
        final int restrictCountFinal = restrictCount;

        class BasicThread implements Runnable {

            @Override
            public void run() {
                boolean purgeClaimed = false;
                boolean consumerPaused = false;
                boolean duckTransaction = false;
                boolean duckPurgeStarted = false;
                boolean duckRollbackSucceeded = false;
                boolean maintenanceLocked = false;
                boolean resumePersistence = true;
                boolean handoffStarted = false;
                Connection connection = null;
                Statement transactionStatement = null;
                try {
                    requirePurgeNotCancelled();
                    if (Consumer.isPersistenceHalted()) {
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.DATABASE_BUSY));
                        return;
                    }
                    long timestamp = (System.currentTimeMillis() / 1000L);
                    long timeStart = startTime > 0 ? (timestamp - startTime) : 0;
                    long timeEnd = timestamp - endTime;
                    long removed = 0;

                    for (int i = 0; i <= 5; i++) {
                        requirePurgeNotCancelled();
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

                    requirePurgeNotCancelled();
                    Consumer.OperationStartResult startResult = Consumer.claimPurge();
                    if (startResult != Consumer.OperationStartResult.STARTED) {
                        Phrase phrase = startResult == Consumer.OperationStartResult.PURGE_RUNNING ? Phrase.PURGE_IN_PROGRESS
                                : startResult == Consumer.OperationStartResult.RELOAD_RUNNING ? Phrase.DATABASE_BUSY
                                        : startResult == Consumer.OperationStartResult.PERSISTENCE_HALTED ? Phrase.DATABASE_PERSISTENCE_HALTED : Phrase.ROLLBACK_IN_PROGRESS;
                        Chat.sendGlobalMessage(player, Phrase.build(phrase));
                        try {
                            connection.close();
                        }
                        catch (Exception e) {
                            ErrorReporter.report(e);
                        }
                        return;
                    }
                    purgeClaimed = true;
                    activePurgeThread = Thread.currentThread();
                    requirePurgeNotCancelled();

                    if (argWid > 0) {
                        String worldName = CommandParser.parseWorldName(args, false);
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_STARTED, worldName));
                    }
                    else {
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_STARTED, "#global"));
                    }

                    if (hasBlockRestriction) {
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.ROLLBACK_INCLUDE, restrictTargets.toString(), Selector.FIRST, Selector.FIRST, (restrictCountFinal == 1 ? Selector.FIRST : Selector.SECOND))); // include
                    }

                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_NOTICE_1));
                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_NOTICE_2));

                    while (!Consumer.pausedSuccess && !Consumer.isPersistenceHalted()) {
                        Thread.sleep(1);
                    }
                    if (Consumer.isPersistenceHalted()) {
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.DATABASE_BUSY));
                        return;
                    }
                    Consumer.isPaused = true;
                    consumerPaused = true;
                    requirePurgeNotCancelled();

                    if (ConfigHandler.databaseType.isClickHouse()) {
                        connection.close();
                        connection = null;
                        if (optimize) {
                            Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_OPTIMIZING));
                        }
                        requirePurgeNotCancelled();
                        removed = Database.purgeClickHouse(timeStart, timeEnd, argWid, includeBlockIdsFinal, optimize);
                        EntitySpawnTracking.invalidateDatabaseVerification();
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_SUCCESS));
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_ROWS, NumberFormat.getInstance().format(removed), (removed == 1 ? Selector.FIRST : Selector.SECOND)));
                        return;
                    }

                    String query = "";
                    PreparedStatement preparedStmt = null;
                    boolean abort = false;
                    String purgePrefix = "tmp_" + ConfigHandler.prefix;

                    if (ConfigHandler.databaseType.isSQLite()) {
                        query = "ATTACH DATABASE '" + ConfigHandler.path + ConfigHandler.sqlite + ".tmp' AS tmp_db";
                        preparedStmt = preparePurgeStatement(connection, query);
                        preparedStmt.execute();
                        preparedStmt.close();
                        purgePrefix = "tmp_db." + ConfigHandler.prefix;
                    }

                    Integer[] lastVersion = Patch.getDatabaseVersion(connection, true);
                    boolean newVersion = VersionUtils.newVersion(lastVersion, VersionUtils.getInternalPluginVersion());
                    if (newVersion && !ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_FAILED));
                        return;
                    }

                    if (ConfigHandler.databaseType.isDuckDB()) {
                        transactionStatement = createPurgeStatement(connection);
                        Database.beginTransaction(transactionStatement, ConfigHandler.databaseType);
                        duckPurgeStarted = true;
                        duckTransaction = true;
                    }

                    if (ConfigHandler.databaseType.isSQLite()) {
                        for (String table : ConfigHandler.databaseTables) {
                            requirePurgeNotCancelled();
                            try {
                                query = "DROP TABLE IF EXISTS " + purgePrefix + table + "";
                                preparedStmt = preparePurgeStatement(connection, query);
                                preparedStmt.execute();
                                preparedStmt.close();
                            }
                            catch (Exception e) {
                                reportPurgeFailure(e);
                            }
                        }

                        Database.createDatabaseTables(purgePrefix, false, null, ConfigHandler.databaseType, true);
                    }

                    List<String> excludeTables = Arrays.asList("database_lock"); // don't insert data into these tables
                    for (String table : ConfigHandler.databaseTables) {
                        requirePurgeNotCancelled();
                        String tableName = table.replaceAll("_", " ");
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_PROCESSING, tableName));

                        if (ConfigHandler.databaseType.isSQLite()) {
                            String columns = "";
                            Statement columnStatement = createPurgeStatement(connection);
                            ResultSet rs = columnStatement.executeQuery("SELECT * FROM " + purgePrefix + table);
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
                            rs.close();
                            columnStatement.close();
                            String insertColumns = "";
                            String selectColumns = columns;
                            if (table.equals("block")) {
                                insertColumns = "(rowid," + columns + ")";
                                selectColumns = "rowid," + columns;
                            }

                            boolean error = false;
                            if (!excludeTables.contains(table)) {
                                try {
                                    boolean purge = true;
                                    String timeLimit = "";
                                    if (table.equals("entity_spawn")) {
                                        timeLimit = " WHERE removed=0 OR block_rowid IN(SELECT rowid FROM " + purgePrefix + "block) OR kill_rowid IN(SELECT rowid FROM " + purgePrefix + "entity) OR rowid IN(SELECT entity_spawn_rowid FROM " + purgePrefix + "entity_container) OR rowid IN(SELECT entity_spawn_rowid FROM " + purgePrefix + "entity_interaction)";
                                    }
                                    else if (PurgePolicy.isPurgeable(table)) {
                                        String blockRestriction = "(";
                                        if (hasBlockRestriction && PurgePolicy.supportsBlockRestriction(table)) {
                                            blockRestriction = "action IN(" + LookupActions.ENTITY_KILL + "," + LookupActions.ENTITY_SPAWN + ") OR type NOT IN(" + includeBlockFinal + ") OR (type IN(" + includeBlockFinal + ") AND ";
                                        }
                                        else if (hasBlockRestriction) {
                                            purge = false;
                                        }

                                        if (argWid > 0 && PurgePolicy.isWorldScoped(table)) {
                                            if (table.equals("entity_container") || table.equals("entity_interaction")) {
                                                if (purge) {
                                                    String worldMatch = "(wid = '" + argWid + "' OR entity_spawn_rowid IN(SELECT rowid FROM " + ConfigHandler.prefix + "entity_spawn WHERE current_wid = '" + argWid + "'))";
                                                    timeLimit = " WHERE (" + worldMatch + " AND (time >= '" + timeEnd + "' OR time < '" + timeStart + "')) OR NOT " + worldMatch;
                                                }
                                            }
                                            else {
                                                timeLimit = " WHERE (" + blockRestriction + "wid = '" + argWid + "' AND (time >= '" + timeEnd + "' OR time < '" + timeStart + "'))) OR (wid != '" + argWid + "')";
                                            }
                                        }
                                        else if (argWid == 0 && purge) {
                                            timeLimit = " WHERE " + blockRestriction + "(time >= '" + timeEnd + "' OR time < '" + timeStart + "'))";
                                        }
                                    }
                                    query = "INSERT INTO " + purgePrefix + table + insertColumns + " SELECT " + selectColumns + " FROM " + ConfigHandler.prefix + table + timeLimit;
                                    preparedStmt = preparePurgeStatement(connection, query);
                                    preparedStmt.execute();
                                    preparedStmt.close();
                                }
                                catch (Exception e) {
                                    error = true;
                                    reportPurgeFailure(e);
                                }
                            }

                            if (error) {
                                requirePurgeNotCancelled();
                                Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_ERROR, tableName));
                                Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_REPAIRING));

                                try {
                                    query = "DELETE FROM " + purgePrefix + table;
                                    preparedStmt = preparePurgeStatement(connection, query);
                                    preparedStmt.execute();
                                    preparedStmt.close();
                                }
                                catch (Exception e) {
                                    reportPurgeFailure(e);
                                }

                                try {
                                    query = "REINDEX " + ConfigHandler.prefix + table;
                                    preparedStmt = preparePurgeStatement(connection, query);
                                    preparedStmt.execute();
                                    preparedStmt.close();
                                }
                                catch (Exception e) {
                                    reportPurgeFailure(e);
                                }

                                try {
                                    String index = " NOT INDEXED";
                                    query = "INSERT INTO " + purgePrefix + table + insertColumns + " SELECT " + selectColumns + " FROM " + ConfigHandler.prefix + table + index;
                                    preparedStmt = preparePurgeStatement(connection, query);
                                    preparedStmt.execute();
                                    preparedStmt.close();
                                }
                                catch (Exception e) {
                                    reportPurgeFailure(e);
                                    abort = true;
                                    break;
                                }

                                try {
                                    boolean purge = PurgePolicy.isPurgeable(table);

                                    String blockRestriction = "";
                                    if (hasBlockRestriction && PurgePolicy.supportsBlockRestriction(table)) {
                                        blockRestriction = "action NOT IN(" + LookupActions.ENTITY_KILL + "," + LookupActions.ENTITY_SPAWN + ") AND type IN(" + includeBlockFinal + ") AND ";
                                    }
                                    else if (hasBlockRestriction) {
                                        purge = false;
                                    }

                                    String worldRestriction = "";
                                    if (argWid > 0 && PurgePolicy.isWorldScoped(table)) {
                                        if (table.equals("entity_container") || table.equals("entity_interaction")) {
                                            worldRestriction = " AND (wid = '" + argWid + "' OR entity_spawn_rowid IN(SELECT rowid FROM " + ConfigHandler.prefix + "entity_spawn WHERE current_wid = '" + argWid + "'))";
                                        }
                                        else {
                                            worldRestriction = " AND wid = '" + argWid + "'";
                                        }
                                    }
                                    else if (argWid > 0) {
                                        purge = false;
                                    }

                                    if (purge) {
                                        query = "DELETE FROM " + purgePrefix + table + " WHERE " + blockRestriction + "time < '" + timeEnd + "' AND time >= '" + timeStart + "'" + worldRestriction;
                                        preparedStmt = preparePurgeStatement(connection, query);
                                        preparedStmt.execute();
                                        preparedStmt.close();
                                    }
                                }
                                catch (Exception e) {
                                    reportPurgeFailure(e);
                                }
                            }

                            if (PurgePolicy.isPurgeable(table) || table.equals("entity_spawn")) {
                                int oldCount = 0;
                                try {
                                    query = "SELECT COUNT(*) as count FROM " + ConfigHandler.prefix + table + " LIMIT 1 OFFSET 0";
                                    preparedStmt = preparePurgeStatement(connection, query);
                                    ResultSet resultSet = preparedStmt.executeQuery();
                                    while (resultSet.next()) {
                                        oldCount = resultSet.getInt("count");
                                    }
                                    resultSet.close();
                                    preparedStmt.close();
                                }
                                catch (Exception e) {
                                    reportPurgeFailure(e);
                                }

                                int new_count = 0;
                                try {
                                    query = "SELECT COUNT(*) as count FROM " + purgePrefix + table + " LIMIT 1 OFFSET 0";
                                    preparedStmt = preparePurgeStatement(connection, query);
                                    ResultSet resultSet = preparedStmt.executeQuery();
                                    while (resultSet.next()) {
                                        new_count = resultSet.getInt("count");
                                    }
                                    resultSet.close();
                                    preparedStmt.close();
                                }
                                catch (Exception e) {
                                    reportPurgeFailure(e);
                                }

                                removed = removed + (oldCount - new_count);
                            }
                        }

                        if (!ConfigHandler.databaseType.isSQLite()) {
                            try {
                                boolean purge = PurgePolicy.isPurgeable(table);

                                String blockRestriction = "";
                                if (hasBlockRestriction && PurgePolicy.supportsBlockRestriction(table)) {
                                    blockRestriction = "action NOT IN(" + LookupActions.ENTITY_KILL + "," + LookupActions.ENTITY_SPAWN + ") AND type IN(" + includeBlockFinal + ") AND ";
                                }
                                else if (hasBlockRestriction) {
                                    purge = false;
                                }

                                String worldRestriction = "";
                                if (argWid > 0 && PurgePolicy.isWorldScoped(table)) {
                                    if (table.equals("entity_container") || table.equals("entity_interaction")) {
                                        worldRestriction = " AND (wid = '" + argWid + "' OR entity_spawn_rowid IN(SELECT rowid FROM " + ConfigHandler.prefix + "entity_spawn WHERE current_wid = '" + argWid + "'))";
                                    }
                                    else {
                                        worldRestriction = " AND wid = '" + argWid + "'";
                                    }
                                }
                                else if (argWid > 0) {
                                    purge = false;
                                }

                                if (purge) {
                                    query = "DELETE FROM " + ConfigHandler.prefix + table + " WHERE " + blockRestriction + "time < '" + timeEnd + "' AND time >= '" + timeStart + "'" + worldRestriction;
                                    preparedStmt = preparePurgeStatement(connection, query);
                                    removed = removed + preparedStmt.executeUpdate();
                                    preparedStmt.close();
                                }
                            }
                            catch (Exception e) {
                                if (ConfigHandler.databaseType.isDuckDB()) {
                                    throw e;
                                }
                                if (!ConfigHandler.serverRunning) {
                                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_FAILED));
                                    return;
                                }

                                reportPurgeFailure(e);
                            }
                        }
                    }

                    requirePurgeNotCancelled();
                    String retainedPrefix = ConfigHandler.databaseType.isSQLite() ? purgePrefix : ConfigHandler.prefix;
                    query = "UPDATE " + retainedPrefix + "entity_spawn SET kill_rowid=NULL WHERE kill_rowid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM " + retainedPrefix + "entity WHERE " + retainedPrefix + "entity.rowid=" + retainedPrefix + "entity_spawn.kill_rowid)";
                    preparedStmt = preparePurgeStatement(connection, query);
                    preparedStmt.executeUpdate();
                    preparedStmt.close();

                    requirePurgeNotCancelled();
                    query = "UPDATE " + retainedPrefix + "entity_spawn SET block_rowid=NULL WHERE block_rowid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM " + retainedPrefix + "block WHERE " + retainedPrefix + "block.rowid=" + retainedPrefix + "entity_spawn.block_rowid)";
                    preparedStmt = preparePurgeStatement(connection, query);
                    preparedStmt.executeUpdate();
                    preparedStmt.close();

                    requirePurgeNotCancelled();
                    query = "DELETE FROM " + retainedPrefix + "entity_interaction WHERE NOT EXISTS (SELECT 1 FROM " + retainedPrefix + "entity_spawn WHERE " + retainedPrefix + "entity_spawn.rowid=" + retainedPrefix + "entity_interaction.entity_spawn_rowid)";
                    preparedStmt = preparePurgeStatement(connection, query);
                    removed = removed + preparedStmt.executeUpdate();
                    preparedStmt.close();

                    requirePurgeNotCancelled();
                    query = "DELETE FROM " + retainedPrefix + "entity_container WHERE NOT EXISTS (SELECT 1 FROM " + retainedPrefix + "entity_spawn WHERE " + retainedPrefix + "entity_spawn.rowid=" + retainedPrefix + "entity_container.entity_spawn_rowid)";
                    preparedStmt = preparePurgeStatement(connection, query);
                    removed = removed + preparedStmt.executeUpdate();
                    preparedStmt.close();

                    requirePurgeNotCancelled();
                    query = "DELETE FROM " + retainedPrefix + "entity_spawn WHERE removed=1 AND block_rowid IS NULL AND kill_rowid IS NULL AND NOT EXISTS (SELECT 1 FROM " + retainedPrefix + "entity_container WHERE " + retainedPrefix + "entity_container.entity_spawn_rowid=" + retainedPrefix + "entity_spawn.rowid) AND NOT EXISTS (SELECT 1 FROM " + retainedPrefix + "entity_interaction WHERE " + retainedPrefix + "entity_interaction.entity_spawn_rowid=" + retainedPrefix + "entity_spawn.rowid)";
                    preparedStmt = preparePurgeStatement(connection, query);
                    removed = removed + preparedStmt.executeUpdate();
                    preparedStmt.close();

                    if (ConfigHandler.databaseType.isMySQL() && optimize) {
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_OPTIMIZING));
                        for (String table : ConfigHandler.databaseTables) {
                            requirePurgeNotCancelled();
                            query = "OPTIMIZE LOCAL TABLE " + ConfigHandler.prefix + table + "";
                            preparedStmt = preparePurgeStatement(connection, query);
                            preparedStmt.execute();
                            preparedStmt.close();
                        }
                    }

                    if (duckTransaction) {
                        activePurgeStatement = transactionStatement;
                        requirePurgeNotCancelled();
                        if (!Database.commitTransactionChecked(transactionStatement, ConfigHandler.databaseType)) {
                            throw new SQLException("Unable to commit DuckDB purge transaction");
                        }
                        duckTransaction = false;
                        duckPurgeStarted = false;
                        requirePurgeNotCancelled();
                        try {
                            transactionStatement.execute("CHECKPOINT");
                        }
                        catch (SQLException e) {
                            if (!shutdownCancellationRequested) {
                                ErrorReporter.report(e);
                            }
                        }
                        transactionStatement.close();
                        transactionStatement = null;
                    }

                    connection.close();
                    connection = null;
                    requirePurgeNotCancelled();

                    try {
                        Consumer.lockDatabaseMaintenanceInterruptibly();
                        maintenanceLocked = true;
                        if (!Database.awaitConnectionDrain(CONNECTION_DRAIN_TIMEOUT_MILLIS)) {
                            throw new SQLException("Timed out waiting for active database connections before purge handoff");
                        }
                        requirePurgeNotCancelled();
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw exception;
                    }

                    if (abort) {
                        if (ConfigHandler.databaseType.isSQLite()) {
                            Files.deleteIfExists(sqliteTempDatabase());
                        }
                        resumePersistence = false;
                        handoffStarted = true;
                        ConfigHandler.loadDatabase();
                        handoffStarted = false;
                        resumePersistence = true;
                        Chat.sendGlobalMessage(player, Color.RED + Phrase.build(Phrase.PURGE_ABORTED));
                        return;
                    }

                    resumePersistence = false;
                    handoffStarted = true;
                    if (ConfigHandler.databaseType.isSQLite()) {
                        replaceSQLiteDatabase();
                    }

                    ConfigHandler.loadDatabase();
                    handoffStarted = false;
                    resumePersistence = true;
                    EntitySpawnTracking.invalidateDatabaseVerification();

                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_SUCCESS));
                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_ROWS, NumberFormat.getInstance().format(removed), (removed == 1 ? Selector.FIRST : Selector.SECOND)));
                }
                catch (Exception e) {
                    boolean shutdownCancelled = shutdownCancellationRequested || e instanceof InterruptedException;
                    if (duckTransaction && transactionStatement != null) {
                        duckRollbackSucceeded = Database.rollbackTransaction(transactionStatement, ConfigHandler.databaseType);
                    }
                    if (ConfigHandler.databaseType.isDuckDB() && duckPurgeStarted && !duckRollbackSucceeded) {
                        Consumer.haltPersistence();
                    }
                    if (handoffStarted) {
                        Consumer.requireDatabaseReload();
                    }
                    if (shutdownCancelled) {
                        Thread.currentThread().interrupt();
                    }
                    else {
                        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_FAILED));
                        if (handoffStarted) {
                            Chat.sendGlobalMessage(player, Phrase.build(Phrase.RELOAD_FAILED));
                        }
                        ErrorReporter.report(e);
                    }
                }
                finally {
                    if (transactionStatement != null) {
                        try {
                            transactionStatement.close();
                        }
                        catch (Exception e) {
                            ErrorReporter.report(e);
                        }
                    }
                    if (connection != null) {
                        try {
                            connection.close();
                        }
                        catch (Exception e) {
                            ErrorReporter.report(e);
                        }
                    }
                    if (maintenanceLocked) {
                        Consumer.unlockDatabaseMaintenance();
                    }
                    if (consumerPaused && resumePersistence && !Consumer.isPersistenceHalted()) {
                        Consumer.isPaused = false;
                    }
                    if (purgeClaimed) {
                        Consumer.releasePurge();
                    }
                    if (activePurgeThread == Thread.currentThread()) {
                        activePurgeStatement = null;
                        activePurgeThread = null;
                    }
                }
            }
        }

        Runnable runnable = new BasicThread();
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private static Path sqliteTempDatabase() {
        return Path.of(ConfigHandler.path + ConfigHandler.sqlite + ".tmp");
    }

    private static void replaceSQLiteDatabase() throws IOException {
        Path database = Path.of(ConfigHandler.path + ConfigHandler.sqlite);
        Path temporary = sqliteTempDatabase();
        Files.move(temporary, database, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
