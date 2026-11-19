package net.coreprotect.command;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.command.lookup.BlockLookupThread;
import net.coreprotect.command.lookup.ChestTransactionLookupThread;
import net.coreprotect.command.lookup.EntityInteractionLookupThread;
import net.coreprotect.command.lookup.StandardLookupThread;
import net.coreprotect.command.parser.ActionParser;
import net.coreprotect.command.parser.LocationParser;
import net.coreprotect.command.parser.MessageFilterParser;
import net.coreprotect.command.parser.RollbackStateParser;
import net.coreprotect.command.parser.TimeParser;
import net.coreprotect.command.parser.WorldParser;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.model.action.EntityActionFilter;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.lookup.LookupOutputMode;
import net.coreprotect.model.lookup.LookupSession;
import net.coreprotect.model.lookup.StandardLookupRequest;
import net.coreprotect.model.lookup.LookupRollbackState;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatMessage;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

public class LookupCommand {
    public static void runCommand(CommandSender player, Command command, boolean permission, String[] args) {
        int resultc = args.length;
        MessageFilterParser.ParseResult messageFilterResult = CommandParser.parseMessageFilters(args);
        args = messageFilterResult.getArguments();
        List<String> argFilters = messageFilterResult.getFilters();
        args = CommandParser.parsePage(args);
        if (!CommandParser.validateCoordinates(player, args)) {
            return;
        }
        TimeParser.ParsedTime requestedTime = TimeParser.parseDurationArguments(args);
        if (requestedTime.invalidArgument() != null) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_PARAMETER, requestedTime.invalidArgument()));
            return;
        }
        String worldFilterName = WorldParser.parseWorldFilterName(args, true);
        int argWid = 0;
        String pendingWorldName = null;
        if (worldFilterName != null && !worldFilterName.isEmpty()) {
            argWid = ConfigHandler.worlds.getOrDefault(worldFilterName, -1);
            if (argWid <= 0) {
                argWid = WorldUtils.matchWorld("#" + worldFilterName);
                if (argWid <= 0 || (ConfigHandler.databaseType.isClickHouse() && !ConfigHandler.worlds.containsKey(worldFilterName))) {
                    pendingWorldName = worldFilterName;
                }
            }
        }
        Location lo = LocationParser.parseLocation(player, args, argWid);
        // List<String> arg_uuids = new ArrayList<String>();
        List<String> argUsers = CommandParser.parseUsers(args);
        Integer[] argRadius = CommandParser.parseRadius(args, player, lo);
        ActionParser.ParseResult actionResult = CommandParser.parseActions(args, true);
        List<Integer> argAction = actionResult.getActions();
        EntityActionFilter argEntityActionFilter = actionResult.getEntityActionFilter();
        List<Object> argBlocks = CommandParser.parseRestricted(player, args, argAction);
        Map<Object, Boolean> argExclude = CommandParser.parseExcluded(player, args, argAction);
        List<String> argExcludeUsers = CommandParser.parseExcludedUsers(player, args);
        long[] argTime = requestedTime.sortedSeconds();
        long startTime = argTime[0];
        long endTime = argTime[1];
        int parseRows = CommandParser.parseRows(args);
        boolean summary = CommandParser.parseSummary(args);
        LookupOutputMode outputMode = summary ? LookupOutputMode.SUMMARY
                : CommandParser.parseCount(args) ? LookupOutputMode.COUNT : LookupOutputMode.DETAIL;
        RollbackStateParser.ParseResult rollbackStateResult = CommandParser.parseRollbackState(args);
        LookupRollbackState rollbackState = rollbackStateResult.getState();
        boolean worldedit = CommandParser.parseWorldEdit(args);
        boolean forceglobal = worldFilterName != null || CommandParser.parseForceGlobal(args);
        boolean pageLookup = false;

        if (argBlocks == null || argExclude == null || argExcludeUsers == null) {
            return;
        }

        if (rollbackStateResult.isConflicting()) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_PARAMETER, "#rolledback + #restored"));
            return;
        }

        if (args[0].toLowerCase(Locale.ROOT).equals("page") && (args.length != 2 || !isAsciiDigits(args[1]))) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, Color.WHITE, "/co page <page>"));
            return;
        }


        if (!CommandParser.validateTargetCombination(player, argBlocks, argExclude.keySet(), argAction, argEntityActionFilter, true)) {
            return;
        }

        if ("".equals(worldFilterName)) {
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, worldFilterName)).build());
            return;
        }

        int type = 0;
        if (ConfigHandler.lookupType.get(player.getName()) != null) {
            type = ConfigHandler.lookupType.get(player.getName());
        }
        if (type == 0 && resultc > 1) {
            type = 4;
        }
        else if (resultc > 2) {
            type = 4;
        }
        else if (resultc > 1) {
            pageLookup = true;
            String dat = args[1];
            if (dat.contains(":")) {
                String[] split = dat.split(":");
                String check1 = split[0].replaceAll("[^a-zA-Z_]", "");
                String check2 = "";
                if (split.length > 1) {
                    check2 = split[1].replaceAll("[^a-zA-Z_]", "");
                }
                if (check1.length() > 0 || check2.length() > 0) {
                    type = 4;
                    pageLookup = false;
                }
            }
            else {
                String check1 = dat.replaceAll("[^a-zA-Z_]", "");
                if (check1.length() > 0) {
                    type = 4;
                    pageLookup = false;
                }
            }
        }
        if (argAction.contains(LookupActions.CHAT) || argAction.contains(LookupActions.COMMAND) || argAction.contains(LookupActions.SESSION) || argAction.contains(LookupActions.USERNAME) || argAction.contains(LookupActions.SIGN)) {
            pageLookup = true;
        }

        if (!permission) {
            if (!pageLookup || !player.hasPermission("coreprotect.inspect")) {
                Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.NO_PERMISSION)).build());
                return;
            }
        }
        if (ConfigHandler.converterRunning || ConfigHandler.migrationRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
            return;
        }
        if (ConfigHandler.purgeRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
            return;
        }
        if (resultc < 2) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co l <params>"));
            return;
        }
        if (argAction.contains(-1)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_ACTION));
            return;
        }
        if (rollbackState != LookupRollbackState.ANY && (argAction.contains(LookupActions.CHAT) || argAction.contains(LookupActions.COMMAND) || argAction.contains(LookupActions.SESSION) || argAction.contains(LookupActions.USERNAME) || argAction.contains(LookupActions.SIGN))) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INCOMPATIBLE_ACTION, rollbackState == LookupRollbackState.ROLLED_BACK ? "#rolledback" : "#restored"));
            return;
        }
        if (messageFilterResult.isSpecified()) {
            if (messageFilterResult.hasInvalidLength()) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MESSAGE_FILTER_TOO_SHORT, Integer.toString(MessageFilterParser.MINIMUM_FILTER_CODE_POINTS)));
                return;
            }
            boolean messageLookup = !argAction.isEmpty() && argAction.stream().allMatch(action -> action == LookupActions.CHAT || action == LookupActions.COMMAND);
            boolean signLookup = !argAction.isEmpty() && argAction.stream().allMatch(action -> action == LookupActions.SIGN);
            if (!messageLookup && !signLookup) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INCOMPATIBLE_ACTION, "f:<filter>"));
                return;
            }
        }
        if (worldedit && argRadius == null) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_SELECTION, "WorldEdit"));
            return;
        }
        if (argRadius != null && argRadius[0] == -1) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_RADIUS));
            return;
        }
        if (ConfigHandler.lookupThrottle.get(player.getName()) != null) {
            Object[] lookupThrottle = ConfigHandler.lookupThrottle.get(player.getName());
            if ((boolean) lookupThrottle[0] || ((System.currentTimeMillis() - (long) lookupThrottle[1])) < 50) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                return;
            }
        }
        boolean allPermission = false;
        if (args[0].equals("near") && player.hasPermission("coreprotect.lookup.near")) {
            allPermission = true;
        }
        if (!allPermission) {
            if (!pageLookup && (argAction.isEmpty() || argAction.contains(LookupActions.BLOCK_BREAK) || argAction.contains(LookupActions.BLOCK_PLACE)) && !player.hasPermission("coreprotect.lookup.block")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(LookupActions.INTERACTION) && !player.hasPermission("coreprotect.lookup.click")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(LookupActions.ENTITY_KILL) && !player.hasPermission("coreprotect.lookup.kill")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(LookupActions.ENTITY_SPAWN) && !player.hasPermission("coreprotect.lookup.spawn")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(LookupActions.CONTAINER) && !argAction.contains(LookupActions.ITEM) && !player.hasPermission("coreprotect.lookup.container")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(LookupActions.CHAT) && !player.hasPermission("coreprotect.lookup.chat")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(LookupActions.COMMAND) && !player.hasPermission("coreprotect.lookup.command")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(LookupActions.SESSION) && !player.hasPermission("coreprotect.lookup.session")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(LookupActions.USERNAME) && !player.hasPermission("coreprotect.lookup.username")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(LookupActions.SIGN) && !player.hasPermission("coreprotect.lookup.sign")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(LookupActions.ITEM) && !argAction.contains(LookupActions.CONTAINER) && !player.hasPermission("coreprotect.lookup.item")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (LookupActions.isInventoryLookup(argAction) && !player.hasPermission("coreprotect.lookup.inventory")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
        }
        if (argAction.contains(LookupActions.CHAT) || argAction.contains(LookupActions.COMMAND) || argAction.contains(LookupActions.SESSION) || argAction.contains(LookupActions.USERNAME) || argAction.contains(LookupActions.SIGN)) {
            if (argAction.contains(LookupActions.USERNAME) && (argRadius != null || worldFilterName != null || worldedit)) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INCOMPATIBLE_ACTION, "r:"));
                return;
            }
            if (argBlocks.size() > 0) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INCOMPATIBLE_ACTION, "i:"));
                return;
            }
            if (argExclude.size() > 0) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INCOMPATIBLE_ACTION, "e:"));
                return;
            }
        }

        if (startTime <= 0 && argRadius == null && !pageLookup && type == 4 && (argBlocks.size() > 0 || argUsers.size() > 0)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_LOOKUP_TIME, Selector.FIRST));
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

        if (type == 1) {
            PageSelection pageSelection = parsePageSelection(player, args, 7, parseRows, true);
            boolean defaultRe = pageSelection.defaultRows;
            int p = pageSelection.page;
            int re = pageSelection.rows;

            String lcommand = ConfigHandler.lookupCommand.get(player.getName());
            String[] data = lcommand.split("\\.");
            int x = Integer.parseInt(data[0]);
            int y = Integer.parseInt(data[1]);
            int z = Integer.parseInt(data[2]);
            int wid = Integer.parseInt(data[3]);
            int x2 = Integer.parseInt(data[4]);
            int y2 = Integer.parseInt(data[5]);
            int z2 = Integer.parseInt(data[6]);
            if (defaultRe) {
                re = Integer.parseInt(data[7]);
            }

            String bc = x + "." + y + "." + z + "." + wid + "." + x2 + "." + y2 + "." + z2 + "." + re;
            ConfigHandler.lookupCommand.put(player.getName(), bc);

            String world = WorldUtils.getWorldName(wid);
            World bukkitWorld = Bukkit.getServer().getWorld(world);
            if (bukkitWorld == null) {
                Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, world)).build());
                return;
            }
            double dx = 0.5 * (x + x2);
            double dy = 0.5 * (y + y2);
            double dz = 0.5 * (z + z2);
            final Location location = new Location(bukkitWorld, dx, dy, dz);

            Runnable runnable = new ChestTransactionLookupThread(player, command, location, p, re);
            Thread thread = new Thread(runnable);
            thread.start();
        }
        else if (type == 2 || type == 3 || type == 7 || type == 8 || type == 9) {
            PageSelection pageSelection = parsePageSelection(player, args, 7, parseRows, true);
            boolean defaultRe = pageSelection.defaultRows;
            int page = pageSelection.page;
            int re = pageSelection.rows;

            if (type == 9) {
                Runnable runnable = new EntityInteractionLookupThread(player, command, page, re);
                Thread thread = new Thread(runnable);
                thread.start();
                return;
            }

            String lcommand = ConfigHandler.lookupCommand.get(player.getName());
            String[] data = lcommand.split("\\.");
            int x = Integer.parseInt(data[0]);
            int y = Integer.parseInt(data[1]);
            int z = Integer.parseInt(data[2]);
            int wid = Integer.parseInt(data[3]);
            int lookupType = Integer.parseInt(data[4]);
            if (defaultRe) {
                re = Integer.parseInt(data[5]);
            }

            String bc = x + "." + y + "." + z + "." + wid + "." + lookupType + "." + re;
            Integer entitySpawnRowId = type == 2 && data.length > 6 ? Integer.valueOf(data[6]) : null;
            if (entitySpawnRowId != null) {
                bc += "." + entitySpawnRowId;
            }
            ConfigHandler.lookupCommand.put(player.getName(), bc);

            String world = WorldUtils.getWorldName(wid);
            World bukkitWorld = Bukkit.getServer().getWorld(world);
            if (bukkitWorld == null) {
                Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, world)).build());
                return;
            }
            final Block block = bukkitWorld.getBlockAt(x, y, z);
            final BlockState blockState = block.getState();

            Runnable runnable = new BlockLookupThread(player, command, block, blockState, page, re, type, entitySpawnRowId);
            Thread thread = new Thread(runnable);
            thread.start();
        }
        else if (type == 4 || type == LookupSession.STANDARD_LOOKUP) {
            int defaultRowCount = outputMode == LookupOutputMode.SUMMARY ? 8 : 4;
            if (outputMode != LookupOutputMode.SUMMARY && (argAction.contains(LookupActions.CHAT) || argAction.contains(LookupActions.COMMAND) || argAction.contains(LookupActions.USERNAME) || LookupActions.isInventoryLookup(argAction))) {
                defaultRowCount = 7;
            }
            PageSelection pageSelection = parsePageSelection(player, args, defaultRowCount, parseRows, type == LookupSession.STANDARD_LOOKUP);
            boolean defaultRe = pageSelection.defaultRows;
            int pa = pageSelection.page;
            int re = pageSelection.rows;

            if (type == LookupSession.STANDARD_LOOKUP) {
                LookupSession session = ConfigHandler.lookupSessions.get(player.getName());
                if (session == null) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
                    return;
                }
                int pageSize = defaultRe ? session.request.pageSize : re;
                startStandardLookup(player, command, session.request.forPage(pa, pageSize), session);
                return;
            }

            int g = 1;
            if (argUsers.contains("#global")) {
                if (argRadius == null) {
                    g = 0;
                }
            }

            if (g == 1 && (pageLookup || argBlocks.size() > 0 || argUsers.size() > 0 || (argUsers.size() == 0 && argRadius != null))) {
                Integer MAX_RADIUS = Config.getGlobal().MAX_RADIUS;
                if (argRadius != null) {
                    int radiusValue = argRadius[0];
                    if (radiusValue > MAX_RADIUS && MAX_RADIUS > 0) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MAXIMUM_RADIUS, MAX_RADIUS.toString(), Selector.FIRST));
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.GLOBAL_LOOKUP));
                        return;
                    }
                }

                if (argUsers.size() == 0) {
                    argUsers.add("#global");
                }
                List<String> rollbackusers = argUsers;
                int c = 0;
                for (String ruser : rollbackusers) {
                    if (Bukkit.getServer() != null) {
                        List<Player> players = Bukkit.getServer().matchPlayer(ruser);
                        for (Player p : players) {
                            if (p.getName().equalsIgnoreCase(ruser)) {
                                rollbackusers.set(c, p.getName());
                            }
                        }
                    }
                    c++;

                    if (LookupActions.isInventoryLookup(argAction)) {
                        if (ruser.startsWith("#")) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_USERNAME, ruser));
                            return;
                        }
                    }
                }

                int x = 0;
                int y = 0;
                int z = 0;
                int wid = 0;

                if (lo != null) {
                    x = lo.getBlockX();
                    z = lo.getBlockZ();
                    if (lo.getWorld() != null && pendingWorldName == null) {
                        wid = WorldUtils.getWorldId(lo.getWorld().getName());
                    }
                }

                if (rollbackusers.size() == 1 && rollbackusers.contains("#global") && argAction.contains(LookupActions.USERNAME)) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co l a:username u:<user>"));
                    return;
                }

                if (rollbackusers.contains("#container")) {
                    if (argAction.contains(LookupActions.CHAT) || argAction.contains(LookupActions.COMMAND) || argAction.contains(LookupActions.SESSION) || argAction.contains(LookupActions.USERNAME) || argAction.contains(LookupActions.SIGN) || argAction.contains(LookupActions.ITEM)) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_USERNAME, "#container"));
                        return;
                    }

                    StandardLookupRequest.Scope container = containerScope(player.getName());
                    if (container != null) {
                        if (!player.hasPermission("coreprotect.lookup.container") && !allPermission) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                            return;
                        }
                        x = container.x;
                        y = container.y;
                        z = container.z;
                        wid = container.worldId;
                        argAction.add(LookupActions.INSPECT_CONTAINER);
                        argRadius = null;
                        argWid = 0;
                        pendingWorldName = null;
                    }
                    else {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_CONTAINER));
                        return;
                    }
                }

                try {
                    long now = System.currentTimeMillis() / 1000L;
                    long timeStart = startTime > 0 ? now - startTime : 0;
                    long timeEnd = endTime > 0 ? now - endTime : 0;
                    StandardLookupRequest.Filters filters = new StandardLookupRequest.Filters(rollbackusers, argBlocks, argExclude, argExcludeUsers, argAction, argEntityActionFilter, argFilters);
                    StandardLookupRequest.Scope scope = new StandardLookupRequest.Scope(lo, x, y, z, wid, argWid, pendingWorldName, timeStart, timeEnd, argRadius);
                    startStandardLookup(player, command, new StandardLookupRequest(filters, scope, pa, re, outputMode, rollbackState), null);
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
            else {
                // Functions.sendMessage(player, ChatColors.RED + "You did not specify a lookup radius.");
                if (argUsers.size() == 0 && argBlocks.size() == 0 && (argWid > 0 || forceglobal)) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_LOOKUP_USER, Selector.FIRST));
                    return;
                }
                else if (argUsers.size() == 0 && argBlocks.size() == 0 && argRadius == null) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_LOOKUP_USER, Selector.SECOND));
                    return;
                }
                else {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co l <params>"));
                }
            }
        }
        else {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co l <params>"));
        }
    }

    static boolean hasContainerHistory(String name) {
        Integer type = ConfigHandler.lookupType.get(name);
        if (type == null) {
            return false;
        }
        if (type == 1) {
            return true;
        }
        LookupSession session = ConfigHandler.lookupSessions.get(name);
        return type == LookupSession.STANDARD_LOOKUP && session != null && session.request.filters.users.contains("#container");
    }

    static StandardLookupRequest.Scope containerScope(String name) {
        if (!hasContainerHistory(name)) {
            return null;
        }
        LookupSession session = ConfigHandler.lookupSessions.get(name);
        if (Integer.valueOf(LookupSession.STANDARD_LOOKUP).equals(ConfigHandler.lookupType.get(name))) {
            return session == null ? null : session.request.scope;
        }
        String input = ConfigHandler.lookupCommand.get(name);
        if (input == null) {
            return null;
        }
        String[] coordinates = input.split("\\.");
        if (coordinates.length < 4) {
            return null;
        }
        return new StandardLookupRequest.Scope(null, Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]),
                Integer.parseInt(coordinates[2]), Integer.parseInt(coordinates[3]), 0, null, 0, 0, null);
    }

    private static void startStandardLookup(CommandSender player, Command command, StandardLookupRequest request, LookupSession session) {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.ITALIC + Phrase.build(Phrase.LOOKUP_SEARCHING));
        new Thread(new StandardLookupThread(player, command, request, session)).start();
    }

    private static boolean isAsciiDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static PageSelection parsePageSelection(CommandSender player, String[] args, int defaultRows, int parsedRows, boolean parseArgPage) {
        boolean defaultRe = true;
        int page = 1;
        int rows = (parsedRows > 0) ? parsedRows : defaultRows;
        if (parseArgPage && args.length > 1) {
            String pages = args[1];
            if (pages.contains(":")) {
                String[] data = pages.split(":");
                pages = data[0];
                String results = data.length > 1 ? data[1] : "";
                results = results.replaceAll("[^0-9]", "");
                if (!results.isEmpty() && results.length() < 10) {
                    int r = Integer.parseInt(results);
                    if (r > 0) {
                        rows = r;
                        defaultRe = false;
                    }
                }
            }
            pages = pages.replaceAll("[^0-9]", "");
            if (!pages.isEmpty() && pages.length() < 10) {
                int p = Integer.parseInt(pages);
                if (p > 0) {
                    page = p;
                }
            }
        }
        if (rows > 1000) {
            rows = 1000;
        }
        if (rows > 100 && !(player instanceof ConsoleCommandSender)) {
            rows = 100;
        }
        return new PageSelection(page, rows, defaultRe);
    }

    private static final class PageSelection {
        private final int page;
        private final int rows;
        private final boolean defaultRows;

        private PageSelection(int page, int rows, boolean defaultRows) {
            this.page = page;
            this.rows = rows;
            this.defaultRows = defaultRows;
        }
    }
}
