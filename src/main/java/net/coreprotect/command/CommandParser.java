package net.coreprotect.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import net.coreprotect.command.parser.ActionParser;
import net.coreprotect.command.parser.LocationParser;
import net.coreprotect.command.parser.MaterialParser;
import net.coreprotect.command.parser.TimeParser;
import net.coreprotect.command.parser.UserParser;
import net.coreprotect.command.parser.WorldParser;

/**
 * Main parser class for CoreProtect commands.
 * Delegates to specialized parser classes for specific functionality.
 */
public class CommandParser {

    /**
     * Parse page number from command arguments
     * 
     * @param argumentArray
     *            The command arguments
     * @return The modified argument array
     */
    protected static String[] parsePage(String[] argumentArray) {
        return ActionParser.parsePage(argumentArray);
    }

    /**
     * Parse action type from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return A list of action type integers
     */
    protected static List<Integer> parseAction(String[] inputArguments) {
        return ActionParser.parseAction(inputArguments);
    }

    /**
     * Parse coordinates from command arguments
     * 
     * @param location
     *            The base location
     * @param inputArguments
     *            The command arguments
     * @param worldId
     *            The world ID
     * @return The parsed location
     */
    protected static Location parseCoordinates(Location location, String[] inputArguments, int worldId) {
        return LocationParser.parseCoordinates(location, inputArguments, worldId);
    }

    /**
     * Parse count flag from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return true if the count flag is present
     */
    protected static boolean parseCount(String[] inputArguments) {
        return ActionParser.parseCount(inputArguments);
    }

    /**
     * Parse excluded materials and entities from command arguments
     * 
     * @param player
     *            The command sender
     * @param inputArguments
     *            The command arguments
     * @param argAction
     *            The list of actions to include
     * @return A map of excluded materials and entities
     */
    protected static Map<Object, Boolean> parseExcluded(CommandSender player, String[] inputArguments, List<Integer> argAction) {
        return MaterialParser.parseExcluded(player, inputArguments, argAction);
    }

    /**
     * Parse excluded users from command arguments
     * 
     * @param player
     *            The command sender
     * @param inputArguments
     *            The command arguments
     * @return A list of excluded users
     */
    protected static List<String> parseExcludedUsers(CommandSender player, String[] inputArguments) {
        return UserParser.parseExcludedUsers(player, inputArguments);
    }

    /**
     * Parse force global flag from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return true if global search should be forced
     */
    protected static boolean parseForceGlobal(String[] inputArguments) {
        return WorldParser.parseForceGlobal(inputArguments);
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
    protected static Location parseLocation(CommandSender user, String[] argumentArray) {
        return LocationParser.parseLocation(user, argumentArray);
    }

    /**
     * Parse noisy/verbose flag from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return 1 if noisy/verbose mode is enabled, 0 otherwise
     */
    protected static int parseNoisy(String[] inputArguments) {
        return ActionParser.parseNoisy(inputArguments);
    }

    /**
     * Parse preview flag from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return 1 for preview, 2 for preview cancel, 0 otherwise
     */
    protected static int parsePreview(String[] inputArguments) {
        return ActionParser.parsePreview(inputArguments);
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
    protected static Integer[] parseRadius(String[] inputArguments, CommandSender user, Location location) {
        Integer[] result = LocationParser.parseRadius(inputArguments, user, location);

        // Handle WorldEdit case which LocationParser returned as null
        if (result == null && WorldParser.parseWorldEdit(inputArguments)) {
            if (user.getServer().getPluginManager().getPlugin("WorldEdit") != null) {
                Integer[] worldEditResult = WorldEditHandler.runWorldEditCommand(user);
                if (worldEditResult != null) {
                    result = worldEditResult;
                }
            }
        }

        return result;
    }

    /**
     * Parse restricted materials and entities from command arguments
     * 
     * @param player
     *            The command sender
     * @param inputArguments
     *            The command arguments
     * @param argAction
     *            The list of actions to include
     * @return A list of restricted materials and entities
     */
    protected static List<Object> parseRestricted(CommandSender player, String[] inputArguments, List<Integer> argAction) {
        return MaterialParser.parseRestricted(player, inputArguments, argAction);
    }

    /**
     * Parse time from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return An array of two longs - [time1, time2]
     */
    protected static long[] parseTime(String[] inputArguments) {
        return TimeParser.parseTime(inputArguments);
    }

    /**
     * Parse time string from command arguments for display
     * 
     * @param inputArguments
     *            The command arguments
     * @return A formatted time string
     */
    protected static String parseTimeString(String[] inputArguments) {
        return TimeParser.parseTimeString(inputArguments);
    }

    /**
     * Parse rows from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return The number of rows
     */
    protected static int parseRows(String[] inputArguments) {
        return TimeParser.parseRows(inputArguments);
    }

    /**
     * Parse world from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @param processWorldEdit
     *            Whether to process WorldEdit arguments
     * @param requireLoaded
     *            Whether the world must be loaded
     * @return The world ID
     */
    protected static int parseWorld(String[] inputArguments, boolean processWorldEdit, boolean requireLoaded) {
        return WorldParser.parseWorld(inputArguments, processWorldEdit, requireLoaded);
    }

    /**
     * Parse whether to use WorldEdit for radius
     * 
     * @param inputArguments
     *            The command arguments
     * @return true if WorldEdit should be used
     */
    protected static boolean parseWorldEdit(String[] inputArguments) {
        return WorldParser.parseWorldEdit(inputArguments);
    }

    /**
     * Parse world name from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @param processWorldEdit
     *            Whether to process WorldEdit arguments
     * @return The world name
     */
    protected static String parseWorldName(String[] inputArguments, boolean processWorldEdit) {
        return WorldParser.parseWorldName(inputArguments, processWorldEdit);
    }

    /**
     * Get a map of block tags and their associated materials
     * 
     * @return A map of block tags and their associated materials
     */
    protected static Map<String, Set<Material>> getTags() {
        return MaterialParser.getTags();
    }

    /**
     * Check if an argument matches a block tag
     * 
     * @param argument
     *            The argument to check
     * @return true if the argument matches a block tag
     */
    protected static boolean checkTags(String argument) {
        return MaterialParser.checkTags(argument);
    }

    /**
     * Check if an argument matches a block tag and add the associated materials to the list
     * 
     * @param argument
     *            The argument to check
     * @param list
     *            The list to add the associated materials to
     * @return true if the argument matches a block tag
     */
    protected static boolean checkTags(String argument, Map<Object, Boolean> list) {
        return MaterialParser.checkTags(argument, list);
    }

    /**
     * Check if an argument matches a block tag and add the associated materials to the list
     * 
     * @param argument
     *            The argument to check
     * @param list
     *            The list to add the associated materials to
     * @return true if the argument matches a block tag
     */
    protected static boolean checkTags(String argument, List<Object> list) {
        return MaterialParser.checkTags(argument, list);
    }

    /**
     * Parse users from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return A list of parsed users
     */
    protected static List<String> parseUsers(String[] inputArguments) {
        return UserParser.parseUsers(inputArguments);
    }

    /**
     * Helper method for formatting BigDecimal values
     * 
     * @param input
     *            The BigDecimal value to format
     * @return The formatted string
     */
    private static String timeString(BigDecimal input) {
        return input.stripTrailingZeros().toPlainString();
    }

}
