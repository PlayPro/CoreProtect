package net.coreprotect.command;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.command.parser.ActionParser;
import net.coreprotect.command.parser.ParserUtils;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.ContainerRollback;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.PlayerLookup;
import net.coreprotect.database.rollback.Rollback;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.model.action.EntityActionFilter;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.lookup.StandardLookupRequest;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.LookupThrottle;

public class RollbackRestoreCommand {
    public static void runCommand(CommandSender player, Command command, boolean permission, String[] args, Location argLocation, long forceStart, long forceEnd) {
        runCommand(player, command, permission, args, argLocation, forceStart, forceEnd, null);
    }

    public static void runCommand(CommandSender player, Command command, boolean permission, String[] args, Location argLocation, long forceStart, long forceEnd, List<Object> savedRollback) {
        String lookupOnlyFlag = findLookupOnlyFlag(args);
        if (lookupOnlyFlag != null) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_PARAMETER, lookupOnlyFlag));
            return;
        }
        if (!CommandParser.validateCoordinates(player, args)) {
            return;
        }

        Location lo = (argLocation != null ? argLocation : CommandParser.parseLocation(player, args));
        List<String> argUuids = new ArrayList<>();
        List<String> argUsers = CommandParser.parseUsers(args);
        Integer[] argRadius = CommandParser.parseRadius(args, player, lo);
        int argNoisy = CommandParser.parseNoisy(args);
        ActionParser.ParseResult actionResult = CommandParser.parseActions(args, false);
        List<Integer> argAction = actionResult.getActions();
        EntityActionFilter argEntityActionFilter = actionResult.getEntityActionFilter();
        List<Object> argBlocks = CommandParser.parseRestricted(player, args, argAction);
        Map<Object, Boolean> argExclude = CommandParser.parseExcluded(player, args, argAction);
        List<String> argExcludeUsers = CommandParser.parseExcludedUsers(player, args);
        String ts = CommandParser.parseTimeString(args);
        long[] argTime = CommandParser.parseTime(args);
        long startTime = argTime[0];
        long endTime = argTime[1];
        int argWid = CommandParser.parseWorld(args, true, true);
        boolean worldedit = CommandParser.parseWorldEdit(args);
        boolean forceglobal = CommandParser.parseForceGlobal(args);
        int preview = CommandParser.parsePreview(args);
        String corecommand = args[0].toLowerCase(Locale.ROOT);

        if (argBlocks == null || argExclude == null || argExcludeUsers == null) {
            return;
        }

        if (!CommandParser.validateTargetCombination(player, argBlocks, argExclude.keySet(), argAction, argEntityActionFilter, false)) {
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
        if (argWid == -1) {
            String worldName = CommandParser.parseWorldName(args, true);
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.WORLD_NOT_FOUND, worldName));
            return;
        }
        if (preview > 0 && (!(player instanceof Player))) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PREVIEW_IN_GAME));
            return;
        }
        if (preview > 0 && argAction.contains(LookupActions.SIGN)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ACTION_NOT_SUPPORTED));
            return;
        }
        if (argAction.contains(-1)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_ACTION));
            return;
        }
        if (worldedit && argRadius == null) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_SELECTION, "WorldEdit"));
            return;
        }
        if (argRadius != null && argRadius[0] == -1) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_RADIUS));
            return;
        }
        if (ConfigHandler.activeRollbacks.get(player.getName()) != null) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_IN_PROGRESS));
            return;
        }
        Object[] lookupThrottle = ConfigHandler.lookupThrottle.get(player.getName());
        if (lookupThrottle != null && ((boolean) lookupThrottle[0] || System.currentTimeMillis() - (long) lookupThrottle[1] < 100)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
            return;
        }
        if (preview > 1 && forceStart <= 0) {
            preview = 1;
        }

        if (permission) {
            int a = 0;
            if (corecommand.equals("restore") || corecommand.equals("rs") || corecommand.equals("re")) {
                a = 1;
            }
            final int finalAction = a;

            int DEFAULT_RADIUS = Config.getGlobal().DEFAULT_RADIUS;
            boolean exactEntityContainer = argUsers.contains("#container") && ConfigHandler.lookupEntityContainer.get(player.getName()) != null;
            if ((player instanceof Player || player instanceof BlockCommandSender) && argRadius == null && DEFAULT_RADIUS > 0 && !forceglobal && !argAction.contains(LookupActions.ITEM) && !argAction.contains(LookupActions.INSPECT_CONTAINER) && !exactEntityContainer) {
                Location location = lo;
                int xmin = location.getBlockX() - DEFAULT_RADIUS;
                int xmax = location.getBlockX() + DEFAULT_RADIUS;
                int zmin = location.getBlockZ() - DEFAULT_RADIUS;
                int zmax = location.getBlockZ() + DEFAULT_RADIUS;
                argRadius = new Integer[] { DEFAULT_RADIUS, xmin, xmax, null, null, zmin, zmax, 0 };
            }
            // if (arg_radius==-2)arg_radius = -1;

            int g = 1;
            if (argUsers.contains("#global")) {
                if (argRadius == null) {
                    g = 0;
                }
            }

            if (argUsers.size() == 0 && (argWid > 0 || forceglobal) && argRadius == null) {
                if (finalAction == 0) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_ROLLBACK_USER, Selector.FIRST));
                }
                else {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_ROLLBACK_USER, Selector.SECOND));
                }
                return;
            }

            if (LookupActions.isInventoryLookup(argAction)) {
                if (argUsers.size() == 0) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_ACTION_USER));
                    return;
                }

                argExclude.put(Material.FIRE, false);
                argExclude.put(Material.WATER, false);
                argExclude.put(Material.FARMLAND, false);
                argExcludeUsers.add("#hopper");
            }
            else if (!argAction.contains(LookupActions.CONTAINER) && !argAction.contains(LookupActions.SIGN) && Config.getGlobal().EXCLUDE_TNT && !argExclude.containsKey(Material.TNT) && !argBlocks.contains(Material.TNT)) {
                argExclude.put(Material.TNT, true);
            }

            if (g == 1 && (argUsers.size() > 0 || (argUsers.size() == 0 && argRadius != null))) {
                Integer MAX_RADIUS = Config.getGlobal().MAX_RADIUS;
                if (argRadius != null) {
                    int radiusValue = argRadius[0];
                    if (radiusValue > MAX_RADIUS && MAX_RADIUS > 0) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MAXIMUM_RADIUS, MAX_RADIUS.toString(), (finalAction == 0 ? Selector.SECOND : Selector.THIRD)));
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.GLOBAL_ROLLBACK, "r:#global", (finalAction == 0 ? Selector.FIRST : Selector.SECOND)));
                        return;
                    }
                }
                if (argAction.size() > 0) {
                    if (argAction.contains(LookupActions.CONTAINER)) {
                        if (argUsers.contains("#global") || (argUsers.size() == 0 && argRadius == null)) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_ACTION_USER));
                            return;
                        }
                        else if (preview > 0) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PREVIEW_TRANSACTION, !argAction.contains(LookupActions.ITEM) ? Selector.FIRST : Selector.SECOND));
                            return;
                        }
                    }
                    if (argAction.contains(LookupActions.SESSION) || (argAction.contains(LookupActions.ITEM) && !argAction.contains(LookupActions.CONTAINER)) || (!argAction.contains(LookupActions.BLOCK_BREAK) && !argAction.contains(LookupActions.BLOCK_PLACE) && !argAction.contains(LookupActions.ENTITY_KILL) && !argAction.contains(LookupActions.ENTITY_SPAWN) && !argAction.contains(LookupActions.CONTAINER) && !argAction.contains(LookupActions.SIGN))) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ACTION_NOT_SUPPORTED));
                        return;
                    }
                }

                if (argUsers.size() == 0) {
                    argUsers.add("#global");
                }

                List<String> rollbackusers = argUsers;
                int c = 0;
                for (String ruser : rollbackusers) {
                    List<Player> players = Bukkit.getServer().matchPlayer(ruser); // here
                    for (Player p : players) {
                        if (p.getName().equalsIgnoreCase(ruser)) {
                            ruser = p.getName();
                            rollbackusers.set(c, ruser);
                        }
                    }
                    c++;

                    if (LookupActions.isInventoryLookup(argAction)) {
                        Player onlineUser = Bukkit.getServer().getPlayer(ruser);
                        if (onlineUser == null || !onlineUser.isOnline()) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.USER_OFFLINE, ruser));
                            return;
                        }
                    }
                }

                int wid = 0;
                int x = 0;
                int y = 0;
                int z = 0;
                if (rollbackusers.contains("#container")) {
                    if (argAction.contains(LookupActions.ITEM)) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_USERNAME, "#container"));
                        return;
                    }

                    StandardLookupRequest.Scope container = LookupCommand.containerScope(player.getName());
                    if (container != null) {
                        if (preview > 0) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PREVIEW_TRANSACTION, Selector.FIRST));
                            return;
                        }
                        else {
                            x = container.x;
                            y = container.y;
                            z = container.z;
                            wid = container.worldId;
                            String containerWorldName = WorldUtils.getWorldName(wid);
                            World containerWorld = Bukkit.getServer().getWorld(containerWorldName);
                            if (containerWorld == null) {
                                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.WORLD_NOT_FOUND, containerWorldName));
                                return;
                            }
                            argAction.add(LookupActions.INSPECT_CONTAINER);
                            argRadius = null;
                            argWid = 0;
                            lo = new Location(containerWorld, x, y, z);
                            if (!exactEntityContainer) {
                                Block block = lo.getBlock();
                                if (block.getState() instanceof Chest) {
                                    BlockFace[] blockFaces = new BlockFace[] { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
                                    for (BlockFace face : blockFaces) {
                                        if (block.getRelative(face, 1).getState() instanceof Chest) {
                                            Block relative = block.getRelative(face, 1);
                                            int x2 = relative.getX();
                                            int z2 = relative.getZ();
                                            double newX = (x + x2) / 2.0;
                                            double newZ = (z + z2) / 2.0;
                                            lo.setX(newX);
                                            lo.setZ(newZ);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_CONTAINER));
                        return;
                    }
                }

                final List<String> rollbackusers2 = rollbackusers;
                if (startTime > 0) {
                    long unixtimestamp = (System.currentTimeMillis() / 1000L);
                    long timeStart = unixtimestamp - startTime;
                    long timeEnd = endTime > 0 ? (unixtimestamp - endTime) : 0;
                    if (forceStart > 0) {
                        timeStart = forceStart;
                        timeEnd = forceEnd;
                    }
                    final long finalTimeStart = timeStart;
                    final long finalTimeEnd = timeEnd;
                    final Integer[] radius = argRadius;
                    try {
                        final CommandSender player2 = player;
                        final int noisy = argNoisy;
                        final String rtime = ts;
                        final List<String> uuidList = argUuids;
                        final List<Object> blist = argBlocks;
                        final Map<Object, Boolean> elist = argExclude;
                        final List<String> euserlist = argExcludeUsers;
                        final Location locationFinal = lo;
                        final int finalArgWid = argWid;
                        final List<Integer> finalArgAction = argAction;
                        final EntityActionFilter finalEntityActionFilter = argEntityActionFilter;
                        final String[] finalArgs = args;
                        final int finalPreview = preview;

                        Consumer.OperationStartResult startResult = Consumer.claimRollback(player.getName(), finalPreview == 0);
                        if (startResult != Consumer.OperationStartResult.STARTED) {
                            Phrase phrase = startResult == Consumer.OperationStartResult.PURGE_RUNNING ? Phrase.PURGE_IN_PROGRESS
                                    : startResult == Consumer.OperationStartResult.PERSISTENCE_HALTED ? Phrase.DATABASE_PERSISTENCE_HALTED
                                            : startResult == Consumer.OperationStartResult.RELOAD_RUNNING ? Phrase.DATABASE_BUSY : Phrase.ROLLBACK_IN_PROGRESS;
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(phrase));
                            return;
                        }

                        class BasicThread2 implements Runnable {
                            @Override
                            public void run() {
                                if (!LookupThrottle.tryAcquire(player.getName(), 100)) {
                                    Consumer.releaseRollback(player.getName());
                                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                                    return;
                                }
                                try (Connection connection = Database.getConnection(false, 1000);
                                        Statement statement = connection == null ? null : connection.createStatement()) {
                                    int action = finalAction;
                                    Location location = locationFinal;
                                    if (connection != null) {
                                        String baduser = "";
                                        boolean exists = false;
                                        for (String check : rollbackusers2) {
                                            if (!check.equals("#global") && !check.equals("#container")) {
                                                exists = PlayerLookup.playerExists(connection, check);
                                                if (!exists) {
                                                    baduser = check;
                                                    break;
                                                }
                                            }
                                            else {
                                                exists = true;
                                            }
                                        }
                                        if (exists) {
                                            for (String check : euserlist) {
                                                if (!check.equals("#global") && !check.equals("#hopper")) {
                                                    exists = PlayerLookup.playerExists(connection, check);
                                                    if (!exists) {
                                                        baduser = check;
                                                        break;
                                                    }
                                                }
                                                else if (check.equals("#global")) {
                                                    baduser = "#global";
                                                    exists = false;
                                                }
                                            }
                                        }
                                        if (exists) {
                                            boolean restrictWorld = false;
                                            if (radius != null) {
                                                restrictWorld = true;
                                            }
                                            if (location == null) {
                                                restrictWorld = false;
                                            }
                                            if (finalArgWid > 0) {
                                                restrictWorld = true;
                                                String worldName = WorldUtils.getWorldName(finalArgWid);
                                                World world = Bukkit.getServer().getWorld(worldName);
                                                if (world == null) {
                                                    Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.WORLD_NOT_FOUND, worldName));
                                                    return;
                                                }
                                                location = new Location(world, 0, 0, 0);
                                            }
                                            boolean verbose = false;
                                            if (noisy == 1) {
                                                verbose = true;
                                            }

                                            String users = "";
                                            for (String value : rollbackusers2) {
                                                if (users.length() == 0) {
                                                    users = "" + value + "";
                                                }
                                                else {
                                                    users = users + ", " + value;
                                                }
                                            }
                                            if (users.equals("#global") && restrictWorld) {
                                                // chat output only, don't pass into any functions
                                                String worldLabel = (location != null && location.getWorld() != null) ? location.getWorld().getName() : WorldUtils.getWorldName(finalArgWid);
                                                users = "#" + worldLabel;
                                            }
                                            String userLabel = users;
                                            BooleanSupplier admission = null;
                                            if (savedRollback != null) {
                                                admission = () -> {
                                                    if (!consumeSavedRollback(player2.getName(), savedRollback)) {
                                                        Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_ABORTED));
                                                        return false;
                                                    }
                                                    sendStarted(player2, userLabel, finalPreview, action);
                                                    return true;
                                                };
                                            }
                                            else {
                                                sendStarted(player2, userLabel, finalPreview, action);
                                            }

                                            boolean completed = true;
                                            if (finalArgAction.contains(LookupActions.INSPECT_CONTAINER) && ConfigHandler.lookupEntityContainer.get(player2.getName()) == null) {
                                                completed = ContainerRollback.performContainerRollbackRestore(statement, player2, uuidList, rollbackusers2, rtime, blist, elist, euserlist, finalArgAction, location, radius, finalTimeStart, finalTimeEnd, restrictWorld, false, verbose, action, admission);
                                            }
                                            else {
                                                completed = Rollback.performRollbackRestore(statement, player2, uuidList, rollbackusers2, rtime, blist, elist, euserlist, finalArgAction, finalEntityActionFilter, location, radius, finalTimeStart, finalTimeEnd, restrictWorld, false, verbose, action, finalPreview, admission) != null;
                                            }
                                            if (completed && finalPreview < 2) {
                                                List<Object> list = new ArrayList<>();
                                                list.add(finalTimeStart);
                                                list.add(finalTimeEnd);
                                                list.add(finalArgs);
                                                list.add(locationFinal);
                                                ConfigHandler.lastRollback.put(player2.getName(), list);
                                            }
                                        }
                                        else {
                                            Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.USER_NOT_FOUND, baduser));
                                        }
                                    }
                                    else {
                                        Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                                    }
                                }
                                catch (Exception e) {
                                    ErrorReporter.report(e);
                                }
                                finally {
                                    Consumer.releaseRollback(player2.getName());
                                    LookupThrottle.release(player2.getName());
                                }
                            }
                        }
                        try {
                            new Thread(new BasicThread2()).start();
                        }
                        catch (RuntimeException | Error e) {
                            Consumer.releaseRollback(player.getName());
                            throw e;
                        }
                    }
                    catch (Exception e) {
                        ErrorReporter.report(e);
                    }
                }
                else {
                    if (finalAction == 0) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_LOOKUP_TIME, Selector.SECOND));
                    }
                    else {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_LOOKUP_TIME, Selector.THIRD));
                    }
                }
            }
            else {
                if (finalAction == 0) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_ROLLBACK_RADIUS, Selector.FIRST)); // rollback
                }
                else {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_ROLLBACK_RADIUS, Selector.SECOND)); // restore
                }
            }
        }
        else {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
        }
    }

    private static void sendStarted(CommandSender sender, String users, int preview, int action) {
        String prefix = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- ";
        if (preview == 2) {
            Chat.sendMessage(sender, prefix + Phrase.build(Phrase.PREVIEW_CANCELLING));
        }
        else {
            String selector = preview == 1 ? Selector.THIRD : action == 0 ? Selector.FIRST : Selector.SECOND;
            Chat.sendMessage(sender, prefix + Phrase.build(Phrase.ROLLBACK_STARTED, users, selector));
        }
    }

    private static boolean consumeSavedRollback(String playerName, List<Object> savedRollback) {
        Map<String, List<Object>> history = ConfigHandler.lastRollback;
        synchronized (history) {
            if (history.get(playerName) != savedRollback) {
                return false;
            }
            history.remove(playerName);
            return true;
        }
    }

    private static String findLookupOnlyFlag(String[] args) {
        for (int index = 1; index < args.length; index++) {
            String raw = args[index];
            String argument = ParserUtils.normalize(raw);
            if (argument.equals("#count") || argument.equals("#sum") || argument.equals("#summary") || argument.equals("count") || argument.equals("sum")) {
                return raw.trim();
            }
        }
        return null;
    }
}
