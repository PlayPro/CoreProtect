package net.coreprotect.command.parser;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.utility.WorldUtils;

/**
 * Parser for location-related command arguments
 */
public class LocationParser {

    private static final String[] COORDINATE_PREFIXES = {
            "coordinates:",
            "coordinate:",
            "cordinates:",
            "cordinate:",
            "position:",
            "location:",
            "coords:",
            "coord:",
            "cords:",
            "cord:",
            "c:"
    };

    private static final String[] RADIUS_PREFIXES = {
            "radius:",
            "r:"
    };

    public static String stripCoordinatePrefix(String argument) {
        return ParserUtils.stripPrefix(argument, COORDINATE_PREFIXES);
    }

    public static boolean isValidBlockCoordinates(String spec) {
        return parseCoordinateValues(spec) != null;
    }

    public static String findInvalidCoordinateArgument(String[] arguments) {
        for (int index = 1; index < arguments.length; index++) {
            String raw = arguments[index];
            String value = stripCoordinatePrefix(ParserUtils.normalize(raw));
            if (value == null) {
                continue;
            }
            if (value.isEmpty() && index + 1 < arguments.length) {
                value = ParserUtils.normalize(arguments[++index]);
            }
            if (!isValidBlockCoordinates(value)) {
                return raw;
            }
        }
        return null;
    }

    private static boolean isBlockCoordinate(double coordinate) {
        return coordinate >= Integer.MIN_VALUE && coordinate < Integer.MAX_VALUE + 1.0;
    }

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
        return parseLocation(user, argumentArray, WorldParser.parseWorld(argumentArray, true, true));
    }

    public static Location parseLocation(CommandSender user, String[] argumentArray, int worldId) {
        Location location = null;
        if (user instanceof Player) {
            location = ((Player) user).getLocation();
        }
        else if (user instanceof BlockCommandSender) {
            location = ((BlockCommandSender) user).getBlock().getLocation();
        }

        return parseCoordinates(location, argumentArray, worldId);
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
        World targetWorld = worldId > 0 ? Bukkit.getWorld(WorldUtils.getWorldName(worldId)) : null;
        int next = 0;
        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);
            String stripped = stripCoordinatePrefix(argument);
            if ("".equals(stripped)) {
                next = 2;
                continue;
            }

            if (next == 2 || stripped != null) {
                next = 0;
                double[] parsed = parseCoordinateValues(stripped != null ? stripped : argument);
                if (parsed == null) {
                    continue;
                }

                double x = parsed[0];
                double y = (parsed.length == 3) ? parsed[1] : (location != null ? location.getY() : 0.0);
                double z = parsed[parsed.length - 1];

                if (targetWorld != null) {
                    location = new Location(targetWorld, x, clampY(targetWorld, y), z);
                }
                else if (location != null) {
                    World world = location.getWorld();
                    location = new Location(world, x, clampY(world, y), z, location.getYaw(), location.getPitch());
                }
            }
            else {
                next = 0;
            }
        }
        if (targetWorld != null && location != null && !targetWorld.equals(location.getWorld())) {
            return new Location(targetWorld, location.getX(), clampY(targetWorld, location.getY()), location.getZ(), location.getYaw(), location.getPitch());
        }
        return location;
    }

    private static double[] parseCoordinateValues(String spec) {
        String[] parts = spec.split(",", -1);
        if (parts.length != 2 && parts.length != 3) {
            return null;
        }

        double[] coordinates = new double[parts.length];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index].trim();
            if (!ParserUtils.isSignedDecimal(part)) {
                return null;
            }
            double coordinate = Double.parseDouble(part);
            if (!Double.isFinite(coordinate)) {
                return null;
            }
            coordinates[index] = coordinate;
        }
        return isBlockCoordinate(coordinates[0]) && isBlockCoordinate(coordinates[coordinates.length - 1]) ? coordinates : null;
    }

    private static double clampY(World world, double y) {
        if (world == null) {
            return y;
        }
        int worldMaxHeight = world.getMaxHeight() - 1;
        int worldMinHeight = BukkitAdapter.ADAPTER.getMinHeight(world);
        if (y < worldMinHeight) {
            return worldMinHeight;
        }
        if (y > worldMaxHeight) {
            return worldMaxHeight;
        }
        return y;
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
        Integer[] radius = null;
        int next = 0;
        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);
            if (ParserUtils.equalsAny(argument, RADIUS_PREFIXES)) {
                if (next == 2) {
                    return new Integer[] { -1 };
                }
                next = 2;
                continue;
            }

            String stripped = ParserUtils.stripPrefix(argument, RADIUS_PREFIXES);
            if (next == 2 || stripped != null) {
                String radiusSpec = (stripped != null) ? stripped : argument;
                next = 0;

                if (radiusSpec.equals("#worldedit") || radiusSpec.equals("#we")
                        || (radiusSpec.startsWith("#") && radiusSpec.length() > 1)
                        || radiusSpec.equals("global") || radiusSpec.equals("off") || radiusSpec.equals("-1") || radiusSpec.equals("none") || radiusSpec.equals("false")) {
                    // Handled by WorldParser / CommandParser
                }
                else {
                    String[] dimensions = radiusSpec.contains("x") ? radiusSpec.split("x", -1) : new String[] { radiusSpec };
                    int r_x = 0;
                    int r_y = -1;
                    int r_z = 0;
                    boolean validRadius = dimensions.length >= 1 && dimensions.length <= 3;

                    if (validRadius) {
                        for (int rcount = 0; rcount < dimensions.length; rcount++) {
                            String value = dimensions[rcount];
                            if (!ParserUtils.isUnsignedDecimal(value)) {
                                validRadius = false;
                                break;
                            }
                            double parsed = Double.parseDouble(value);
                            if (!Double.isFinite(parsed)) {
                                validRadius = false;
                                break;
                            }
                            int parsedInt = parsed >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parsed;
                            if (rcount == 0) {
                                r_x = parsedInt;
                                r_z = parsedInt;
                            }
                            else if (rcount == 1) {
                                r_y = parsedInt;
                            }
                            else {
                                r_z = parsedInt;
                            }
                        }
                    }

                    if (!validRadius) {
                        return new Integer[] { -1 };
                    }
                    else if (location != null) {
                        Integer xmin = clampToInt((long) location.getBlockX() - r_x);
                        Integer xmax = clampToInt((long) location.getBlockX() + r_x);
                        Integer ymin = null;
                        Integer ymax = null;
                        Integer zmin = clampToInt((long) location.getBlockZ() - r_z);
                        Integer zmax = clampToInt((long) location.getBlockZ() + r_z);
                        if (r_y > -1) {
                            ymin = clampToInt((long) location.getBlockY() - r_y);
                            ymax = clampToInt((long) location.getBlockY() + r_y);
                        }
                        int max = Math.max(r_x, Math.max(r_y, r_z));
                        radius = new Integer[] { max, xmin, xmax, ymin, ymax, zmin, zmax, 0 };
                    }
                }
            }
            else {
                next = 0;
            }
        }
        return next == 2 ? new Integer[] { -1 } : radius;
    }

    private static int clampToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }
}
