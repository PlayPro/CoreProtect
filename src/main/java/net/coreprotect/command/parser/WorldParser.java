package net.coreprotect.command.parser;

import java.util.Locale;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.WorldUtils;

/**
 * Parser for world-related command arguments
 */
public class WorldParser {

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
    public static int parseWorld(String[] inputArguments, boolean processWorldEdit, boolean requireLoaded) {
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
                        world_id = WorldUtils.matchWorld(inputProcessed);
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

    /**
     * Parse world name from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @param processWorldEdit
     *            Whether to process WorldEdit arguments
     * @return The world name
     */
    public static String parseWorldName(String[] inputArguments, boolean processWorldEdit) {
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

    /**
     * Parse whether to use WorldEdit for radius
     * 
     * @param inputArguments
     *            The command arguments
     * @return true if WorldEdit should be used
     */
    public static boolean parseWorldEdit(String[] inputArguments) {
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

    /**
     * Parse whether to force global search
     * 
     * @param inputArguments
     *            The command arguments
     * @return true if global search should be forced
     */
    public static boolean parseForceGlobal(String[] inputArguments) {
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
                        int worldId = WorldUtils.matchWorld(argument);
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
}
