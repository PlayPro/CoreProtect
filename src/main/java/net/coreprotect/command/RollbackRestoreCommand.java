package net.coreprotect.command;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ContainerRollback;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.PlayerLookup;
import net.coreprotect.database.rollback.Rollback;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public class RollbackRestoreCommand {
    protected static void runCommand(CommandSender player, Command command, boolean permission, String[] args, Location argLocation, long forceStart, long forceEnd) {
        Location lo = (argLocation != null ? argLocation : CommandHandler.parseLocation(player, args));
        List<String> argUuids = new ArrayList<>();
        List<String> argUsers = CommandHandler.parseUsers(args);
        Integer[] argRadius = CommandHandler.parseRadius(args, player, lo);
        int argNoisy = CommandHandler.parseNoisy(args);
        List<Integer> argAction = CommandHandler.parseAction(args);
        List<Object> argBlocks = CommandHandler.parseRestricted(player, args, argAction);
        Map<Object, Boolean> argExclude = CommandHandler.parseExcluded(player, args, argAction);
        List<String> argExcludeUsers = CommandHandler.parseExcludedUsers(player, args);
        String ts = CommandHandler.parseTimeString(args);
        long[] argTime = CommandHandler.parseTime(args);
        long startTime = argTime[0];
        long endTime = argTime[1];
        int argWid = CommandHandler.parseWorld(args, true, true);
        boolean count = CommandHandler.parseCount(args);
        boolean worldedit = CommandHandler.parseWorldEdit(args);
        boolean forceglobal = CommandHandler.parseForceGlobal(args);
        int preview = CommandHandler.parsePreview(args);
        String corecommand = args[0].toLowerCase(Locale.ROOT);

        if (argBlocks == null || argExclude == null || argExcludeUsers == null) {
            return;
        }

        /* check for invalid block/entity combinations (include) */
        boolean hasBlock = false;
        boolean hasEntity = false;
        for (Object arg : argBlocks) {
            if (arg instanceof Material) {
                hasBlock = true;
            }
            else if (arg instanceof EntityType) {
                hasEntity = true;
                if (argAction.size() == 0) {
                    argAction.add(3);
                }
                else if (!argAction.contains(3)) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_INCLUDE_COMBO));
                    return;
                }
            }
        }

        /* check for invalid block/entity combinations (exclude) */
        for (Object arg : argExclude.keySet()) {
            if (arg instanceof Material) {
                hasBlock = true;
            }
            else if (arg instanceof EntityType) {
                hasEntity = true;
                if (argAction.size() == 0) {
                    argAction.add(3);
                }
                else if (!argAction.contains(3)) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_INCLUDE_COMBO));
                    return;
                }
            }
        }

        if (hasBlock && hasEntity) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_INCLUDE_COMBO));
            return;
        }

        if (count) {
            LookupCommand.runCommand(player, command, permission, args);
            return;
        }
        if (ConfigHandler.converterRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
            return;
        }
        if (ConfigHandler.purgeRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
            return;
        }
        if (argWid == -1) {
            String worldName = CommandHandler.parseWorldName(args, true);
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.WORLD_NOT_FOUND, worldName));
            return;
        }
        if (preview > 0 && (!(player instanceof Player))) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PREVIEW_IN_GAME));
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
        if (ConfigHandler.lookupThrottle.get(player.getName()) != null) {
            Object[] lookupThrottle = ConfigHandler.lookupThrottle.get(player.getName());
            if ((boolean) lookupThrottle[0] || ((System.currentTimeMillis() - (long) lookupThrottle[1])) < 100) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                return;
            }
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
            if ((player instanceof Player || player instanceof BlockCommandSender) && argRadius == null && DEFAULT_RADIUS > 0 && !forceglobal && !argAction.contains(11)) {
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

            if (argAction.contains(4) && argAction.contains(11)) { // a:inventory
                if (argUsers.size() == 0) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_ACTION_USER));
                    return;
                }

                argExclude.put(Material.FIRE, false);
                argExclude.put(Material.WATER, false);
                argExclude.put(Material.FARMLAND, false);
                argExcludeUsers.add("#hopper");
            }
            else if (!argAction.contains(4) && Config.getGlobal().EXCLUDE_TNT && !argExclude.containsKey(Material.TNT) && !argBlocks.contains(Material.TNT)) {
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
                    if (argAction.contains(4)) {
                        if (argUsers.contains("#global") || (argUsers.size() == 0 && argRadius == null)) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_ACTION_USER));
                            return;
                        }
                        else if (preview > 0) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PREVIEW_TRANSACTION, !argAction.contains(11) ? Selector.FIRST : Selector.SECOND));
                            return;
                        }
                    }
                    if (argAction.contains(8) || (argAction.contains(11) && !argAction.contains(4)) || (!argAction.contains(0) && !argAction.contains(1) && !argAction.contains(3) && !argAction.contains(4))) {
                        if (finalAction == 0) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ACTION_NOT_SUPPORTED));
                        }
                        else {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ACTION_NOT_SUPPORTED));
                        }
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

                    if (argAction.contains(4) && argAction.contains(11)) {
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
                    if (argAction.contains(11)) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_USERNAME, "#container"));
                        return;
                    }

                    boolean valid = false;
                    if (ConfigHandler.lookupType.get(player.getName()) != null) {
                        int lookupType = ConfigHandler.lookupType.get(player.getName());
                        if (lookupType == 1) {
                            valid = true;
                        }
                        else if (lookupType == 5) {
                            if (ConfigHandler.lookupUlist.get(player.getName()).contains("#container")) {
                                valid = true;
                            }
                        }
                    }
                    if (valid) {
                        if (preview > 0) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PREVIEW_TRANSACTION, Selector.FIRST));
                            return;
                        }
                        else {
                            String lcommand = ConfigHandler.lookupCommand.get(player.getName());
                            String[] data = lcommand.split("\\.");
                            x = Integer.parseInt(data[0]);
                            y = Integer.parseInt(data[1]);
                            z = Integer.parseInt(data[2]);
                            wid = Integer.parseInt(data[3]);
                            argAction.add(5);
                            argRadius = null;
                            argWid = 0;
                            lo = new Location(Bukkit.getServer().getWorld(Util.getWorldName(wid)), x, y, z);
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
                        final String[] finalArgs = args;
                        final int finalPreview = preview;

                        ConfigHandler.activeRollbacks.put(player.getName(), true);

                        class BasicThread2 implements Runnable {
                            @Override
                            public void run() {
                                try (Connection connection = Database.getConnection(false, 1000)) {
                                    ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });
                                    int action = finalAction;
                                    Location location = locationFinal;
                                    if (connection != null) {
                                        Statement statement = connection.createStatement();
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
                                                location = new Location(Bukkit.getServer().getWorld(Util.getWorldName(finalArgWid)), 0, 0, 0);
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
                                                users = "#" + location.getWorld().getName();
                                            }
                                            if (finalPreview == 2) {
                                                Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PREVIEW_CANCELLING));
                                            }
                                            else if (finalPreview == 1) {
                                                Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_STARTED, users, Selector.THIRD));
                                            }
                                            else if (action == 0) {
                                                Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_STARTED, users, Selector.FIRST));
                                            }
                                            else {
                                                Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_STARTED, users, Selector.SECOND));
                                            }

                                            if (finalArgAction.contains(5)) {
                                                ContainerRollback.performContainerRollbackRestore(statement, player2, uuidList, rollbackusers2, rtime, blist, elist, euserlist, finalArgAction, location, radius, finalTimeStart, finalTimeEnd, restrictWorld, false, verbose, action);
                                            }
                                            else {
                                                Rollback.performRollbackRestore(statement, player2, uuidList, rollbackusers2, rtime, blist, elist, euserlist, finalArgAction, location, radius, finalTimeStart, finalTimeEnd, restrictWorld, false, verbose, action, finalPreview);
                                            }
                                            if (finalPreview < 2) {
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
                                        statement.close();
                                    }
                                    else {
                                        Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                                    }
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }
                                if (ConfigHandler.activeRollbacks.get(player2.getName()) != null) {
                                    ConfigHandler.activeRollbacks.remove(player2.getName());
                                }
                                ConfigHandler.lookupThrottle.put(player2.getName(), new Object[] { false, System.currentTimeMillis() });
                            }
                        }
                        Runnable runnable = new BasicThread2();
                        Thread thread = new Thread(runnable);
                        thread.start();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
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
}
