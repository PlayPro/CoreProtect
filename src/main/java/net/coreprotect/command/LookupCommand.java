package net.coreprotect.command;

import java.sql.Connection;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

import com.google.common.base.Strings;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.database.lookup.BlockLookup;
import net.coreprotect.database.lookup.ChestTransactionLookup;
import net.coreprotect.database.lookup.InteractionLookup;
import net.coreprotect.database.lookup.PlayerLookup;
import net.coreprotect.database.lookup.SignMessageLookup;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatMessage;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public class LookupCommand {
    protected static void runCommand(CommandSender player, Command command, boolean permission, String[] args) {
        int resultc = args.length;
        args = CommandHandler.parsePage(args);
        Location lo = CommandHandler.parseLocation(player, args);
        // List<String> arg_uuids = new ArrayList<String>();
        List<String> argUsers = CommandHandler.parseUsers(args);
        Integer[] argRadius = CommandHandler.parseRadius(args, player, lo);
        int argNoisy = CommandHandler.parseNoisy(args);
        List<Integer> argAction = CommandHandler.parseAction(args);
        List<Object> argBlocks = CommandHandler.parseRestricted(player, args, argAction);
        List<Object> argExclude = CommandHandler.parseExcluded(player, args, argAction);
        List<String> argExcludeUsers = CommandHandler.parseExcludedUsers(player, args);
        String ts = CommandHandler.parseTimeString(args);
        long rbseconds = CommandHandler.parseTime(args);
        int argWid = CommandHandler.parseWorld(args, true, true);
        int parseRows = CommandHandler.parseRows(args);
        boolean count = CommandHandler.parseCount(args);
        boolean worldedit = CommandHandler.parseWorldEdit(args);
        boolean forceglobal = CommandHandler.parseForceGlobal(args);
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
        for (Object arg : argExclude) {
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
            String worldName = CommandHandler.parseWorldName(args, true);
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

        if (rbseconds <= 0 && !pageLookup && type == 4 && (argBlocks.size() > 0 || argUsers.size() > 0)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_LOOKUP_TIME, Selector.FIRST));
            return;
        }

        if (argAction.contains(4) && argAction.contains(11)) { // a:inventory
            argExclude.add(Material.FIRE);
            argExclude.add(Material.WATER);
            argExclude.add(Material.FARMLAND);
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

            String world = Util.getWorldName(wid);
            double dx = 0.5 * (x + x2);
            double dy = 0.5 * (y + y2);
            double dz = 0.5 * (z + z2);
            final Location location = new Location(Bukkit.getServer().getWorld(world), dx, dy, dz);
            final CommandSender player2 = player;
            final int p2 = p;
            final int finalLimit = re;

            class BasicThread implements Runnable {
                @Override
                public void run() {
                    try (Connection connection = Database.getConnection(true)) {
                        ConfigHandler.lookupThrottle.put(player2.getName(), new Object[] { true, System.currentTimeMillis() });
                        if (connection != null) {
                            Statement statement = connection.createStatement();
                            String blockdata = ChestTransactionLookup.performLookup(command.getName(), statement, location, player2, p2, finalLimit, false);
                            if (blockdata.contains("\n")) {
                                for (String b : blockdata.split("\n")) {
                                    Chat.sendComponent(player2, b);
                                }
                            }
                            else {
                                Chat.sendComponent(player2, blockdata);
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

                    ConfigHandler.lookupThrottle.put(player2.getName(), new Object[] { false, System.currentTimeMillis() });
                }
            }
            Runnable runnable = new BasicThread();
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

            String world = Util.getWorldName(wid);
            final Block fblock = Bukkit.getServer().getWorld(world).getBlockAt(x, y, z);// .getLocation();
            final BlockState fblockstate = fblock.getState();
            final CommandSender player2 = player;
            final int p2 = page;
            final int finalLimit = re;
            final int t = type;
            class BasicThread implements Runnable {
                @Override
                public void run() {
                    try (Connection connection = Database.getConnection(true)) {
                        ConfigHandler.lookupThrottle.put(player2.getName(), new Object[] { true, System.currentTimeMillis() });
                        if (connection != null) {
                            Statement statement = connection.createStatement();
                            if (t == 8) {
                                List<String> signData = SignMessageLookup.performLookup(command.getName(), statement, fblockstate.getLocation(), player2, p2, finalLimit);
                                for (String signMessage : signData) {
                                    String bypass = null;

                                    if (signMessage.contains("\n")) {
                                        String[] split = signMessage.split("\n");
                                        signMessage = split[0];
                                        bypass = split[1];
                                    }

                                    if (signMessage.length() > 0) {
                                        Chat.sendComponent(player2, signMessage, bypass);
                                    }
                                }
                            }
                            else {
                                String blockdata = null;
                                if (t == 7) {
                                    blockdata = InteractionLookup.performLookup(command.getName(), statement, fblock, player2, 0, p2, finalLimit);
                                }
                                else {
                                    blockdata = BlockLookup.performLookup(command.getName(), statement, fblockstate, player2, 0, p2, finalLimit);
                                }
                                if (blockdata.contains("\n")) {
                                    for (String b : blockdata.split("\n")) {
                                        Chat.sendComponent(player2, b);
                                    }
                                }
                                else if (blockdata.length() > 0) {
                                    Chat.sendComponent(player2, blockdata);
                                }
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

                    ConfigHandler.lookupThrottle.put(player2.getName(), new Object[] { false, System.currentTimeMillis() });
                }
            }
            Runnable runnable = new BasicThread();
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
                    List<Player> players = Bukkit.getServer().matchPlayer(ruser);
                    for (Player p : players) {
                        if (p.getName().equalsIgnoreCase(ruser)) {
                            rollbackusers.set(c, p.getName());
                        }
                    }
                    c++;
                }

                long cs = -1;
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
                    cs = Long.parseLong(data[4]);
                    // arg_radius = Integer.parseInt(data[5]);
                    argNoisy = Integer.parseInt(data[5]);
                    argExcluded = Integer.parseInt(data[6]);
                    argRestricted = Integer.parseInt(data[7]);
                    argWid = Integer.parseInt(data[8]);
                    if (defaultRe) {
                        re = Integer.parseInt(data[9]);
                    }

                    rollbackusers = ConfigHandler.lookupUlist.get(player.getName());
                    argBlocks = ConfigHandler.lookupBlist.get(player.getName());
                    argExclude = ConfigHandler.lookupElist.get(player.getName());
                    argExcludeUsers = ConfigHandler.lookupEUserlist.get(player.getName());
                    argAction = ConfigHandler.lookupAlist.get(player.getName());
                    argRadius = ConfigHandler.lookupRadius.get(player.getName());
                    ts = ConfigHandler.lookupTime.get(player.getName());
                    rbseconds = 1;
                }
                else {
                    if (lo != null) {
                        x = lo.getBlockX();
                        z = lo.getBlockZ();
                        wid = Util.getWorldId(lo.getWorld().getName());
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

                final List<String> rollbackusers2 = rollbackusers;
                long unixtimestamp = (System.currentTimeMillis() / 1000L);
                if (cs == -1) {
                    if (rbseconds <= 0) {
                        cs = 0;
                    }
                    else {
                        cs = unixtimestamp - rbseconds;
                    }
                }
                final long stime = cs;
                final Integer[] radius = argRadius;

                try {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.ITALIC + Phrase.build(Phrase.LOOKUP_SEARCHING));
                    final CommandSender player2 = player;
                    final int finalX = x;
                    final int finalY = y;
                    final int finalZ = z;
                    final int finalWid = wid;
                    final int finalArgWid = argWid;
                    final int noisy = argNoisy;
                    final String rtime = ts;
                    final int excluded = argExcluded;
                    final int restricted = argRestricted;
                    // final List<String> uuid_list = arg_uuids;
                    final List<Object> blist = argBlocks;
                    final List<Object> elist = argExclude;
                    final List<String> euserlist = argExcludeUsers;
                    final int page = pa;
                    final int displayResults = re;
                    final int typeLookup = type;
                    final Location finalLocation = lo;
                    final List<Integer> finalArgAction = argAction;
                    final boolean finalCount = count;

                    class BasicThread2 implements Runnable {
                        @Override
                        public void run() {
                            try (Connection connection = Database.getConnection(true)) {
                                ConfigHandler.lookupThrottle.put(player2.getName(), new Object[] { true, System.currentTimeMillis() });

                                List<String> uuidList = new ArrayList<>();
                                Location location = finalLocation;
                                boolean exists = false;
                                String bc = finalX + "." + finalY + "." + finalZ + "." + finalWid + "." + stime + "." + noisy + "." + excluded + "." + restricted + "." + finalArgWid + "." + displayResults;
                                ConfigHandler.lookupCommand.put(player2.getName(), bc);
                                ConfigHandler.lookupPage.put(player2.getName(), page);
                                ConfigHandler.lookupTime.put(player2.getName(), rtime);
                                ConfigHandler.lookupType.put(player2.getName(), 5);
                                ConfigHandler.lookupElist.put(player2.getName(), elist);
                                ConfigHandler.lookupEUserlist.put(player2.getName(), euserlist);
                                ConfigHandler.lookupBlist.put(player2.getName(), blist);
                                ConfigHandler.lookupUlist.put(player2.getName(), rollbackusers2);
                                ConfigHandler.lookupAlist.put(player2.getName(), finalArgAction);
                                ConfigHandler.lookupRadius.put(player2.getName(), radius);

                                if (connection != null) {
                                    Statement statement = connection.createStatement();
                                    String baduser = "";
                                    for (String check : rollbackusers2) {
                                        if ((!check.equals("#global") && !check.equals("#container")) || finalArgAction.contains(9)) {
                                            exists = PlayerLookup.playerExists(connection, check);
                                            if (!exists) {
                                                baduser = check;
                                                break;
                                            }
                                            else if (finalArgAction.contains(9)) {
                                                if (ConfigHandler.uuidCache.get(check.toLowerCase(Locale.ROOT)) != null) {
                                                    String uuid = ConfigHandler.uuidCache.get(check.toLowerCase(Locale.ROOT));
                                                    uuidList.add(uuid);
                                                }
                                            }
                                        }
                                        else {
                                            exists = true;
                                        }
                                    }
                                    if (exists) {
                                        for (String check : euserlist) {
                                            if (!check.equals("#global")) {
                                                exists = PlayerLookup.playerExists(connection, check);
                                                if (!exists) {
                                                    baduser = check;
                                                    break;
                                                }
                                            }
                                            else {
                                                baduser = "#global";
                                                exists = false;
                                            }
                                        }
                                    }

                                    if (exists) {
                                        List<String> userList = new ArrayList<>();
                                        if (!finalArgAction.contains(9)) {
                                            userList = rollbackusers2;
                                        }

                                        int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                                        boolean restrict_world = false;
                                        if (radius != null) {
                                            restrict_world = true;
                                        }
                                        if (location == null) {
                                            restrict_world = false;
                                        }
                                        if (finalArgWid > 0) {
                                            restrict_world = true;
                                            location = new Location(Bukkit.getServer().getWorld(Util.getWorldName(finalArgWid)), finalX, finalY, finalZ);
                                        }
                                        else if (location != null) {
                                            location = new Location(Bukkit.getServer().getWorld(Util.getWorldName(finalWid)), finalX, finalY, finalZ);
                                        }

                                        Long[] rowData = new Long[] { 0L, 0L, 0L, 0L };
                                        long rowMax = (long) page * displayResults;
                                        long pageStart = rowMax - displayResults;
                                        long rows = 0L;
                                        boolean checkRows = true;

                                        if (typeLookup == 5 && page > 1) {
                                            rowData = ConfigHandler.lookupRows.get(player2.getName());
                                            rows = rowData[3];

                                            if (pageStart < rows) {
                                                checkRows = false;
                                            }
                                        }

                                        if (checkRows) {
                                            rows = Lookup.countLookupRows(statement, player2, uuidList, userList, blist, elist, euserlist, finalArgAction, location, radius, rowData, stime, restrict_world, true);
                                            rowData[3] = rows;
                                            ConfigHandler.lookupRows.put(player2.getName(), rowData);
                                        }
                                        if (finalCount) {
                                            String row_format = NumberFormat.getInstance().format(rows);
                                            Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_ROWS_FOUND, row_format, (rows == 1 ? Selector.FIRST : Selector.SECOND)));
                                        }
                                        else if (pageStart < rows) {
                                            List<String[]> lookupList = Lookup.performPartialLookup(statement, player2, uuidList, userList, blist, elist, euserlist, finalArgAction, location, radius, rowData, stime, (int) pageStart, displayResults, restrict_world, true);

                                            Chat.sendMessage(player2, Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.LOOKUP_HEADER, "CoreProtect" + Color.WHITE + " | " + Color.DARK_AQUA) + Color.WHITE + " -----");
                                            if (finalArgAction.contains(6) || finalArgAction.contains(7)) { // Chat/command
                                                for (String[] data : lookupList) {
                                                    String time = data[0];
                                                    String dplayer = data[1];
                                                    String message = data[2];
                                                    String timeago = Util.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                                    Chat.sendComponent(player2, timeago + " " + Color.WHITE + "- " + Color.DARK_AQUA + dplayer + ": " + Color.WHITE, message);
                                                }
                                            }
                                            else if (finalArgAction.contains(8)) { // login/logouts
                                                for (String[] data : lookupList) {
                                                    String time = data[0];
                                                    String dplayer = data[1];
                                                    int wid = Integer.parseInt(data[2]);
                                                    int x = Integer.parseInt(data[3]);
                                                    int y = Integer.parseInt(data[4]);
                                                    int z = Integer.parseInt(data[5]);
                                                    int action = Integer.parseInt(data[6]);
                                                    String timeago = Util.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                                    int timeLength = 50 + (Util.getTimeSince(Integer.parseInt(time), unixtimestamp, false).replaceAll("[^0-9]", "").length() * 6);
                                                    String leftPadding = Color.BOLD + Strings.padStart("", 10, ' ');
                                                    if (timeLength % 4 == 0) {
                                                        leftPadding = Strings.padStart("", timeLength / 4, ' ');
                                                    }
                                                    else {
                                                        leftPadding = leftPadding + Color.WHITE + Strings.padStart("", (timeLength - 50) / 4, ' ');
                                                    }

                                                    String tag = (action != 0 ? Color.GREEN + "+" : Color.RED + "-");
                                                    Chat.sendComponent(player2, timeago + " " + tag + " " + Color.DARK_AQUA + Phrase.build(Phrase.LOOKUP_LOGIN, Color.DARK_AQUA + dplayer + Color.WHITE, (action != 0 ? Selector.FIRST : Selector.SECOND)));
                                                    Chat.sendComponent(player2, Color.WHITE + leftPadding + Color.GREY + "^ " + Util.getCoordinates(command.getName(), wid, x, y, z, true, true) + "");
                                                }
                                            }
                                            else if (finalArgAction.contains(9)) { // username-changes
                                                for (String[] data : lookupList) {
                                                    String time = data[0];
                                                    String user = ConfigHandler.uuidCacheReversed.get(data[1]);
                                                    String username = data[2];
                                                    String timeago = Util.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                                    Chat.sendComponent(player2, timeago + " " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_USERNAME, Color.DARK_AQUA + user + Color.WHITE, Color.DARK_AQUA + username + Color.WHITE));
                                                }
                                            }
                                            else if (finalArgAction.contains(10)) { // sign messages
                                                for (String[] data : lookupList) {
                                                    String time = data[0];
                                                    String dplayer = data[1];
                                                    int wid = Integer.parseInt(data[2]);
                                                    int x = Integer.parseInt(data[3]);
                                                    int y = Integer.parseInt(data[4]);
                                                    int z = Integer.parseInt(data[5]);
                                                    String message = data[6];
                                                    String timeago = Util.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                                    int timeLength = 50 + (Util.getTimeSince(Integer.parseInt(time), unixtimestamp, false).replaceAll("[^0-9]", "").length() * 6);
                                                    String leftPadding = Color.BOLD + Strings.padStart("", 10, ' ');
                                                    if (timeLength % 4 == 0) {
                                                        leftPadding = Strings.padStart("", timeLength / 4, ' ');
                                                    }
                                                    else {
                                                        leftPadding = leftPadding + Color.WHITE + Strings.padStart("", (timeLength - 50) / 4, ' ');
                                                    }

                                                    Chat.sendComponent(player2, timeago + " " + Color.WHITE + "- " + Color.DARK_AQUA + dplayer + ": " + Color.WHITE, message);
                                                    Chat.sendComponent(player2, Color.WHITE + leftPadding + Color.GREY + "^ " + Util.getCoordinates(command.getName(), wid, x, y, z, true, true) + "");
                                                }
                                            }
                                            else if (finalArgAction.contains(4) && finalArgAction.contains(11)) { // inventory transactions
                                                for (String[] data : lookupList) {
                                                    String time = data[0];
                                                    String dplayer = data[1];
                                                    String dtype = data[5];
                                                    int ddata = Integer.parseInt(data[6]);
                                                    int daction = Integer.parseInt(data[7]);
                                                    int amount = Integer.parseInt(data[10]);
                                                    String rbd = ((Integer.parseInt(data[8]) == 2 || Integer.parseInt(data[8]) == 3) ? Color.STRIKETHROUGH : "");
                                                    String timeago = Util.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                                    Material blockType = Util.itemFilter(Util.getType(Integer.parseInt(dtype)), (Integer.parseInt(data[13]) == 0));
                                                    String dname = Util.nameFilter(blockType.name().toLowerCase(Locale.ROOT), ddata);

                                                    String selector = Selector.FIRST;
                                                    String tag = Color.WHITE + "-";
                                                    if (daction == 2 || daction == 3) { // LOOKUP_ITEM
                                                        selector = (daction != 2 ? Selector.FIRST : Selector.SECOND);
                                                        tag = (daction != 2 ? Color.GREEN + "+" : Color.RED + "-");
                                                    }
                                                    else if (daction == 4 || daction == 5) { // LOOKUP_STORAGE
                                                        selector = (daction == 4 ? Selector.FIRST : Selector.SECOND);
                                                        tag = (daction == 4 ? Color.GREEN + "+" : Color.RED + "-");
                                                    }
                                                    else if (daction == 6 || daction == 7) { // LOOKUP_PROJECTILE
                                                        selector = Selector.SECOND;
                                                        tag = Color.RED + "-";
                                                    }
                                                    else if (daction == ItemLogger.ITEM_BREAK || daction == ItemLogger.ITEM_DESTROY || daction == ItemLogger.ITEM_CREATE) {
                                                        selector = (daction == ItemLogger.ITEM_CREATE ? Selector.FIRST : Selector.SECOND);
                                                        tag = (daction == ItemLogger.ITEM_CREATE ? Color.GREEN + "+" : Color.RED + "-");
                                                    }
                                                    else { // LOOKUP_CONTAINER
                                                        selector = (daction == 0 ? Selector.FIRST : Selector.SECOND);
                                                        tag = (daction == 0 ? Color.GREEN + "+" : Color.RED + "-");
                                                    }

                                                    Chat.sendComponent(player2, timeago + " " + tag + " " + Phrase.build(Phrase.LOOKUP_CONTAINER, Color.DARK_AQUA + rbd + dplayer + Color.WHITE + rbd, "x" + amount, Color.DARK_AQUA + rbd + dname + Color.WHITE, selector));
                                                }
                                            }
                                            else {
                                                for (String[] data : lookupList) {
                                                    int drb = Integer.parseInt(data[8]);
                                                    String rbd = "";
                                                    if (drb == 1 || drb == 3) {
                                                        rbd = Color.STRIKETHROUGH;
                                                    }

                                                    String time = data[0];
                                                    String dplayer = data[1];
                                                    int x = Integer.parseInt(data[2]);
                                                    int y = Integer.parseInt(data[3]);
                                                    int z = Integer.parseInt(data[4]);
                                                    String dtype = data[5];
                                                    int ddata = Integer.parseInt(data[6]);
                                                    int daction = Integer.parseInt(data[7]);
                                                    int wid = Integer.parseInt(data[9]);
                                                    int amount = Integer.parseInt(data[10]);
                                                    String tag = Color.WHITE + "-";

                                                    String timeago = Util.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                                    int timeLength = 50 + (Util.getTimeSince(Integer.parseInt(time), unixtimestamp, false).replaceAll("[^0-9]", "").length() * 6);
                                                    String leftPadding = Color.BOLD + Strings.padStart("", 10, ' ');
                                                    if (timeLength % 4 == 0) {
                                                        leftPadding = Strings.padStart("", timeLength / 4, ' ');
                                                    }
                                                    else {
                                                        leftPadding = leftPadding + Color.WHITE + Strings.padStart("", (timeLength - 50) / 4, ' ');
                                                    }

                                                    String dname = "";
                                                    boolean isPlayer = false;
                                                    if (daction == 3 && !finalArgAction.contains(11) && amount == -1) {
                                                        int dTypeInt = Integer.parseInt(dtype);
                                                        if (dTypeInt == 0) {
                                                            if (ConfigHandler.playerIdCacheReversed.get(ddata) == null) {
                                                                UserStatement.loadName(connection, ddata);
                                                            }
                                                            dname = ConfigHandler.playerIdCacheReversed.get(ddata);
                                                            isPlayer = true;
                                                        }
                                                        else {
                                                            dname = Util.getEntityType(dTypeInt).name();
                                                        }
                                                    }
                                                    else {
                                                        dname = Util.getType(Integer.parseInt(dtype)).name().toLowerCase(Locale.ROOT);
                                                        dname = Util.nameFilter(dname, ddata);
                                                    }
                                                    if (dname.length() > 0 && !isPlayer) {
                                                        dname = "minecraft:" + dname.toLowerCase(Locale.ROOT) + "";
                                                    }

                                                    // Hide "minecraft:" for now.
                                                    if (dname.contains("minecraft:")) {
                                                        String[] blockNameSplit = dname.split(":");
                                                        dname = blockNameSplit[1];
                                                    }

                                                    // Functions.sendMessage(player2, timeago+" " + ChatColors.WHITE + "- " + ChatColors.DARK_AQUA+rbd+""+dplayer+" " + ChatColors.WHITE+rbd+""+a+" " + ChatColors.DARK_AQUA+rbd+"#"+dtype+ChatColors.WHITE + ". " + ChatColors.GREY + "(x"+x+"/y"+y+"/z"+z+")");

                                                    Phrase phrase = Phrase.LOOKUP_BLOCK;
                                                    String selector = Selector.FIRST;
                                                    String action = "a:block";
                                                    if (finalArgAction.contains(4) || finalArgAction.contains(5) || finalArgAction.contains(11) || amount > -1) {
                                                        if (daction == 2 || daction == 3) {
                                                            phrase = Phrase.LOOKUP_ITEM; // {picked up|dropped}
                                                            selector = (daction != 2 ? Selector.FIRST : Selector.SECOND);
                                                            tag = (daction != 2 ? Color.GREEN + "+" : Color.RED + "-");
                                                            action = "a:item";
                                                        }
                                                        else if (daction == 4 || daction == 5) {
                                                            phrase = Phrase.LOOKUP_STORAGE; // {deposited|withdrew}
                                                            selector = (daction != 4 ? Selector.FIRST : Selector.SECOND);
                                                            tag = (daction != 4 ? Color.RED + "-" : Color.GREEN + "+");
                                                            action = "a:item";
                                                        }
                                                        else if (daction == 6 || daction == 7) {
                                                            phrase = Phrase.LOOKUP_PROJECTILE; // {threw|shot}
                                                            selector = (daction != 7 ? Selector.FIRST : Selector.SECOND);
                                                            tag = Color.RED + "-";
                                                            action = "a:item";
                                                        }
                                                        else {
                                                            phrase = Phrase.LOOKUP_CONTAINER; // {added|removed}
                                                            selector = (daction != 0 ? Selector.FIRST : Selector.SECOND);
                                                            tag = (daction != 0 ? Color.GREEN + "+" : Color.RED + "-");
                                                            action = "a:container";
                                                        }

                                                        Chat.sendComponent(player2, timeago + " " + tag + " " + Phrase.build(phrase, Color.DARK_AQUA + rbd + dplayer + Color.WHITE + rbd, "x" + amount, Color.DARK_AQUA + rbd + dname + Color.WHITE, selector));
                                                    }
                                                    else {
                                                        if (daction == 2 || daction == 3) {
                                                            phrase = Phrase.LOOKUP_INTERACTION; // {clicked|killed}
                                                            selector = (daction != 3 ? Selector.FIRST : Selector.SECOND);
                                                            tag = (daction != 3 ? Color.WHITE + "-" : Color.RED + "-");
                                                            action = (daction == 2 ? "a:click" : "a:kill");
                                                        }
                                                        else {
                                                            phrase = Phrase.LOOKUP_BLOCK; // {placed|broke}
                                                            selector = (daction != 0 ? Selector.FIRST : Selector.SECOND);
                                                            tag = (daction != 0 ? Color.GREEN + "+" : Color.RED + "-");
                                                        }

                                                        Chat.sendComponent(player2, timeago + " " + tag + " " + Phrase.build(phrase, Color.DARK_AQUA + rbd + dplayer + Color.WHITE + rbd, Color.DARK_AQUA + rbd + dname + Color.WHITE, selector));
                                                    }

                                                    action = (finalArgAction.size() == 0 ? " (" + action + ")" : "");
                                                    Chat.sendComponent(player2, Color.WHITE + leftPadding + Color.GREY + "^ " + Util.getCoordinates(command.getName(), wid, x, y, z, true, true) + Color.GREY + Color.ITALIC + action);
                                                }
                                            }
                                            if (rows > displayResults) {
                                                int total_pages = (int) Math.ceil(rows / (displayResults + 0.0));
                                                if (finalArgAction.contains(6) || finalArgAction.contains(7) || finalArgAction.contains(9) || (finalArgAction.contains(4) && finalArgAction.contains(11))) {
                                                    Chat.sendMessage(player2, "-----");
                                                }
                                                Chat.sendComponent(player2, Util.getPageNavigation(command.getName(), page, total_pages));
                                            }
                                        }
                                        else if (rows > 0) {
                                            Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.FIRST));
                                        }
                                        else {
                                            Chat.sendMessage(player2, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
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
