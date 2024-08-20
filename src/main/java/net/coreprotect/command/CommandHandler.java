package net.coreprotect.command;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.NetworkHandler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public class CommandHandler implements CommandExecutor {
    private static CommandHandler instance;
    private static ConcurrentHashMap<String, Boolean> versionAlert = new ConcurrentHashMap<>();

    public static CommandHandler getInstance() {
        if (instance == null) {
            instance = new CommandHandler();
        }
        return instance;
    }

    protected static String[] parsePage(String[] argumentArray) {
        if (argumentArray.length == 2) {
            argumentArray[1] = argumentArray[1].replaceFirst("page:", "");
        }

        return argumentArray;
    }

    protected static List<Integer> parseAction(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        List<Integer> result = new ArrayList<>();
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("a:") || argument.equals("action:")) {
                    next = 1;
                }
                else if (next == 1 || argument.startsWith("a:") || argument.startsWith("action:")) {
                    result.clear();
                    argument = argument.replaceAll("action:", "");
                    argument = argument.replaceAll("a:", "");
                    if (argument.startsWith("#")) {
                        argument = argument.replaceFirst("#", "");
                    }
                    if (argument.equals("broke") || argument.equals("break") || argument.equals("remove") || argument.equals("destroy") || argument.equals("block-break") || argument.equals("block-remove") || argument.equals("-block") || argument.equals("-blocks") || argument.equals("block-")) {
                        result.add(0);
                    }
                    else if (argument.equals("placed") || argument.equals("place") || argument.equals("block-place") || argument.equals("+block") || argument.equals("+blocks") || argument.equals("block+")) {
                        result.add(1);
                    }
                    else if (argument.equals("block") || argument.equals("blocks") || argument.equals("block-change") || argument.equals("change") || argument.equals("changes")) {
                        result.add(0);
                        result.add(1);
                    }
                    else if (argument.equals("click") || argument.equals("clicks") || argument.equals("interact") || argument.equals("interaction") || argument.equals("player-interact") || argument.equals("player-interaction") || argument.equals("player-click")) {
                        result.add(2);
                    }
                    else if (argument.equals("death") || argument.equals("deaths") || argument.equals("entity-death") || argument.equals("entity-deaths") || argument.equals("kill") || argument.equals("kills") || argument.equals("entity-kill") || argument.equals("entity-kills")) {
                        result.add(3);
                    }
                    else if (argument.equals("container") || argument.equals("container-change") || argument.equals("containers") || argument.equals("chest") || argument.equals("transaction") || argument.equals("transactions")) {
                        result.add(4);
                    }
                    else if (argument.equals("-container") || argument.equals("container-") || argument.equals("remove-container")) {
                        result.add(4);
                        result.add(0);
                    }
                    else if (argument.equals("+container") || argument.equals("container+") || argument.equals("container-add") || argument.equals("add-container")) {
                        result.add(4);
                        result.add(1);
                    }
                    else if (argument.equals("chat") || argument.equals("chats")) {
                        result.add(6);
                    }
                    else if (argument.equals("command") || argument.equals("commands")) {
                        result.add(7);
                    }
                    else if (argument.equals("logins") || argument.equals("login") || argument.equals("+session") || argument.equals("+sessions") || argument.equals("session+") || argument.equals("+connection") || argument.equals("connection+")) {
                        result.add(8);
                        result.add(1);
                    }
                    else if (argument.equals("logout") || argument.equals("logouts") || argument.equals("-session") || argument.equals("-sessions") || argument.equals("session-") || argument.equals("-connection") || argument.equals("connection-")) {
                        result.add(8);
                        result.add(0);
                    }
                    else if (argument.equals("session") || argument.equals("sessions") || argument.equals("connection") || argument.equals("connections")) {
                        result.add(8);
                    }
                    else if (argument.equals("username") || argument.equals("usernames") || argument.equals("user") || argument.equals("users") || argument.equals("name") || argument.equals("names") || argument.equals("uuid") || argument.equals("uuids") || argument.equals("username-change") || argument.equals("username-changes") || argument.equals("name-change") || argument.equals("name-changes")) {
                        result.add(9);
                    }
                    else if (argument.equals("sign") || argument.equals("signs")) {
                        result.add(10);
                    }
                    else if (argument.equals("inv") || argument.equals("inventory") || argument.equals("inventories")) {
                        result.add(4); // container
                        result.add(11); // item
                    }
                    else if (argument.equals("-inv") || argument.equals("inv-") || argument.equals("-inventory") || argument.equals("inventory-") || argument.equals("-inventories")) {
                        result.add(4);
                        result.add(11);
                        result.add(1);
                    }
                    else if (argument.equals("+inv") || argument.equals("inv+") || argument.equals("+inventory") || argument.equals("inventory+") || argument.equals("+inventories")) {
                        result.add(4);
                        result.add(11);
                        result.add(0);
                    }
                    else if (argument.equals("item") || argument.equals("items")) {
                        result.add(11);
                    }
                    else if (argument.equals("-item") || argument.equals("item-") || argument.equals("-items") || argument.equals("items-") || argument.equals("drop") || argument.equals("drops") || argument.equals("deposit") || argument.equals("deposits") || argument.equals("deposited")) {
                        result.add(11);
                        result.add(0);
                    }
                    else if (argument.equals("+item") || argument.equals("item+") || argument.equals("+items") || argument.equals("items+") || argument.equals("pickup") || argument.equals("pickups") || argument.equals("withdraw") || argument.equals("withdraws") || argument.equals("withdrew")) {
                        result.add(11);
                        result.add(1);
                    }
                    else {
                        result.add(-1);
                    }
                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return result;
    }

    protected static Location parseCoordinates(Location location, String[] inputArguments, int worldId) {
        String[] argumentArray = inputArguments.clone();
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("position:") || argument.equals("location:") || argument.equals("c:") || argument.equals("coord:") || argument.equals("coords:") || argument.equals("cord:") || argument.equals("cords:") || argument.equals("coordinate:") || argument.equals("coordinates:") || argument.equals("cordinate:") || argument.equals("cordinates:")) {
                    next = 2;
                }
                else if (next == 2 || argument.startsWith("c:") || argument.startsWith("coord:") || argument.startsWith("coords:") || argument.startsWith("cord:") || argument.startsWith("cords:") || argument.startsWith("coordinate:") || argument.startsWith("coordinates:") || argument.startsWith("cordinate:") || argument.startsWith("cordinates:")) {
                    argument = argument.replaceAll("coordinates:", "");
                    argument = argument.replaceAll("coordinate:", "");
                    argument = argument.replaceAll("cordinates:", "");
                    argument = argument.replaceAll("cordinate:", "");
                    argument = argument.replaceAll("coords:", "");
                    argument = argument.replaceAll("coord:", "");
                    argument = argument.replaceAll("cords:", "");
                    argument = argument.replaceAll("cord:", "");
                    argument = argument.replaceAll("c:", "");
                    if (argument.contains(",")) {
                        String[] i2 = argument.split(",");
                        double x = 0.00;
                        double y = 0.00;
                        double z = 0.00;
                        int cCount = 0;
                        for (String coord : i2) {
                            coord = coord.replaceAll("[^0-9.\\-]", "");
                            if (coord.length() > 0 && !coord.equals(".") && !coord.equals("-") && coord.indexOf('.') == coord.lastIndexOf('.')) {
                                double parsedCoord = Double.parseDouble(coord);
                                if (cCount == 0) {
                                    x = parsedCoord;
                                }
                                else if (cCount == 1) {
                                    z = parsedCoord;
                                }
                                else if (cCount == 2) {
                                    y = z;
                                    z = parsedCoord;
                                }
                                cCount++;
                            }
                        }
                        if (cCount > 1) {
                            if (location == null && worldId > 0) {
                                location = new Location(Bukkit.getWorld(Util.getWorldName(worldId)), 0, 0, 0);
                            }
                            if (location != null) {
                                int worldMaxHeight = location.getWorld().getMaxHeight() - 1;
                                int worldMinHeight = BukkitAdapter.ADAPTER.getMinHeight(location.getWorld());

                                if (y < worldMinHeight) {
                                    y = Double.valueOf(worldMinHeight);
                                }
                                if (y > worldMaxHeight) {
                                    y = Double.valueOf(worldMaxHeight);
                                }

                                location.setX(x);
                                location.setY(y);
                                location.setZ(z);
                            }
                        }
                    }
                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return location;
    }

    protected static boolean parseCount(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        boolean result = false;
        int count = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");
                if (argument.equals("#count") || argument.equals("#sum")) {
                    result = true;
                }
            }
            count++;
        }
        return result;
    }

    protected static Map<Object, Boolean> parseExcluded(CommandSender player, String[] inputArguments, List<Integer> argAction) {
        String[] argumentArray = inputArguments.clone();
        Map<Object, Boolean> excluded = new HashMap<>();
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("e:") || argument.equals("exclude:")) {
                    next = 5;
                }
                else if (next == 5 || argument.startsWith("e:") || argument.startsWith("exclude:")) {
                    argument = argument.replaceAll("exclude:", "");
                    argument = argument.replaceAll("e:", "");
                    if (argument.contains(",")) {
                        String[] i2 = argument.split(",");
                        for (String i3 : i2) {
                            if (!checkTags(i3, excluded)) {
                                Material i3_material = Util.getType(i3);
                                if (i3_material != null && (i3_material.isBlock() || argAction.contains(4))) {
                                    excluded.put(i3_material, false);
                                }
                                else {
                                    EntityType i3_entity = Util.getEntityType(i3);
                                    if (i3_entity != null) {
                                        excluded.put(i3_entity, false);
                                    }
                                    else if (i3_material != null) {
                                        excluded.put(i3_material, false);
                                    }
                                }
                            }
                        }
                        if (argument.endsWith(",")) {
                            next = 5;
                        }
                        else {
                            next = 0;
                        }
                    }
                    else {
                        if (!checkTags(argument, excluded)) {
                            Material iMaterial = Util.getType(argument);
                            if (iMaterial != null && (iMaterial.isBlock() || argAction.contains(4))) {
                                excluded.put(iMaterial, false);
                            }
                            else {
                                EntityType iEntity = Util.getEntityType(argument);
                                if (iEntity != null) {
                                    excluded.put(iEntity, false);
                                }
                                else if (iMaterial != null) {
                                    excluded.put(iMaterial, false);
                                }
                            }
                        }
                        next = 0;
                    }
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return excluded;
    }

    protected static List<String> parseExcludedUsers(CommandSender player, String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        List<String> excluded = new ArrayList<>();
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("e:") || argument.equals("exclude:")) {
                    next = 5;
                }
                else if (next == 5 || argument.startsWith("e:") || argument.startsWith("exclude:")) {
                    argument = argument.replaceAll("exclude:", "");
                    argument = argument.replaceAll("e:", "");
                    if (argument.contains(",")) {
                        String[] i2 = argument.split(",");
                        for (String i3 : i2) {
                            boolean isBlock = false;
                            if (checkTags(i3)) {
                                isBlock = true;
                            }
                            else {
                                Material i3_material = Util.getType(i3);
                                if (i3_material != null) {
                                    isBlock = true;
                                }
                                else {
                                    EntityType i3Entity = Util.getEntityType(i3);
                                    if (i3Entity != null) {
                                        isBlock = true;
                                    }
                                }
                            }
                            if (!isBlock) {
                                excluded.add(i3);
                            }
                        }
                        if (argument.endsWith(",")) {
                            next = 5;
                        }
                        else {
                            next = 0;
                        }
                    }
                    else {
                        boolean isBlock = false;
                        if (checkTags(argument)) {
                            isBlock = true;
                        }
                        else {
                            Material iMaterial = Util.getType(argument);
                            if (iMaterial != null) {
                                isBlock = true;
                            }
                            else {
                                EntityType entityType = Util.getEntityType(argument);
                                if (entityType != null) {
                                    isBlock = true;
                                }
                            }
                        }
                        if (!isBlock) {
                            excluded.add(argument);
                        }
                        next = 0;
                    }
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return excluded;
    }

    protected static boolean parseForceGlobal(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        boolean result = false;
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("r:") || argument.equals("radius:")) {
                    next = 2;
                }
                else if (next == 2 || argument.startsWith("r:") || argument.startsWith("radius:")) {
                    argument = argument.replaceAll("radius:", "");
                    argument = argument.replaceAll("r:", "");
                    if (argument.equals("#global") || argument.equals("global") || argument.equals("off") || argument.equals("-1") || argument.equals("none") || argument.equals("false")) {
                        result = true;
                    }
                    else if (argument.startsWith("#")) {
                        int worldId = Util.matchWorld(argument);
                        if (worldId > 0) {
                            result = true;
                        }
                    }
                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return result;
    }

    protected static Location parseLocation(CommandSender user, String[] argumentArray) {
        Location location = null;
        if (user instanceof Player) {
            location = ((Player) user).getLocation();
        }
        else if (user instanceof BlockCommandSender) {
            location = ((BlockCommandSender) user).getBlock().getLocation();
        }

        return parseCoordinates(location, argumentArray, parseWorld(argumentArray, true, true));
    }

    protected static int parseNoisy(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        int noisy = 0;
        int count = 0;
        if (Config.getGlobal().VERBOSE) {
            noisy = 1;
        }
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("n") || argument.equals("noisy") || argument.equals("v") || argument.equals("verbose") || argument.equals("#v") || argument.equals("#verbose")) {
                    noisy = 1;
                }
                else if (argument.equals("#silent")) {
                    noisy = 0;
                }
            }
            count++;
        }
        return noisy;
    }

    protected static int parsePreview(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        int result = 0;
        int count = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");
                if (argument.equals("#preview")) {
                    result = 1;
                }
                else if (argument.equals("#preview_cancel")) {
                    result = 2;
                }
            }
            count++;
        }
        return result;
    }

    protected static Integer[] parseRadius(String[] inputArguments, CommandSender user, Location location) {
        String[] argumentArray = inputArguments.clone();
        Integer[] radius = null;
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("r:") || argument.equals("radius:")) {
                    next = 2;
                }
                else if (next == 2 || argument.startsWith("r:") || argument.startsWith("radius:")) {
                    argument = argument.replaceAll("radius:", "");
                    argument = argument.replaceAll("r:", "");
                    if (argument.equals("#worldedit") || argument.equals("#we")) {
                        if (user.getServer().getPluginManager().getPlugin("WorldEdit") != null) {
                            Integer[] worldEditResult = WorldEditHandler.runWorldEditCommand(user);
                            if (worldEditResult != null) {
                                radius = worldEditResult;
                            }
                        }
                    }
                    else if ((argument.startsWith("#") && argument.length() > 1) || argument.equals("global") || argument.equals("off") || argument.equals("-1") || argument.equals("none") || argument.equals("false")) {
                        // radius = -2;
                    }
                    else {
                        int rcount = 0;
                        int r_x = 0;
                        int r_y = -1;
                        int r_z = 0;
                        String[] r_dat = new String[] { argument };
                        boolean validRadius = false;
                        if (argument.contains("x")) {
                            r_dat = argument.split("x");
                        }
                        for (String value : r_dat) {
                            String i4 = value.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.length() == value.length() && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                double a1 = Double.parseDouble(i4);
                                if (rcount == 0) { // x
                                    r_x = (int) a1;
                                    r_z = (int) a1;
                                }
                                else if (rcount == 1) { // y
                                    r_y = (int) a1;
                                }
                                else if (rcount == 2) { // z
                                    r_z = (int) a1;
                                }
                                validRadius = true;
                            }
                            rcount++;
                        }
                        if (location != null) {
                            Integer xmin = location.getBlockX() - r_x;
                            Integer xmax = location.getBlockX() + r_x;
                            Integer ymin = null;
                            Integer ymax = null;
                            Integer zmin = location.getBlockZ() - r_z;
                            Integer zmax = location.getBlockZ() + r_z;
                            if (r_y > -1) {
                                ymin = location.getBlockY() - r_y;
                                ymax = location.getBlockY() + r_y;
                            }
                            int max = r_x;
                            if (r_y > max) {
                                max = r_y;
                            }
                            if (r_z > max) {
                                max = r_z;
                            }
                            if (validRadius) {
                                radius = new Integer[] { max, xmin, xmax, ymin, ymax, zmin, zmax, 0 };
                            }
                            else {
                                radius = new Integer[] { -1 };
                            }
                        }
                    }
                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return radius;
    }

    protected static List<Object> parseRestricted(CommandSender player, String[] inputArguments, List<Integer> argAction) {
        String[] argumentArray = inputArguments.clone();
        List<Object> restricted = new ArrayList<>();
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("i:") || argument.equals("include:") || argument.equals("item:") || argument.equals("items:") || argument.equals("b:") || argument.equals("block:") || argument.equals("blocks:")) {
                    next = 4;
                }
                else if (next == 4 || argument.startsWith("i:") || argument.startsWith("include:") || argument.startsWith("item:") || argument.startsWith("items:") || argument.startsWith("b:") || argument.startsWith("block:") || argument.startsWith("blocks:")) {
                    argument = argument.replaceAll("include:", "");
                    argument = argument.replaceAll("i:", "");
                    argument = argument.replaceAll("items:", "");
                    argument = argument.replaceAll("item:", "");
                    argument = argument.replaceAll("blocks:", "");
                    argument = argument.replaceAll("block:", "");
                    argument = argument.replaceAll("b:", "");
                    if (argument.contains(",")) {
                        String[] i2 = argument.split(",");
                        for (String i3 : i2) {
                            if (!checkTags(argument, restricted)) {
                                Material i3_material = Util.getType(i3);
                                if (i3_material != null && (i3_material.isBlock() || argAction.contains(4))) {
                                    restricted.add(i3_material);
                                }
                                else {
                                    EntityType i3_entity = Util.getEntityType(i3);
                                    if (i3_entity != null) {
                                        restricted.add(i3_entity);
                                    }
                                    else if (i3_material != null) {
                                        restricted.add(i3_material);
                                    }
                                    else {
                                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_INCLUDE, i3));
                                        // Functions.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co help include"));
                                        return null;
                                    }
                                }
                            }
                        }
                        if (argument.endsWith(",")) {
                            next = 4;
                        }
                        else {
                            next = 0;
                        }
                    }
                    else {
                        if (!checkTags(argument, restricted)) {
                            Material material = Util.getType(argument);
                            if (material != null && (material.isBlock() || argAction.contains(4))) {
                                restricted.add(material);
                            }
                            else {
                                EntityType entityType = Util.getEntityType(argument);
                                if (entityType != null) {
                                    restricted.add(entityType);
                                }
                                else if (material != null) {
                                    restricted.add(material);
                                }
                                else {
                                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_INCLUDE, argument));
                                    // Functions.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co help include"));
                                    return null;
                                }
                            }
                        }
                        next = 0;
                    }
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return restricted;
    }

    protected static long[] parseTime(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        long timeStart = 0;
        long timeEnd = 0;
        int count = 0;
        int next = 0;
        boolean range = false;
        double w = 0;
        double d = 0;
        double h = 0;
        double m = 0;
        double s = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("t:") || argument.equals("time:")) {
                    next = 1;
                }
                else if (next == 1 || argument.startsWith("t:") || argument.startsWith("time:")) {
                    // time arguments
                    argument = argument.replaceAll("time:", "");
                    argument = argument.replaceAll("t:", "");
                    argument = argument.replaceAll("y", "y:");
                    argument = argument.replaceAll("m", "m:");
                    argument = argument.replaceAll("w", "w:");
                    argument = argument.replaceAll("d", "d:");
                    argument = argument.replaceAll("h", "h:");
                    argument = argument.replaceAll("s", "s:");
                    range = argument.contains("-");

                    int argCount = 0;
                    String[] i2 = argument.split(":");
                    for (String i3 : i2) {
                        if (range && argCount > 0 && timeStart == 0 && i3.startsWith("-")) {
                            timeStart = (long) (((w * 7 * 24 * 60 * 60) + (d * 24 * 60 * 60) + (h * 60 * 60) + (m * 60) + s));
                            w = 0;
                            d = 0;
                            h = 0;
                            m = 0;
                            s = 0;
                        }

                        if (i3.endsWith("w") && w == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                w = Double.parseDouble(i4);
                            }
                        }
                        else if (i3.endsWith("d") && d == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                d = Double.parseDouble(i4);
                            }
                        }
                        else if (i3.endsWith("h") && h == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                h = Double.parseDouble(i4);
                            }
                        }
                        else if (i3.endsWith("m") && m == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                m = Double.parseDouble(i4);
                            }
                        }
                        else if (i3.endsWith("s") && s == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                s = Double.parseDouble(i4);
                            }
                        }

                        argCount++;
                    }
                    if (timeStart > 0) {
                        timeEnd = (long) (((w * 7 * 24 * 60 * 60) + (d * 24 * 60 * 60) + (h * 60 * 60) + (m * 60) + s));
                    }
                    else {
                        timeStart = (long) (((w * 7 * 24 * 60 * 60) + (d * 24 * 60 * 60) + (h * 60 * 60) + (m * 60) + s));
                    }
                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }

        if (timeEnd >= timeStart) {
            return new long[] { timeEnd, timeStart };
        }
        else {
            return new long[] { timeStart, timeEnd };
        }
    }

    private static String timeString(BigDecimal input) {
        return input.stripTrailingZeros().toPlainString();
    }

    protected static String parseTimeString(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        String time = "";
        int count = 0;
        int next = 0;
        boolean range = false;
        BigDecimal w = new BigDecimal(0);
        BigDecimal d = new BigDecimal(0);
        BigDecimal h = new BigDecimal(0);
        BigDecimal m = new BigDecimal(0);
        BigDecimal s = new BigDecimal(0);
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("t:") || argument.equals("time:")) {
                    next = 1;
                }
                else if (next == 1 || argument.startsWith("t:") || argument.startsWith("time:")) {
                    // time arguments
                    argument = argument.replaceAll("time:", "");
                    argument = argument.replaceAll("t:", "");
                    argument = argument.replaceAll("y", "y:");
                    argument = argument.replaceAll("m", "m:");
                    argument = argument.replaceAll("w", "w:");
                    argument = argument.replaceAll("d", "d:");
                    argument = argument.replaceAll("h", "h:");
                    argument = argument.replaceAll("s", "s:");
                    range = argument.contains("-");

                    int argCount = 0;
                    String[] i2 = argument.split(":");
                    for (String i3 : i2) {
                        if (range && argCount > 0 && !time.contains("-") && i3.startsWith("-")) {
                            w = new BigDecimal(0);
                            d = new BigDecimal(0);
                            h = new BigDecimal(0);
                            m = new BigDecimal(0);
                            s = new BigDecimal(0);
                            time = time + " -";
                        }

                        if (i3.endsWith("w") && w.intValue() == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                w = new BigDecimal(i4);
                                if (range) {
                                    time = time + " " + timeString(w) + "w";
                                }
                                else {
                                    time = time + " " + Phrase.build(Phrase.TIME_WEEKS, timeString(w), (w.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND));
                                }
                            }
                        }
                        else if (i3.endsWith("d") && d.intValue() == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                d = new BigDecimal(i4);
                                if (range) {
                                    time = time + " " + timeString(d) + "d";
                                }
                                else {
                                    time = time + " " + Phrase.build(Phrase.TIME_DAYS, timeString(d), (d.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND));
                                }
                            }
                        }
                        else if (i3.endsWith("h") && h.intValue() == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                h = new BigDecimal(i4);
                                if (range) {
                                    time = time + " " + timeString(h) + "h";
                                }
                                else {
                                    time = time + " " + Phrase.build(Phrase.TIME_HOURS, timeString(h), (h.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND));
                                }
                            }
                        }
                        else if (i3.endsWith("m") && m.intValue() == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                m = new BigDecimal(i4);
                                if (range) {
                                    time = time + " " + timeString(m) + "m";
                                }
                                else {
                                    time = time + " " + Phrase.build(Phrase.TIME_MINUTES, timeString(m), (m.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND));
                                }
                            }
                        }
                        else if (i3.endsWith("s") && s.intValue() == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                s = new BigDecimal(i4);
                                if (range) {
                                    time = time + " " + timeString(s) + "s";
                                }
                                else {
                                    time = time + " " + Phrase.build(Phrase.TIME_SECONDS, timeString(s), (s.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND));
                                }
                            }
                        }

                        argCount++;
                    }
                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }

        if (time.startsWith(" ")) {
            time = time.substring(1);
        }

        return time;
    }

    protected static int parseRows(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        int rows = 0;
        int count = 0;
        int next = 0;

        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("rows:")) {
                    next = 1;
                }
                else if (next == 1 || argument.startsWith("rows:")) {
                    argument = argument.replaceAll("rows:", "").trim();
                    if (!argument.startsWith("-")) {
                        String i2 = argument.replaceAll("[^0-9]", "");
                        if (i2.length() > 0 && i2.length() < 10) {
                            rows = Integer.parseInt(i2);
                        }
                    }

                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }

        return rows;
    }

    private static void parseUser(List<String> users, String string) {
        string = string.trim();
        if (string.contains(",")) {
            String[] data = string.split(",");
            for (String user : data) {
                validUserCheck(users, user);
            }
        }
        else {
            validUserCheck(users, string);
        }
    }

    protected static List<String> parseUsers(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        List<String> users = new ArrayList<>();
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (next == 2) {
                    if (argument.endsWith(",")) {
                        next = 2;
                    }
                    else {
                        next = 0;
                    }
                }
                else if (argument.equals("p:") || argument.equals("user:") || argument.equals("users:") || argument.equals("u:")) {
                    next = 1;
                }
                else if (next == 1 || argument.startsWith("p:") || argument.startsWith("user:") || argument.startsWith("users:") || argument.startsWith("u:")) {
                    argument = argument.replaceAll("user:", "");
                    argument = argument.replaceAll("users:", "");
                    argument = argument.replaceAll("p:", "");
                    argument = argument.replaceAll("u:", "");
                    if (argument.contains(",")) {
                        String[] i2 = argument.split(",");
                        for (String i3 : i2) {
                            parseUser(users, i3);
                        }
                        if (argument.endsWith(",")) {
                            next = 1;
                        }
                        else {
                            next = 0;
                        }
                    }
                    else {
                        parseUser(users, argument);
                        next = 0;
                    }
                }
                else if (argument.endsWith(",") || argument.endsWith(":")) {
                    next = 2;
                }
                else if (argument.contains(":")) {
                    next = 0;
                }
                else {
                    parseUser(users, argument);
                    next = 0;
                }
            }
            count++;
        }
        return users;
    }

    protected static int parseWorld(String[] inputArguments, boolean processWorldEdit, boolean requireLoaded) {
        String[] argumentArray = inputArguments.clone();
        int world_id = 0;
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim();
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                String inputProcessed = argument.toLowerCase(Locale.ROOT);
                if (inputProcessed.equals("r:") || inputProcessed.equals("radius:")) {
                    next = 2;
                }
                else if (next == 2 || inputProcessed.startsWith("r:") || inputProcessed.startsWith("radius:")) {
                    argument = argument.replaceAll("radius:", "").replaceAll("r:", "");
                    inputProcessed = argument.toLowerCase(Locale.ROOT);
                    if ((processWorldEdit && (inputProcessed.equals("#worldedit") || inputProcessed.equals("#we"))) || inputProcessed.equals("#global") || inputProcessed.equals("global") || inputProcessed.equals("off") || inputProcessed.equals("-1") || inputProcessed.equals("none") || inputProcessed.equals("false")) {
                        world_id = 0;
                    }
                    else if (inputProcessed.startsWith("#")) {
                        world_id = Util.matchWorld(inputProcessed);
                        if (world_id == -1 && !requireLoaded) {
                            world_id = ConfigHandler.worlds.getOrDefault(argument.replaceFirst("#", ""), -1);
                        }
                    }
                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return world_id;
    }

    protected static boolean parseWorldEdit(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        boolean result = false;
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("r:") || argument.equals("radius:")) {
                    next = 2;
                }
                else if (next == 2 || argument.startsWith("r:") || argument.startsWith("radius:")) {
                    argument = argument.replaceAll("radius:", "");
                    argument = argument.replaceAll("r:", "");
                    if (argument.equals("#worldedit") || argument.equals("#we")) {
                        result = true;
                    }
                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return result;
    }

    protected static String parseWorldName(String[] inputArguments, boolean processWorldEdit) {
        String[] argumentArray = inputArguments.clone();
        String worldName = "";
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("r:") || argument.equals("radius:")) {
                    next = 2;
                }
                else if (next == 2 || argument.startsWith("r:") || argument.startsWith("radius:")) {
                    argument = argument.replaceAll("radius:", "");
                    argument = argument.replaceAll("r:", "");
                    if ((processWorldEdit && (argument.equals("#worldedit") || argument.equals("#we"))) || argument.equals("#global") || argument.equals("global") || argument.equals("off") || argument.equals("-1") || argument.equals("none") || argument.equals("false")) {
                        worldName = "";
                    }
                    else if (argument.startsWith("#")) {
                        worldName = argument.replaceFirst("#", "");
                    }
                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return worldName;
    }

    protected static Map<String, Set<Material>> getTags() {
        Map<String, Set<Material>> tagMap = new HashMap<>();
        tagMap.put("#button", BlockGroup.BUTTONS);
        tagMap.put("#container", BlockGroup.CONTAINERS);
        tagMap.put("#door", BlockGroup.DOORS);
        tagMap.put("#natural", BlockGroup.NATURAL_BLOCKS);
        tagMap.put("#pressure_plate", BlockGroup.PRESSURE_PLATES);
        tagMap.put("#shulker_box", BlockGroup.SHULKER_BOXES);
        return tagMap;
    }

    protected static boolean checkTags(String argument) {
        return getTags().containsKey(argument);
    }

    protected static boolean checkTags(String argument, Map<Object, Boolean> list) {
        for (Entry<String, Set<Material>> entry : getTags().entrySet()) {
            String tag = entry.getKey();
            Set<Material> materials = entry.getValue();

            if (argument.equals(tag)) {
                for (Material block : materials) {
                    list.put(block, false);
                }

                return true;
            }
        }

        return false;
    }

    protected static boolean checkTags(String argument, List<Object> list) {
        for (Entry<String, Set<Material>> entry : getTags().entrySet()) {
            String tag = entry.getKey();
            Set<Material> materials = entry.getValue();

            if (argument.equals(tag)) {
                list.addAll(materials);
                return true;
            }
        }

        return false;
    }

    private static void validUserCheck(List<String> users, String user) {
        List<String> badUsers = Arrays.asList("n", "noisy", "v", "verbose", "#v", "#verbose", "#silent", "#preview", "#preview_cancel", "#count", "#sum");
        String check = user.replaceAll("[\\s'\"]", "");
        if (check.equals(user) && check.length() > 0) {
            if (user.equalsIgnoreCase("#global")) {
                user = "#global";
            }
            if (!badUsers.contains(user.toLowerCase(Locale.ROOT))) {
                users.add(user);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender user, Command command, String commandLabel, String[] argumentArray) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("core") || commandName.equals("coreprotect") || commandName.equals("co")) {
            int resultc = argumentArray.length;
            if (resultc > -1) {
                String corecommand = "help";
                if (resultc > 0) {
                    corecommand = argumentArray[0].toLowerCase(Locale.ROOT);
                }
                boolean permission = false;
                if (!permission) {
                    if (user.hasPermission("coreprotect.rollback") && (corecommand.equals("rollback") || corecommand.equals("rb") || corecommand.equals("ro") || corecommand.equals("apply") || corecommand.equals("cancel"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.restore") && (corecommand.equals("restore") || corecommand.equals("rs") || corecommand.equals("re") || corecommand.equals("undo") || corecommand.equals("apply") || corecommand.equals("cancel"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.inspect") && (corecommand.equals("i") || corecommand.equals("inspect") || corecommand.equals("inspector"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.help") && corecommand.equals("help")) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.purge") && corecommand.equals("purge")) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.lookup") && (corecommand.equals("l") || corecommand.equals("lookup") || corecommand.equals("page") || corecommand.equals("near"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.lookup.near") && corecommand.equals("near")) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.teleport") && (corecommand.equals("tp") || corecommand.equals("teleport"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.reload") && corecommand.equals("reload")) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.status") && (corecommand.equals("status") || corecommand.equals("stats") || corecommand.equals("version"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.consumer") && corecommand.equals("consumer")) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.networking") && corecommand.equals("network-debug")) {
                        permission = true;
                    }
                }

                if (corecommand.equals("rollback") || corecommand.equals("restore") || corecommand.equals("rb") || corecommand.equals("rs") || corecommand.equals("ro") || corecommand.equals("re")) {
                    RollbackRestoreCommand.runCommand(user, command, permission, argumentArray, null, 0, 0);
                }
                else if (corecommand.equals("apply")) {
                    ApplyCommand.runCommand(user, command, permission, argumentArray);
                }
                else if (corecommand.equals("cancel")) {
                    CancelCommand.runCommand(user, command, permission, argumentArray);
                }
                else if (corecommand.equals("undo")) {
                    UndoCommand.runCommand(user, command, permission, argumentArray);
                }
                else if (corecommand.equals("help")) {
                    HelpCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("purge")) {
                    PurgeCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("inspect") || corecommand.equals("i")) {
                    InspectCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("lookup") || corecommand.equals("l") || corecommand.equals("page")) {
                    LookupCommand.runCommand(user, command, permission, argumentArray);
                }
                else if (corecommand.equals("near")) {
                    LookupCommand.runCommand(user, command, permission, new String[] { "near", "r:5x5" });
                }
                else if (corecommand.equals("teleport") || corecommand.equals("tp")) {
                    TeleportCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("status") || corecommand.equals("stats") || corecommand.equals("version")) {
                    StatusCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("reload")) {
                    ReloadCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("consumer")) {
                    ConsumerCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("network-debug")) {
                    NetworkDebugCommand.runCommand(user, permission, argumentArray);
                }
                else {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.COMMAND_NOT_FOUND, Color.WHITE, "/co " + corecommand));
                }
            }
            else {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, Color.WHITE, "/co <parameters>"));
            }

            if (user.isOp() && versionAlert.get(user.getName()) == null) {
                String latestVersion = NetworkHandler.latestVersion();
                if (latestVersion != null) {
                    versionAlert.put(user.getName(), true);
                    class updateAlert implements Runnable {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(5000);
                                Chat.sendMessage(user, Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.UPDATE_HEADER, "CoreProtect" + (Util.isCommunityEdition() ? " " + ConfigHandler.COMMUNITY_EDITION : "")) + Color.WHITE + " -----");
                                Chat.sendMessage(user, Color.DARK_AQUA + Phrase.build(Phrase.UPDATE_NOTICE, Color.WHITE, "CoreProtect v" + latestVersion));
                                Chat.sendMessage(user, Color.DARK_AQUA + Phrase.build(Phrase.LINK_DOWNLOAD, Color.WHITE, "www.coreprotect.net/download/"));
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    (new Thread(new updateAlert())).start();
                }
            }

            return true;
        }

        return false;
    }
}
