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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.command.parser.LocationParser;
import net.coreprotect.command.parser.MaterialParser;
import net.coreprotect.command.parser.ParserUtils;
import net.coreprotect.command.parser.TimeParser;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Database;
import net.coreprotect.database.PurgeFilter;
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
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

public class PurgeCommand {

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
        return registerPurgeStatement(connection.prepareStatement(query));
    }

    private static Statement createPurgeStatement(Connection connection) throws SQLException, InterruptedException {
        requirePurgeNotCancelled();
        return registerPurgeStatement(connection.createStatement());
    }

    private static <T extends Statement> T registerPurgeStatement(T statement) throws InterruptedException {
        activePurgeStatement = statement;
        try {
            requirePurgeNotCancelled();
            return statement;
        }
        catch (InterruptedException interrupted) {
            try {
                statement.close();
            }
            catch (SQLException closeFailure) {
                interrupted.addSuppressed(closeFailure);
            }
            finally {
                activePurgeStatement = null;
            }
            throw interrupted;
        }
    }

    private static void executePurgeStatement(Connection connection, String query) throws SQLException, InterruptedException {
        try (PreparedStatement statement = preparePurgeStatement(connection, query)) {
            statement.execute();
        }
    }

    private static long executePurgeUpdate(Connection connection, String query) throws SQLException, InterruptedException {
        try (PreparedStatement statement = preparePurgeStatement(connection, query)) {
            return statement.executeUpdate();
        }
        finally {
            activePurgeStatement = null;
        }
    }

    static long countPurgeRows(Connection connection, String table) throws SQLException, InterruptedException {
        try (PreparedStatement statement = preparePurgeStatement(connection, "SELECT COUNT(*) AS count FROM " + table);
                ResultSet results = statement.executeQuery()) {
            return results.next() ? results.getLong("count") : 0;
        }
    }

    private static void reportPurgeFailure(Exception exception) throws Exception {
        if (shutdownCancellationRequested || Thread.currentThread().isInterrupted()) {
            throw exception;
        }
        ErrorReporter.report(exception);
    }

    static String findUnsupportedPurgeArgument(String[] args) {
        boolean listContinuation = false;
        String emptyList = null; // an include or exclude argument that has no value yet
        boolean hasRadius = false;
        String coordinateArgument = null;
        for (int i = 1; i < args.length; i++) {
            String token = args[i].trim();
            if (token.length() == 0) {
                continue;
            }

            String argument = ParserUtils.normalize(token);

            if (argument.equals("#optimize")) {
                continue;
            }

            String listValues = MaterialParser.stripIncludePrefix(argument);
            if (listValues == null) {
                listValues = MaterialParser.stripExcludePrefix(argument);
            }

            if (listValues != null) {
                if (emptyList != null) {
                    return emptyList;
                }
                listContinuation = listValues.length() == 0 || listValues.endsWith(",");
                emptyList = hasListValue(listValues) ? null : token;
                continue;
            }

            if (listContinuation && isListValue(argument)) {
                listContinuation = argument.endsWith(",");
                if (hasListValue(argument)) {
                    emptyList = null;
                }
                continue;
            }
            // The radius, world and action parsers read a key such as r: or a: even inside a list
            listContinuation = false;

            int valueStart = argument.indexOf(':') + 1;
            if (valueStart == 0) {
                return token;
            }

            String prefix = argument.substring(0, valueStart);
            String value = argument.substring(valueStart);
            if (value.isEmpty() && i + 1 < args.length) {
                value = ParserUtils.normalize(args[++i]);
                if (!value.startsWith("#") && value.indexOf(':') >= 0) {
                    return token;
                }
            }
            if (value.isEmpty()) {
                return token;
            }

            switch (prefix) {
                case "a:":
                case "action:":
                    if (!CommandParser.parseAction(new String[] { "purge", prefix + value }).equals(Collections.singletonList(LookupActions.ENTITY_KILL))) {
                        return token;
                    }
                    break;
                case "t:":
                case "time:":
                    if (!TimeParser.isValidDuration(value)) {
                        return token;
                    }
                    break;
                case "r:":
                case "radius:":
                    if (hasRadius) {
                        return token;
                    }
                    hasRadius = true;
                    break;
                default:
                    if (LocationParser.stripCoordinatePrefix(prefix) == null || !LocationParser.isValidBlockCoordinates(value)) {
                        return token;
                    }
                    coordinateArgument = token;
                    break;
            }
        }

        if (coordinateArgument != null && (!hasRadius || LocationParser.parseRadius(args, null, new Location(null, 0, 0, 0)) == null)) {
            return coordinateArgument;
        }

        // An empty list would silently purge without that include or exclude restriction
        return emptyList;
    }

    private static boolean hasListValue(String values) {
        return values.replace(",", "").length() > 0;
    }

    private static boolean isListValue(String argument) {
        int separator = argument.indexOf(':');
        return separator < 0 || argument.substring(0, separator).equals("minecraft");
    }

    protected static void runCommand(final CommandSender player, boolean permission, String[] args) {
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
        if (args.length <= 1) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co purge t:<time>"));
            return;
        }
        String unsupportedArgument = findUnsupportedPurgeArgument(args);
        if (unsupportedArgument != null) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_PARAMETER, unsupportedArgument));
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co help purge"));
            return;
        }

        Location location = CommandParser.parseLocation(player, args);
        final Integer[] argRadius = LocationParser.parseRadius(args, player, location != null ? location : new Location(null, 0, 0, 0));
        final List<Integer> argAction = CommandParser.parseAction(args);
        final List<Object> argBlocks = CommandParser.parseRestricted(player, args, argAction);
        final Map<Object, Boolean> argExclude = CommandParser.parseExcluded(player, args, argAction);
        final List<String> argExcludeUsers = CommandParser.parseExcludedUsers(player, args);
        final long[] argTime = TimeParser.parsePurgeTime(args);
        final int argWid = CommandParser.parseWorld(args, false, false);
        final List<Integer> supportedActions = List.of(LookupActions.ENTITY_KILL);
        long startTime = argTime[0];
        long endTime = argTime[1];

        if (argBlocks == null || argExclude == null || argExcludeUsers == null) {
            return;
        }
        if (startTime == 0L && endTime == 0L) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co purge t:<time>"));
            return;
        }
        if (CommandParser.parseWorldEdit(args)) {
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.INVALID_WORLD)).build());
            return;
        }
        if (argRadius != null && argRadius[0] == -1) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_RADIUS));
            return;
        }
        int maxRadius = Config.getGlobal().MAX_RADIUS;
        if (argRadius != null && maxRadius > 0 && argRadius[0] > maxRadius) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MAXIMUM_PURGE_RADIUS, Integer.toString(maxRadius)));
            return;
        }
        if (argRadius != null && (location == null || location.getWorld() == null)) {
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
        List<Integer> includeBlockIds = new ArrayList<>();
        List<Integer> includeEntityIds = new ArrayList<>();
        boolean hasBlock = false;
        boolean entity = false;
        int restrictCount = 0;

        if (argBlocks.size() > 0) {
            for (Object restrictTarget : argBlocks) {
                String targetName = "";

                if (restrictTarget instanceof Material) {
                    targetName = ((Material) restrictTarget).name();
                    int blockId = MaterialUtils.getBlockId(targetName, false);
                    includeBlockIds.add(blockId);

                    /* Include legacy IDs */
                    int legacyId = BukkitAdapter.ADAPTER.getLegacyBlockId((Material) restrictTarget);
                    if (legacyId > 0) {
                        includeBlockIds.add(legacyId);
                    }

                    targetName = ((Material) restrictTarget).name().toLowerCase(Locale.ROOT);
                    hasBlock = true;
                }
                else if (restrictTarget instanceof EntityType) {
                    includeEntityIds.addAll(EntityUtils.getEntityIds((EntityType) restrictTarget));
                    targetName = ((EntityType) restrictTarget).name().toLowerCase(Locale.ROOT);
                    entity = true;
                }
                else if (restrictTarget instanceof String) {
                    int blockId = MaterialUtils.getBlockId((String) restrictTarget, false);
                    includeBlockIds.add(blockId);

                    targetName = ((String) restrictTarget).toLowerCase(Locale.ROOT);
                    hasBlock = true;
                }

                if (restrictCount == 0) {
                    restrict.append(targetName);
                }
                else {
                    restrict.append(", ").append(targetName);
                }

                restrictCount++;
            }
        }

        List<Integer> excludeEntityIds = new ArrayList<>();
        StringBuilder exclude = new StringBuilder();
        for (Object excludeTarget : argExclude.keySet()) {
            if (!(excludeTarget instanceof EntityType)) {
                // Purge exclusions only support entity types (kills to keep).
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_PARAMETER, "e:" + excludeTarget.toString().toLowerCase(Locale.ROOT)));
                return;
            }
            excludeEntityIds.addAll(EntityUtils.getEntityIds((EntityType) excludeTarget));
            exclude.append(exclude.length() == 0 ? "" : ", ").append(((EntityType) excludeTarget).name().toLowerCase(Locale.ROOT));
        }
        if (!argExcludeUsers.isEmpty()) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_PARAMETER, "e:" + argExcludeUsers.get(0)));
            return;
        }

        boolean killsOnly = argAction.contains(LookupActions.ENTITY_KILL);
        boolean entityFilter = entity || killsOnly || !excludeEntityIds.isEmpty();
        boolean excludeWithoutKills = !excludeEntityIds.isEmpty() && hasBlock && !entity; // a block restriction keeps every kill
        if ((killsOnly && hasBlock) || (entity && !excludeEntityIds.isEmpty()) || excludeWithoutKills || ((entityFilter || argRadius != null) && ConfigHandler.databaseType.isClickHouse())) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ACTION_NOT_SUPPORTED));
            return;
        }
        if (argRadius != null && !hasBlock && !entity && !killsOnly) {
            // A radius only limits co_block rows, so the purge must be restricted to them
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co purge t:<time> r:<radius> i:<include>"));
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
        final StringBuilder excludeTargets = exclude;
        final List<Integer> includeBlockIdsFinal = List.copyOf(includeBlockIds);
        final List<Integer> includeEntityIdsFinal = List.copyOf(includeEntityIds);
        final List<Integer> excludeEntityIdsFinal = List.copyOf(excludeEntityIds);
        final boolean killsOnlyFinal = killsOnly;
        final boolean optimize = optimizeCheck;
        final int restrictCountFinal = restrictCount;
        final int purgeWorldId = argRadius != null ? WorldUtils.getWorldId(location.getWorld().getName()) : argWid;
        if (argRadius != null && purgeWorldId <= 0) {
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.INVALID_WORLD)).build());
            return;
        }
        String restrictSelector = Selector.FIRST; // block
        if (hasBlock && entity) {
            restrictSelector = Selector.THIRD; // target
        }
        else if (entity) {
            restrictSelector = Selector.SECOND; // entity
        }
        final String restrictKind = restrictSelector;

        long now = System.currentTimeMillis() / 1000L;
        PurgeFilter filter = new PurgeFilter(startTime > 0 ? now - startTime : 0, now - endTime, purgeWorldId,
                argRadius, includeBlockIdsFinal, includeEntityIdsFinal, excludeEntityIdsFinal, killsOnlyFinal);
        String worldName = argRadius != null ? location.getWorld().getName()
                : argWid > 0 ? CommandParser.parseWorldName(args, false) : "#global";
        Runnable announce = () -> announcePurge(player, worldName, argRadius, restrictCountFinal, restrictTargets.toString(),
                restrictKind, excludeEntityIdsFinal, excludeTargets.toString());
        new Thread(() -> runPurge(player, filter, optimize, announce)).start();
    }

    private static void runPurge(CommandSender player, PurgeFilter filter, boolean optimize, Runnable announce) {
        boolean purgeClaimed = false;
        boolean consumerPaused = false;
        boolean duckTransaction = false;
        boolean duckPurgeStarted = false;
        AtomicBoolean duckCommitAttempted = new AtomicBoolean();
        boolean duckRollbackSucceeded = false;
        boolean maintenanceLocked = false;
        boolean resumePersistence = true;
        boolean handoffStarted = false;
        Connection connection = null;
        Statement transactionStatement = null;
        Path sqliteTemporary = null;
        try {
            requirePurgeNotCancelled();
            if (Consumer.isPersistenceHalted()) {
                Chat.sendGlobalMessage(player, Phrase.build(Phrase.DATABASE_BUSY));
                return;
            }
            long removed;

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
                return;
            }
            purgeClaimed = true;
            activePurgeThread = Thread.currentThread();
            requirePurgeNotCancelled();

            announce.run();

            while (!Consumer.isPauseAcknowledged() && !Consumer.isPersistenceHalted()) {
                Thread.sleep(1);
            }
            if (Consumer.isPersistenceHalted()) {
                Chat.sendGlobalMessage(player, Phrase.build(Phrase.DATABASE_BUSY));
                return;
            }
            Consumer.setMaintenancePaused(true);
            consumerPaused = true;
            requirePurgeNotCancelled();

            if (ConfigHandler.databaseType.isClickHouse()) {
                connection.close();
                connection = null;
                removed = purgeClickHouse(player, filter, optimize);
                EntitySpawnTracking.invalidateDatabaseVerification();
                Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_SUCCESS));
                Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_ROWS, NumberFormat.getInstance().format(removed), (removed == 1 ? Selector.FIRST : Selector.SECOND)));
                return;
            }

            boolean abort = false;
            String retainedPrefix = ConfigHandler.prefix;

            if (ConfigHandler.databaseType.isSQLite()) {
                sqliteTemporary = sqliteTempDatabase();
                attachSQLitePurgeDatabase(connection, sqliteTemporary);
                retainedPrefix = "tmp_db." + ConfigHandler.prefix;
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
                SQLiteRebuild rebuild = rebuildSQLiteTables(connection, player, filter, retainedPrefix);
                removed = rebuild.removed;
                abort = rebuild.aborted;
            }
            else {
                Long deleted = deleteRelationalRows(connection, player, filter);
                if (deleted == null) {
                    return;
                }
                removed = deleted;
            }

            if (ConfigHandler.databaseType.isMySQL() && optimize) {
                removed += sweepMySqlOrphans(connection, filter);
            }
            if (!abort) {
                removed += deleteOrphanedEntityRows(connection, retainedPrefix, filter);
            }
            if (ConfigHandler.databaseType.isMySQL() && optimize) {
                optimizeMySqlTables(connection, player);
            }

            if (duckTransaction) {
                activePurgeStatement = transactionStatement;
                requirePurgeNotCancelled();
                if (!Database.commitTransactionChecked(transactionStatement, ConfigHandler.databaseType, () -> duckCommitAttempted.set(true))) {
                    throw new SQLException("Unable to commit DuckDB purge transaction");
                }
                duckTransaction = false;
                duckPurgeStarted = false;
                duckCommitAttempted.set(false);
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
                replaceSQLiteDatabase(sqliteTemporary);
                sqliteTemporary = null;
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
            boolean reloadRequired = false;
            if (duckTransaction && transactionStatement != null) {
                duckRollbackSucceeded = Database.rollbackTransaction(transactionStatement, ConfigHandler.databaseType);
            }
            if (ConfigHandler.databaseType.isDuckDB() && requiresDuckDatabaseReload(duckPurgeStarted, duckCommitAttempted.get(), duckRollbackSucceeded)) {
                Consumer.requireDatabaseReload();
                ConfigHandler.databaseReachable = false;
                resumePersistence = false;
                reloadRequired = true;
            }
            if (handoffStarted) {
                Consumer.requireDatabaseReload();
                ConfigHandler.databaseReachable = false;
                reloadRequired = true;
            }
            if (shutdownCancelled) {
                Thread.currentThread().interrupt();
            }
            else {
                Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_FAILED));
                if (reloadRequired) {
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
            boolean connectionClosed = connection == null;
            if (!connectionClosed) {
                try {
                    connection.close();
                    connectionClosed = true;
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
            if (sqliteTemporary != null && connectionClosed) {
                try {
                    Files.deleteIfExists(sqliteTemporary);
                }
                catch (IOException e) {
                    ErrorReporter.report(e);
                }
            }
            if (maintenanceLocked) {
                Consumer.unlockDatabaseMaintenance();
            }
            if (consumerPaused && resumePersistence && !Consumer.isPersistenceHalted()) {
                Consumer.setMaintenancePaused(false);
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

    private static void announcePurge(CommandSender player, String worldName, Integer[] radius, int restrictCount,
            String restrictTargets, String restrictKind, List<Integer> excludeEntityIds, String excludeTargets) {
        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_STARTED, worldName));
        if (radius != null) {
            Chat.sendGlobalMessage(player, Phrase.build(Phrase.ROLLBACK_RADIUS, radius[0].toString(), radius[0] == 1 ? Selector.FIRST : Selector.SECOND));
        }
        if (restrictCount > 0) {
            Chat.sendGlobalMessage(player, Phrase.build(Phrase.ROLLBACK_INCLUDE, restrictTargets, Selector.FIRST, restrictKind,
                    restrictCount == 1 ? Selector.FIRST : Selector.SECOND));
        }
        if (!excludeEntityIds.isEmpty()) {
            Chat.sendGlobalMessage(player, Phrase.build(Phrase.ROLLBACK_INCLUDE, excludeTargets, Selector.SECOND, Selector.SECOND,
                    excludeEntityIds.size() == 1 ? Selector.FIRST : Selector.SECOND));
        }
        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_NOTICE_1));
        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_NOTICE_2));
    }

    private static long purgeClickHouse(CommandSender player, PurgeFilter filter, boolean optimize) throws Exception {
        if (optimize) {
            Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_OPTIMIZING));
        }
        requirePurgeNotCancelled();
        return Database.purgeClickHouse(filter.startTime(), filter.endTime(), filter.worldId(), filter.blockTypes(), optimize);
    }

    private static SQLiteRebuild rebuildSQLiteTables(Connection connection, CommandSender player, PurgeFilter purgeFilter, String purgePrefix) throws Exception {
        String query;
        for (String table : ConfigHandler.databaseTables) {
            requirePurgeNotCancelled();
            try {
                query = "DROP TABLE IF EXISTS " + purgePrefix + table;
                executePurgeStatement(connection, query);
            }
            catch (Exception e) {
                reportPurgeFailure(e);
            }
        }

        Database.createDatabaseTables(purgePrefix, false, null, ConfigHandler.databaseType, true);

        long removed = 0;
        for (String table : ConfigHandler.databaseTables) {
            requirePurgeNotCancelled();
            String tableName = table.replace('_', ' ');
            Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_PROCESSING, tableName));
            String columns = "";
            try (Statement columnStatement = createPurgeStatement(connection);
                    ResultSet results = columnStatement.executeQuery("SELECT * FROM " + purgePrefix + table)) {
                ResultSetMetaData metadata = results.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    if (!columns.isEmpty()) {
                        columns += ",";
                    }
                    columns += metadata.getColumnName(i);
                }
            }
            String insertColumns = "";
            String selectColumns = columns;
            if (table.equals("block")) {
                insertColumns = "(rowid," + columns + ")";
                selectColumns = "rowid," + columns;
            }

            boolean error = false;
            if (!table.equals("database_lock")) {
                try {
                    String timeLimit = "";
                    if (table.equals("entity")) {
                        timeLimit = " WHERE " + purgeFilter.entityRetainCondition(purgePrefix + "block");
                    }
                    else {
                        String purgeCondition = purgeFilter.deleteCondition(table);
                        if (purgeCondition != null) {
                            timeLimit = " WHERE NOT COALESCE((" + purgeCondition + "), 0)";
                        }
                    }
                    query = "INSERT INTO " + purgePrefix + table + insertColumns + " SELECT " + selectColumns + " FROM " + ConfigHandler.prefix + table + timeLimit;
                    executePurgeStatement(connection, query);
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
                    executePurgeStatement(connection, query);
                }
                catch (Exception e) {
                    reportPurgeFailure(e);
                }

                try {
                    query = "REINDEX " + ConfigHandler.prefix + table;
                    executePurgeStatement(connection, query);
                }
                catch (Exception e) {
                    reportPurgeFailure(e);
                }

                try {
                    String index = " NOT INDEXED";
                    query = "INSERT INTO " + purgePrefix + table + insertColumns + " SELECT " + selectColumns + " FROM " + ConfigHandler.prefix + table + index;
                    executePurgeStatement(connection, query);
                }
                catch (Exception e) {
                    reportPurgeFailure(e);
                    return new SQLiteRebuild(removed, true);
                }

                try {
                    if (table.equals("entity")) {
                        query = purgeFilter.deleteUnreferencedEntities(purgePrefix + "entity", purgePrefix + "block");
                        executePurgeStatement(connection, query);
                    }
                    else {
                        String purgeCondition = purgeFilter.deleteCondition(table);
                        if (purgeCondition != null) {
                            query = "DELETE FROM " + purgePrefix + table + " WHERE " + purgeCondition;
                            executePurgeStatement(connection, query);
                        }
                    }
                }
                catch (Exception e) {
                    reportPurgeFailure(e);
                }
            }

            if (PurgePolicy.isPurgeable(table) || table.equals("entity_spawn")) {
                try {
                    removed += countPurgeRows(connection, ConfigHandler.prefix + table) - countPurgeRows(connection, purgePrefix + table);
                }
                catch (Exception e) {
                    reportPurgeFailure(e);
                }
            }
        }
        return new SQLiteRebuild(removed, false);
    }

    static Long deleteRelationalRows(Connection connection, CommandSender player, PurgeFilter purgeFilter) throws Exception {
        long removed = 0;
        String query;
        for (String table : ConfigHandler.databaseTables) {
            requirePurgeNotCancelled();
            Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_PROCESSING, table.replace('_', ' ')));
            try {
                String purgeCondition = purgeFilter.deleteCondition(table);
                boolean deleteEntities = purgeCondition != null && table.equals("block") && purgeFilter.removesKills() && !purgeFilter.purgesEntitiesByTime();
                // Kill rows and their entity data go together, or the kept kills lose their rollback data
                boolean atomic = deleteEntities && ConfigHandler.databaseType.isMySQL();
                if (atomic) {
                    connection.setAutoCommit(false);
                }
                try {
                    long deleted = 0;
                    if (deleteEntities) {
                        query = purgeFilter.deleteEntitiesOfPurgedKills(ConfigHandler.databaseType, ConfigHandler.prefix);
                        deleted = deleted + executePurgeUpdate(connection, query);
                    }

                    if (purgeCondition != null) {
                        query = "DELETE FROM " + ConfigHandler.prefix + table + " WHERE " + purgeCondition;
                        deleted = deleted + executePurgeUpdate(connection, query);
                    }

                    if (atomic) {
                        requirePurgeNotCancelled();
                        connection.commit();
                        connection.setAutoCommit(true);
                    }
                    removed = removed + deleted;
                }
                catch (Exception e) {
                    if (atomic) {
                        try {
                            connection.rollback();
                            connection.setAutoCommit(true);
                        }
                        catch (SQLException rollbackException) {
                            e.addSuppressed(rollbackException);
                        }
                    }
                    throw e;
                }
            }
            catch (Exception e) {
                if (e instanceof InterruptedException || ConfigHandler.databaseType.isDuckDB()) {
                    throw e;
                }
                if (!ConfigHandler.serverRunning) {
                    Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_FAILED));
                    return null;
                }

                reportPurgeFailure(e);
            }
        }
        return removed;
    }

    private static long sweepMySqlOrphans(Connection connection, PurgeFilter purgeFilter) throws Exception {
        long removed = 0;
        try {
            for (String sweepQuery : PurgeFilter.mysqlOrphanSweepSetup(ConfigHandler.prefix)) {
                executePurgeStatement(connection, sweepQuery);
            }
            removed = removed + executePurgeUpdate(connection, purgeFilter.mysqlOrphanSweepDelete(ConfigHandler.prefix));
        }
        catch (Exception e) {
            reportPurgeFailure(e);
        }
        finally {
            try {
                executePurgeStatement(connection, PurgeFilter.mysqlOrphanSweepTeardown(ConfigHandler.prefix));
            }
            catch (Exception e) {
                reportPurgeFailure(e);
            }
        }
        return removed;
    }

    static long deleteOrphanedEntityRows(Connection connection, String retainedPrefix, PurgeFilter filter) throws Exception {
        String query;
        requirePurgeNotCancelled();
        query = "UPDATE " + retainedPrefix + "entity_spawn SET kill_rowid=NULL WHERE kill_rowid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM " + retainedPrefix + "entity WHERE " + retainedPrefix + "entity.rowid=" + retainedPrefix + "entity_spawn.kill_rowid)";
        executePurgeUpdate(connection, query);

        requirePurgeNotCancelled();
        query = "UPDATE " + retainedPrefix + "entity_spawn SET block_rowid=NULL WHERE block_rowid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM " + retainedPrefix + "block WHERE " + retainedPrefix + "block.rowid=" + retainedPrefix + "entity_spawn.block_rowid)";
        executePurgeUpdate(connection, query);

        requirePurgeNotCancelled();
        query = "DELETE FROM " + retainedPrefix + "entity_spawn WHERE " + filter.unreferencedSpawnCondition(retainedPrefix);
        return executePurgeUpdate(connection, query);
    }

    private static void optimizeMySqlTables(Connection connection, CommandSender player) throws Exception {
        Chat.sendGlobalMessage(player, Phrase.build(Phrase.PURGE_OPTIMIZING));
        for (String table : ConfigHandler.databaseTables) {
            requirePurgeNotCancelled();
            String query = "OPTIMIZE LOCAL TABLE " + ConfigHandler.prefix + table;
            executePurgeStatement(connection, query);
        }
    }

    private static final class SQLiteRebuild {
        private final long removed;
        private final boolean aborted;

        private SQLiteRebuild(long removed, boolean aborted) {
            this.removed = removed;
            this.aborted = aborted;
        }
    }

    private static Path sqliteTempDatabase() {
        return Path.of(ConfigHandler.path + ConfigHandler.sqlite + ".tmp");
    }

    static void attachSQLitePurgeDatabase(Connection connection, Path temporary) throws IOException, SQLException, InterruptedException {
        Files.deleteIfExists(temporary);
        try (PreparedStatement statement = preparePurgeStatement(connection, "ATTACH DATABASE ? AS tmp_db")) {
            statement.setString(1, temporary.toString());
            statement.execute();
        }
    }

    static boolean requiresDuckDatabaseReload(boolean purgeStarted, boolean commitAttempted, boolean rollbackSucceeded) {
        return purgeStarted && (commitAttempted || !rollbackSucceeded);
    }

    private static void replaceSQLiteDatabase(Path temporary) throws IOException {
        Path database = Path.of(ConfigHandler.path + ConfigHandler.sqlite);
        Files.move(temporary, database, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
