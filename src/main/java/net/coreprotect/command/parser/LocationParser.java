package net.coreprotect.command.parser;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.utility.WorldUtils;

/**
 * Parser for location-related command arguments
 */
public class LocationParser {

    /**
     * Parse location from command sender and command arguments
     * 
     * @param user
     *            The command sender
     * @param argumentArray
     *            The command arguments
     * @return The parsed location
     */
    public static Location parseLocation(CommandSender user, String[] argumentArray) {
        Location location = null;
        if (user instanceof Player) {
            location = ((Player) user).getLocation();
        }
        else if (user instanceof BlockCommandSender) {
            location = ((BlockCommandSender) user).getBlock().getLocation();
        }

        return parseCoordinates(location, argumentArray, WorldParser.parseWorld(argumentArray, true, true));
    }

    /**
     * Parse coordinates from command arguments and apply them to a location
     * 
     * @param location
     *            The base location
     * @param inputArguments
     *            The command arguments
     * @param worldId
     *            The world ID
     * @return The modified location
     */
    public static Location parseCoordinates(Location location, String[] inputArguments, int worldId) {
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
                                location = new Location(Bukkit.getWorld(WorldUtils.getWorldName(worldId)), 0, 0, 0);
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

    /**
     * Parse radius from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @param user
     *            The command sender
     * @param location
     *            The base location
     * @return The parsed radius
     */
    public static Integer[] parseRadius(String[] inputArguments, CommandSender user, Location location) {
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
                        // For WorldEdit, we'll need to use the original CommandParser since
                        // the WorldEditHandler.runWorldEditCommand method is protected
                        return null; // Let CommandParser handle this case
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
}
