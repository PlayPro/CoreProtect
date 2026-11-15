package net.coreprotect.command.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.WorldUtils;

/**
 * Parser for world-related command arguments
 */
public class WorldParser {

    private static final String[] RADIUS_PREFIXES = {
            "radius:",
            "r:"
    };

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
        int worldId = 0;
        for (String value : extractRadiusValues(inputArguments, true)) {
            String lower = value.toLowerCase(Locale.ROOT);
            if (isGlobalReset(lower, processWorldEdit)) {
                worldId = 0;
            }
            else if (lower.startsWith("#")) {
                worldId = WorldUtils.matchWorld(lower);
                if (worldId == -1 && !requireLoaded) {
                    String worldName = value.substring(1);
                    worldId = ConfigHandler.resolveIdentifierId(ConfigHandler.CacheType.WORLDS, worldName, false);
                }
            }
        }
        return worldId;
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
        String worldName = "";
        for (String value : extractRadiusValues(inputArguments, false)) {
            if (isGlobalReset(value, processWorldEdit)) {
                worldName = "";
            }
            else if (value.startsWith("#")) {
                worldName = value.substring(1);
            }
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
        for (String value : extractRadiusValues(inputArguments, false)) {
            if (isWorldEditToken(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse whether to force global search
     * 
     * @param inputArguments
     *            The command arguments
     * @return true if global search should be forced
     */
    public static boolean parseForceGlobal(String[] inputArguments) {
        boolean result = false;
        for (String value : extractRadiusValues(inputArguments, false)) {
            if (isGlobalKeyword(value)) {
                result = true;
            }
            else if (value.startsWith("#") && WorldUtils.matchWorld(value) > 0) {
                result = true;
            }
        }
        return result;
    }

    private static List<String> extractRadiusValues(String[] inputArguments, boolean preserveCase) {
        List<String> values = new ArrayList<>();
        int next = 0;
        for (int index = 1; index < inputArguments.length; index++) {
            String cleaned = ParserUtils.stripQuotesAndBackslashes(inputArguments[index]);
            String normalized = cleaned.toLowerCase(Locale.ROOT);
            if (ParserUtils.equalsAny(normalized, RADIUS_PREFIXES)) {
                next = 2;
                continue;
            }

            String stripped = preserveCase
                    ? ParserUtils.stripPrefixPreservingCase(cleaned, normalized, RADIUS_PREFIXES)
                    : ParserUtils.stripPrefix(normalized, RADIUS_PREFIXES);
            if (next == 2 || stripped != null) {
                values.add(stripped != null ? stripped : (preserveCase ? cleaned : normalized));
                next = 0;
            }
            else {
                next = 0;
            }
        }
        return values;
    }

    private static boolean isWorldEditToken(String lower) {
        return lower.equals("#worldedit") || lower.equals("#we");
    }

    private static boolean isGlobalKeyword(String lower) {
        return lower.equals("#global") || lower.equals("global") || lower.equals("off") || lower.equals("-1") || lower.equals("none") || lower.equals("false");
    }

    private static boolean isGlobalReset(String lower, boolean processWorldEdit) {
        return (processWorldEdit && isWorldEditToken(lower)) || isGlobalKeyword(lower);
    }
}
