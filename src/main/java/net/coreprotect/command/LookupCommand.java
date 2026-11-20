package net.coreprotect.command;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
import net.coreprotect.command.lookup.LookupWorker;
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
    private static final String PAGE_PREFIX = "page:";
    private static final Map<Integer, String> LOOKUP_ACTION_PERMISSIONS = Map.of(
            LookupActions.INTERACTION, "coreprotect.lookup.click",
            LookupActions.ENTITY_KILL, "coreprotect.lookup.kill",
            LookupActions.ENTITY_SPAWN, "coreprotect.lookup.spawn",
            LookupActions.CHAT, "coreprotect.lookup.chat",
            LookupActions.COMMAND, "coreprotect.lookup.command",
            LookupActions.SESSION, "coreprotect.lookup.session",
            LookupActions.USERNAME, "coreprotect.lookup.username",
            LookupActions.SIGN, "coreprotect.lookup.sign");

    public static void runCommand(CommandSender player, Command command, boolean permission, String[] args) {
        int resultc = args.length;
        if (resultc == 0) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co l <params>"));
            return;
        }
        if (handlePagination(player, command, permission, args)) {
            return;
        }
        MessageFilterParser.ParseResult messageFilterResult = CommandParser.parseMessageFilters(args);
        args = messageFilterResult.getArguments();
        List<String> argFilters = messageFilterResult.getFilters();
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
        String incompatibleAction = actionResult.getIncompatibleAction();
        if (incompatibleAction != null) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INCOMPATIBLE_ACTION, "a:" + incompatibleAction));
            return;
        }
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

        if (argBlocks == null || argExclude == null || argExcludeUsers == null) {
            return;
        }

        if (rollbackStateResult.isConflicting()) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_PARAMETER, "#rolledback + #restored"));
            return;
        }

        if (!CommandParser.validateTargetCombination(player, argBlocks, argExclude.keySet(), argAction, argEntityActionFilter, true)) {
            return;
        }

        if ("".equals(worldFilterName)) {
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, worldFilterName)).build());
            return;
        }

        boolean pageLookup = isTextOrSessionLookup(argAction);

        if (!permission) {
            if (!pageLookup || !player.hasPermission("coreprotect.inspect")) {
                Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.NO_PERMISSION)).build());
                return;
            }
        }
        if (!checkMaintenance(player)) {
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
        if (!checkLookupThrottle(player)) {
            return;
        }
        boolean near = args[0].equals("near") && player.hasPermission("coreprotect.lookup.near");
        if (!near && !hasLookupActionPermissions(player, argAction)) {
            sendNoPermission(player);
            return;
        }
        if (pageLookup) {
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

        if (startTime <= 0 && argRadius == null && !pageLookup && (argBlocks.size() > 0 || argUsers.size() > 0)) {
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

        int defaultRows = outputMode == LookupOutputMode.SUMMARY ? 8 : 4;
        if (outputMode != LookupOutputMode.SUMMARY && (argAction.contains(LookupActions.CHAT) || argAction.contains(LookupActions.COMMAND)
                || argAction.contains(LookupActions.USERNAME) || LookupActions.isInventoryLookup(argAction))) {
            defaultRows = 7;
        }
        int rows = limitRows(player, parseRows > 0 ? parseRows : defaultRows);

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
                    if (!near && !player.hasPermission("coreprotect.lookup.container")) {
                        sendNoPermission(player);
                        return;
                    }
                    x = container.x;
                    y = container.y;
                    z = container.z;
                    wid = container.worldId;
                    lo = container.location();
                    if (lo == null) {
                        lo = new Location(null, x, y, z);
                    }
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
                StandardLookupRequest.Origin origin = near ? StandardLookupRequest.Origin.NEAR
                        : permission ? StandardLookupRequest.Origin.LOOKUP : StandardLookupRequest.Origin.INSPECT;
                startStandardLookup(player, command, new StandardLookupRequest(filters, scope, 1, rows, outputMode, rollbackState, origin), null);
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

    private static boolean isTextOrSessionLookup(List<Integer> actions) {
        return actions.contains(LookupActions.CHAT) || actions.contains(LookupActions.COMMAND)
                || actions.contains(LookupActions.SESSION) || actions.contains(LookupActions.USERNAME) || actions.contains(LookupActions.SIGN);
    }

    private static boolean hasLookupActionPermissions(CommandSender player, List<Integer> actions) {
        boolean inspectedContainer = actions.contains(LookupActions.INSPECT_CONTAINER);
        boolean blocks = !isTextOrSessionLookup(actions) && (actions.isEmpty() || (inspectedContainer && actions.size() == 1)
                || actions.contains(LookupActions.BLOCK_BREAK) || actions.contains(LookupActions.BLOCK_PLACE));
        if (blocks && !player.hasPermission("coreprotect.lookup.block")) {
            return false;
        }

        boolean container = actions.contains(LookupActions.CONTAINER);
        boolean item = actions.contains(LookupActions.ITEM);
        if ((inspectedContainer || (container && !item)) && !player.hasPermission("coreprotect.lookup.container")) {
            return false;
        }
        if (item && !player.hasPermission(container ? "coreprotect.lookup.inventory" : "coreprotect.lookup.item")) {
            return false;
        }

        for (Map.Entry<Integer, String> permission : LOOKUP_ACTION_PERMISSIONS.entrySet()) {
            if (actions.contains(permission.getKey()) && !player.hasPermission(permission.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean canResumeStandardLookup(CommandSender player, StandardLookupRequest request) {
        if (request.origin == StandardLookupRequest.Origin.NEAR) {
            return player.hasPermission("coreprotect.lookup.near");
        }
        String permission = request.origin == StandardLookupRequest.Origin.INSPECT ? "coreprotect.inspect" : "coreprotect.lookup";
        return player.hasPermission(permission) && hasLookupActionPermissions(player, request.filters.actions);
    }

    private static boolean requireInspectPermission(CommandSender player) {
        if (player.hasPermission("coreprotect.inspect")) {
            return true;
        }
        sendNoPermission(player);
        return false;
    }

    private static void sendNoPermission(CommandSender player) {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
    }

    private static boolean handlePagination(CommandSender player, Command command, boolean permission, String[] args) {
        String argument = args.length > 1 ? args[1].trim() : "";
        boolean explicitPage = argument.regionMatches(true, 0, PAGE_PREFIX, 0, PAGE_PREFIX.length());
        if (!args[0].equalsIgnoreCase("page") && !explicitPage && (args.length != 2 || !looksLikePage(argument))) {
            return false;
        }

        if (!permission && !player.hasPermission("coreprotect.inspect") && !player.hasPermission("coreprotect.lookup.near")) {
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.NO_PERMISSION)).build());
            return true;
        }
        PageSelection page = args.length == 2 ? parsePageSelection(explicitPage ? argument.substring(PAGE_PREFIX.length()) : argument) : null;
        if (page == null) {
            sendPageUsage(player);
        }
        else if (checkMaintenance(player) && checkLookupThrottle(player)) {
            resumeLookup(player, command, page);
        }
        return true;
    }

    private static boolean looksLikePage(String argument) {
        int separator = argument.indexOf(':');
        if (separator >= 0 && Character.isDigit(argument.charAt(0))) {
            return true;
        }
        int end = separator >= 0 ? separator : argument.length();
        for (int i = 0; i < end; i++) {
            char c = argument.charAt(i);
            if (Character.isLetter(c) || c == '_') {
                return false;
            }
        }
        return true;
    }

    private static void resumeLookup(CommandSender player, Command command, PageSelection page) {
        String name = player.getName();
        Integer type = ConfigHandler.lookupType.get(name);
        if (type == null) {
            sendNoSavedLookup(player);
        }
        else if (type == LookupSession.STANDARD_LOOKUP) {
            LookupSession session = ConfigHandler.lookupSessions.get(name);
            if (session == null) {
                sendNoSavedLookup(player);
                return;
            }
            if (!canResumeStandardLookup(player, session.request)) {
                sendNoPermission(player);
                return;
            }
            int rows = pageRows(player, page, session.request.pageSize);
            if (rows == 0) {
                return;
            }
            if ((long) page.page * rows > Integer.MAX_VALUE) {
                sendPageUsage(player);
                return;
            }
            startStandardLookup(player, command, session.request.forPage(page.page, rows), session);
        }
        else if (type == 9) {
            Integer entity = ConfigHandler.lookupEntityInteraction.get(name);
            if (entity == null) {
                sendNoSavedLookup(player);
                return;
            }
            if (!requireInspectPermission(player)) {
                return;
            }
            int rows = pageRows(player, page, 7);
            if (rows > 0) {
                LookupWorker lookup;
                synchronized (ConfigHandler.lookupThrottle) {
                    if (!entity.equals(ConfigHandler.lookupEntityInteraction.get(name))) {
                        return;
                    }
                    lookup = new EntityInteractionLookupThread(player, command, page.page, rows);
                }
                lookup.start();
            }
        }
        else if (type == 1 || type == 2 || type == 3 || type == 7 || type == 8) {
            resumeBlockLookup(player, command, page, type, name);
        }
        else {
            sendNoSavedLookup(player);
        }
    }

    private static void resumeBlockLookup(CommandSender player, Command command, PageSelection page, int type, String name) {
        String history = ConfigHandler.lookupCommand.get(name);
        if (history == null) {
            sendNoSavedLookup(player);
            return;
        }
        if (!requireInspectPermission(player)) {
            return;
        }

        String[] data = history.split("\\.", -1);
        int rowIndex = type == 1 ? 7 : 5;
        if (data.length <= rowIndex) {
            sendNoSavedLookup(player);
            return;
        }

        try {
            int x = Integer.parseInt(data[0]);
            int y = Integer.parseInt(data[1]);
            int z = Integer.parseInt(data[2]);
            String worldName = WorldUtils.getWorldName(Integer.parseInt(data[3]));
            int rows = pageRows(player, page, Integer.parseInt(data[rowIndex]));
            if (rows == 0) {
                return;
            }

            World world = Bukkit.getServer().getWorld(worldName);
            if (world == null) {
                Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, worldName)).build());
                return;
            }

            Supplier<LookupWorker> worker;
            if (type == 1) {
                Location location = containerHistoryLocation(world, x, y, z, data);
                worker = () -> new ChestTransactionLookupThread(player, command, location, page.page, rows);
            }
            else {
                Integer entitySpawnRowId = type == 2 && data.length > 6 ? Integer.valueOf(data[6]) : null;
                Block block = world.getBlockAt(x, y, z);
                BlockState blockState = block.getState();
                worker = () -> new BlockLookupThread(player, command, block, blockState, page.page, rows, type, entitySpawnRowId);
            }

            LookupWorker lookup;
            synchronized (ConfigHandler.lookupThrottle) {
                if (!history.equals(ConfigHandler.lookupCommand.get(name))) {
                    return;
                }
                lookup = worker.get();
                data[rowIndex] = Integer.toString(rows);
                ConfigHandler.lookupCommand.put(name, String.join(".", data));
            }
            lookup.start();
        }
        catch (NumberFormatException e) {
            sendNoSavedLookup(player);
        }
    }

    private static boolean checkMaintenance(CommandSender player) {
        if (ConfigHandler.converterRunning || ConfigHandler.migrationRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
            return false;
        }
        if (ConfigHandler.purgeRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
            return false;
        }
        return true;
    }

    private static boolean checkLookupThrottle(CommandSender player) {
        Object[] throttle = ConfigHandler.lookupThrottle.get(player.getName());
        if (throttle != null && ((boolean) throttle[0] || System.currentTimeMillis() - (long) throttle[1] < 50)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
            return false;
        }
        return true;
    }

    private static void sendPageUsage(CommandSender player) {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, Color.WHITE, "/co page <page>"));
    }

    private static void sendNoSavedLookup(CommandSender player) {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
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
        int x = Integer.parseInt(coordinates[0]);
        int y = Integer.parseInt(coordinates[1]);
        int z = Integer.parseInt(coordinates[2]);
        Location location = containerHistoryLocation(null, x, y, z, coordinates);
        return new StandardLookupRequest.Scope(location, x, y, z, Integer.parseInt(coordinates[3]), 0, null, 0, 0, null);
    }

    private static Location containerHistoryLocation(World world, int x, int y, int z, String[] coordinates) {
        if (coordinates.length < 7) {
            return new Location(world, x, y, z);
        }
        double centerX = (x + (long) Integer.parseInt(coordinates[4])) / 2.0;
        double centerY = (y + (long) Integer.parseInt(coordinates[5])) / 2.0;
        double centerZ = (z + (long) Integer.parseInt(coordinates[6])) / 2.0;
        return new Location(world, centerX, centerY, centerZ);
    }

    private static void startStandardLookup(CommandSender player, Command command, StandardLookupRequest request, LookupSession session) {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.ITALIC + Phrase.build(Phrase.LOOKUP_SEARCHING));
        StandardLookupThread lookup = new StandardLookupThread(player, command, request, session);
        try {
            new Thread(lookup).start();
        }
        catch (RuntimeException | Error e) {
            lookup.cancel();
            throw e;
        }
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

    private static PageSelection parsePageSelection(String input) {
        int separator = input.indexOf(':');
        int page = parsePositiveInt(separator < 0 ? input : input.substring(0, separator));
        int rows = separator < 0 ? 0 : parsePositiveInt(input.substring(separator + 1));
        return page == 0 || (separator >= 0 && rows == 0) ? null : new PageSelection(page, rows);
    }

    static int parsePositiveInt(String input) {
        if (!isAsciiDigits(input)) {
            return 0;
        }
        try {
            return Integer.parseInt(input);
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int limitRows(CommandSender player, int rows) {
        return Math.min(rows, player instanceof ConsoleCommandSender ? 1000 : 100);
    }

    private static int pageRows(CommandSender player, PageSelection page, int defaultRows) {
        int rows = limitRows(player, page.rows > 0 ? page.rows : defaultRows);
        if (rows <= 0) {
            sendNoSavedLookup(player);
            return 0;
        }
        return rows;
    }

    private static final class PageSelection {
        private final int page;
        private final int rows;

        private PageSelection(int page, int rows) {
            this.page = page;
            this.rows = rows;
        }
    }
}
