package net.coreprotect.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.coreprotect.command.lookup.BlockLookupThread;
import net.coreprotect.command.lookup.ChestTransactionLookupThread;
import net.coreprotect.command.lookup.StandardLookupThread;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatMessage;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.WorldUtils;

public class LookupCommand {
    public static void runCommand(CommandSender player, Command command, boolean permission, String[] args) {
        int resultc = args.length;
        args = CommandParser.parsePage(args);
        Location lo = CommandParser.parseLocation(player, args);
        // List<String> arg_uuids = new ArrayList<String>();
        List<String> argUsers = CommandParser.parseUsers(args);
        Integer[] argRadius = CommandParser.parseRadius(args, player, lo);
        int argNoisy = CommandParser.parseNoisy(args);
        List<Integer> argAction = CommandParser.parseAction(args);
        List<Object> argBlocks = CommandParser.parseRestricted(player, args, argAction);
        Map<Object, Boolean> argExclude = CommandParser.parseExcluded(player, args, argAction);
        List<String> argExcludeUsers = CommandParser.parseExcludedUsers(player, args);
        String ts = CommandParser.parseTimeString(args);
        long[] argTime = CommandParser.parseTime(args);
        long startTime = argTime[0];
        long endTime = argTime[1];
        int argWid = CommandParser.parseWorld(args, true, true);
        int parseRows = CommandParser.parseRows(args);
        boolean count = CommandParser.parseCount(args);
        boolean worldedit = CommandParser.parseWorldEdit(args);
        boolean forceglobal = CommandParser.parseForceGlobal(args);
        boolean pageLookup = false;

        if (argBlocks == null || argExclude == null || argExcludeUsers == null) {
            return;
        }

        if (args[0].toLowerCase(Locale.ROOT).equals("page") && (args.length != 2 || !args[1].equals(args[1].replaceAll("[^0-9]", "")))) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, Color.WHITE, "/co page <page>"));
            return;
        }

        int argExcluded = argExclude.size();
        int argRestricted = argBlocks.size();

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

        if (argWid == -1) {
            String worldName = CommandParser.parseWorldName(args, true);
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, worldName)).build());
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
        if (argAction.contains(6) || argAction.contains(7) || argAction.contains(8) || argAction.contains(9) || argAction.contains(10)) {
            pageLookup = true;
        }

        if (!permission) {
            if (!pageLookup || !player.hasPermission("coreprotect.inspect")) {
                Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.NO_PERMISSION)).build());
                return;
            }
        }
        if (ConfigHandler.converterRunning) {
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
            if (!pageLookup && (argAction.size() == 0 || (argAction.size() == 1 && (argAction.contains(0) || argAction.contains(1)))) && !player.hasPermission("coreprotect.lookup.block")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(2) && !player.hasPermission("coreprotect.lookup.click")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(3) && !player.hasPermission("coreprotect.lookup.kill")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(4) && !argAction.contains(11) && !player.hasPermission("coreprotect.lookup.container")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(6) && !player.hasPermission("coreprotect.lookup.chat")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(7) && !player.hasPermission("coreprotect.lookup.command")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(8) && !player.hasPermission("coreprotect.lookup.session")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(9) && !player.hasPermission("coreprotect.lookup.username")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(10) && !player.hasPermission("coreprotect.lookup.sign")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(11) && !argAction.contains(4) && !player.hasPermission("coreprotect.lookup.item")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
            if (argAction.contains(4) && argAction.contains(11) && !player.hasPermission("coreprotect.lookup.inventory")) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                return;
            }
        }
        if (argAction.contains(6) || argAction.contains(7) || argAction.contains(8) || argAction.contains(9) || argAction.contains(10)) {
            if (argAction.contains(9) && (argRadius != null || argWid > 0 || worldedit)) {
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

        if (startTime <= 0 && !pageLookup && type == 4 && (argBlocks.size() > 0 || argUsers.size() > 0)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_LOOKUP_TIME, Selector.FIRST));
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

        if (type == 1) {
            boolean defaultRe = true;
            int p = 0;
            int re = 7;
            if (parseRows > 0) {
                re = parseRows;
            }
            if (resultc > 1) {
                String pages = args[1];
                if (pages.contains(":")) {
                    String[] data = pages.split(":");
                    pages = data[0];
                    String results = "";
                    if (data.length > 1) {
                        results = data[1];
                    }
                    results = results.replaceAll("[^0-9]", "");
                    if (results.length() > 0 && results.length() < 10) {
                        int r = Integer.parseInt(results);
                        if (r > 0) {
                            re = r;
                            defaultRe = false;
                        }
                    }
                }
                pages = pages.replaceAll("[^0-9]", "");
                if (pages.length() > 0 && pages.length() < 10) {
                    int pa = Integer.parseInt(pages);
                    if (pa > 0) {
                        p = pa;
                    }
                }
            }

            if (re > 1000) {
                re = 1000;
            }
            if (re > 100 && !(player instanceof ConsoleCommandSender)) {
                re = 100;
            }

            if (p <= 0) {
                p = 1;
            }
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
            double dx = 0.5 * (x + x2);
            double dy = 0.5 * (y + y2);
            double dz = 0.5 * (z + z2);
            final Location location = new Location(Bukkit.getServer().getWorld(world), dx, dy, dz);

            Runnable runnable = new ChestTransactionLookupThread(player, command, location, p, re);
            Thread thread = new Thread(runnable);
            thread.start();
        }
        else if (type == 2 || type == 3 || type == 7 || type == 8) {
            boolean defaultRe = true;
            int page = 1;
            int re = 7;
            if (parseRows > 0) {
                re = parseRows;
            }
            if (resultc > 1) {
                String pages = args[1];
                if (pages.contains(":")) {
                    String[] data = pages.split(":");
                    pages = data[0];
                    String results = "";
                    if (data.length > 1) {
                        results = data[1];
                    }
                    results = results.replaceAll("[^0-9]", "");
                    if (results.length() > 0 && results.length() < 10) {
                        int r = Integer.parseInt(results);
                        if (r > 0) {
                            re = r;
                            defaultRe = false;
                        }
                    }
                }
                pages = pages.replaceAll("[^0-9]", "");
                if (pages.length() > 0 && pages.length() < 10) {
                    int p = Integer.parseInt(pages);
                    if (p > 0) {
                        page = p;
                    }
                }
            }

            if (re > 1000) {
                re = 1000;
            }
            if (re > 100 && !(player instanceof ConsoleCommandSender)) {
                re = 100;
            }

            // String bc = x+"."+y+"."+z+"."+wid+"."+rstring+"."+lookup_user;
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
            ConfigHandler.lookupCommand.put(player.getName(), bc);

            String world = WorldUtils.getWorldName(wid);
            final Block block = Bukkit.getServer().getWorld(world).getBlockAt(x, y, z);
            final BlockState blockState = block.getState();

            Runnable runnable = new BlockLookupThread(player, command, block, blockState, page, re, type);
            Thread thread = new Thread(runnable);
            thread.start();
        }
        else if (type == 4 || type == 5) {
            boolean defaultRe = true;
            int pa = 1;
            int re = 4;
            if (argAction.contains(6) || argAction.contains(7) || argAction.contains(9) || (argAction.contains(4) && argAction.contains(11))) {
                re = 7;
            }
            if (parseRows > 0) {
                re = parseRows;
            }
            if (type == 5) {
                if (resultc > 1) {
                    String pages = args[1];
                    if (pages.contains(":")) {
                        String[] data = pages.split(":");
                        pages = data[0];
                        String results = "";
                        if (data.length > 1) {
                            results = data[1];
                        }
                        results = results.replaceAll("[^0-9]", "");
                        if (results.length() > 0 && results.length() < 10) {
                            int r = Integer.parseInt(results);
                            if (r > 0) {
                                re = r;
                                defaultRe = false;
                            }
                        }
                    }
                    pages = pages.replaceAll("[^0-9]", "");
                    if (pages.length() > 0 && pages.length() < 10) {
                        int p = Integer.parseInt(pages);
                        if (p > 0) {
                            pa = p;
                        }
                    }
                }
            }

            if (re > 1000) {
                re = 1000;
            }
            if (re > 100 && !(player instanceof ConsoleCommandSender)) {
                re = 100;
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

                    if (argAction.contains(4) && argAction.contains(11)) {
                        if (ruser.startsWith("#")) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_USERNAME, ruser));
                            return;
                        }
                    }
                }

                long timeStart = -1;
                long timeEnd = 0;
                int x = 0;
                int y = 0;
                int z = 0;
                int wid = 0;

                if (type == 5) {
                    String lcommand = ConfigHandler.lookupCommand.get(player.getName());
                    String[] data = lcommand.split("\\.");
                    x = Integer.parseInt(data[0]);
                    y = Integer.parseInt(data[1]);
                    z = Integer.parseInt(data[2]);
                    wid = Integer.parseInt(data[3]);
                    timeStart = Long.parseLong(data[4]);
                    timeEnd = Long.parseLong(data[5]);
                    argNoisy = Integer.parseInt(data[6]);
                    argExcluded = Integer.parseInt(data[7]);
                    argRestricted = Integer.parseInt(data[8]);
                    argWid = Integer.parseInt(data[9]);
                    if (defaultRe) {
                        re = Integer.parseInt(data[10]);
                    }

                    rollbackusers = ConfigHandler.lookupUlist.get(player.getName());
                    argBlocks = ConfigHandler.lookupBlist.get(player.getName());
                    argExclude = ConfigHandler.lookupElist.get(player.getName());
                    argExcludeUsers = ConfigHandler.lookupEUserlist.get(player.getName());
                    argAction = ConfigHandler.lookupAlist.get(player.getName());
                    argRadius = ConfigHandler.lookupRadius.get(player.getName());
                    ts = ConfigHandler.lookupTime.get(player.getName());
                    startTime = 1;
                    endTime = 0;
                }
                else {
                    if (lo != null) {
                        x = lo.getBlockX();
                        z = lo.getBlockZ();
                        if (lo.getWorld() != null) {
                            wid = WorldUtils.getWorldId(lo.getWorld().getName());
                        }
                    }

                    if (rollbackusers.size() == 1 && rollbackusers.contains("#global") && argAction.contains(9)) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co l a:username u:<user>"));
                        return;
                    }

                    if (rollbackusers.contains("#container")) {
                        if (argAction.contains(6) || argAction.contains(7) || argAction.contains(8) || argAction.contains(9) || argAction.contains(10) || argAction.contains(11)) {
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
                            if (!player.hasPermission("coreprotect.lookup.container") && !allPermission) {
                                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                                return;
                            }
                            String lcommand = ConfigHandler.lookupCommand.get(player.getName());
                            String[] data = lcommand.split("\\.");
                            x = Integer.parseInt(data[0]);
                            y = Integer.parseInt(data[1]);
                            z = Integer.parseInt(data[2]);
                            wid = Integer.parseInt(data[3]);
                            argAction.add(5);
                            argRadius = null;
                            argWid = 0;
                        }
                        else {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_CONTAINER));
                            return;
                        }
                    }
                }

                try {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.ITALIC + Phrase.build(Phrase.LOOKUP_SEARCHING));

                    if (timeStart == -1) {
                        if (startTime <= 0) {
                            timeStart = 0;
                        }
                        else {
                            timeStart = (System.currentTimeMillis() / 1000L) - startTime;
                        }
                        if (endTime <= 0) {
                            timeEnd = 0;
                        }
                        else {
                            timeEnd = (System.currentTimeMillis() / 1000L) - endTime;
                        }
                    }

                    Runnable runnable = new StandardLookupThread(player, command, rollbackusers, argBlocks, argExclude, argExcludeUsers, argAction, argRadius, lo, x, y, z, wid, argWid, timeStart, timeEnd, argNoisy, argExcluded, argRestricted, pa, re, type, ts, count);
                    Thread thread = new Thread(runnable);
                    thread.start();
                }
                catch (Exception e) {
                    e.printStackTrace();
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
}
