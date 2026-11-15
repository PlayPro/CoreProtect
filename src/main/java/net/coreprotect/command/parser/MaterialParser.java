package net.coreprotect.command.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import net.coreprotect.language.Phrase;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.BlockTypeUtils;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.MaterialUtils;

/**
 * Parser for material and entity related command arguments
 */
public class MaterialParser {

    private static final String[] INCLUDE_PREFIXES = {
            "include:",
            "blocks:",
            "block:",
            "items:",
            "item:",
            "i:",
            "b:"
    };

    private static final String[] EXCLUDE_PREFIXES = {
            "exclude:",
            "e:"
    };

    public static String stripIncludePrefix(String normalizedArgument) {
        return ParserUtils.stripPrefix(normalizedArgument, INCLUDE_PREFIXES);
    }

    public static String stripExcludePrefix(String normalizedArgument) {
        return ParserUtils.stripPrefix(normalizedArgument, EXCLUDE_PREFIXES);
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
    public static List<Object> parseRestricted(CommandSender player, String[] inputArguments, List<Integer> argAction) {
        List<Object> restricted = new ArrayList<>();
        int next = 0;

        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);

            if (ParserUtils.equalsAny(argument, INCLUDE_PREFIXES)) {
                next = 4;
                continue;
            }

            String stripped = stripIncludePrefix(argument);
            if (next == 4 || stripped != null) {
                String spec = (stripped != null) ? stripped : argument;
                if (spec.contains(",")) {
                    for (String part : spec.split(",")) {
                        String candidate = part.trim();
                        if (!candidate.isEmpty() && !addRestrictedTarget(player, candidate, argAction, restricted)) {
                            return null;
                        }
                    }
                    next = spec.endsWith(",") ? 4 : 0;
                }
                else {
                    if (!addRestrictedTarget(player, spec, argAction, restricted)) {
                        return null;
                    }
                    next = 0;
                }
            }
            else {
                next = 0;
            }
        }
        return restricted;
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
    public static Map<Object, Boolean> parseExcluded(CommandSender player, String[] inputArguments, List<Integer> argAction) {
        Map<Object, Boolean> excluded = new HashMap<>();
        int next = 0;

        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);

            if (ParserUtils.equalsAny(argument, EXCLUDE_PREFIXES)) {
                next = 5;
                continue;
            }

            String stripped = stripExcludePrefix(argument);
            if (next == 5 || stripped != null) {
                String spec = (stripped != null) ? stripped : argument;
                if (spec.contains(",")) {
                    for (String part : spec.split(",")) {
                        String candidate = part.trim();
                        if (!candidate.isEmpty()) {
                            addExcludedTarget(candidate, argAction, excluded);
                        }
                    }
                    next = spec.endsWith(",") ? 5 : 0;
                }
                else {
                    addExcludedTarget(spec, argAction, excluded);
                    next = 0;
                }
            }
            else {
                next = 0;
            }
        }
        return excluded;
    }

    private static boolean addRestrictedTarget(CommandSender player, String token, List<Integer> argAction, List<Object> restricted) {
        if (checkTags(token, restricted)) {
            return true;
        }
        Object target = resolveTarget(token, argAction);
        if (target != null) {
            restricted.add(target);
            return true;
        }
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_INCLUDE, token));
        return false;
    }

    private static void addExcludedTarget(String token, List<Integer> argAction, Map<Object, Boolean> excluded) {
        if (checkTags(token, excluded)) {
            return;
        }
        Object target = resolveTarget(token, argAction);
        if (target != null) {
            excluded.put(target, false);
        }
    }

    private static Object resolveTarget(String token, List<Integer> argAction) {
        boolean containerAction = argAction != null && argAction.contains(LookupActions.CONTAINER);
        Material material = MaterialUtils.getType(token);
        if (material != null && (material.isBlock() || containerAction)) {
            return material;
        }
        if (!containerAction && BlockTypeUtils.hasBlockType(token)) {
            return BlockTypeUtils.normalizeKey(token);
        }
        EntityType entityType = EntityUtils.getEntityType(token);
        if (entityType != null) {
            return entityType;
        }
        return material;
    }

    /**
     * Get a map of block tags and their associated materials
     * 
     * @return A map of block tags and their associated materials
     */
    public static Map<String, Set<Material>> getTags() {
        Map<String, Set<Material>> tagMap = new HashMap<>();
        tagMap.put("#button", BlockGroup.BUTTONS);
        tagMap.put("#sign", BlockGroup.SIGNS);
        tagMap.put("#container", BlockGroup.CONTAINERS);
        tagMap.put("#door", BlockGroup.DOORS);
        tagMap.put("#natural", BlockGroup.NATURAL_BLOCKS);
        tagMap.put("#pressure_plate", BlockGroup.PRESSURE_PLATES);
        tagMap.put("#shulker_box", BlockGroup.SHULKER_BOXES);
        if (!BlockGroup.BUNDLES.isEmpty()) {
            tagMap.put("#bundle", BlockGroup.BUNDLES);
        }
        return tagMap;
    }

    private static Set<Material> resolveTagMaterials(String argument) {
        if (argument == null || !argument.startsWith("#")) {
            return null;
        }
        switch (argument) {
            case "#button":
                return BlockGroup.BUTTONS;
            case "#sign":
                return BlockGroup.SIGNS;
            case "#container":
                return BlockGroup.CONTAINERS;
            case "#door":
                return BlockGroup.DOORS;
            case "#natural":
                return BlockGroup.NATURAL_BLOCKS;
            case "#pressure_plate":
                return BlockGroup.PRESSURE_PLATES;
            case "#shulker_box":
                return BlockGroup.SHULKER_BOXES;
            case "#bundle":
                return BlockGroup.BUNDLES.isEmpty() ? null : BlockGroup.BUNDLES;
            default:
                return null;
        }
    }

    /**
     * Check if an argument matches a block tag
     * 
     * @param argument
     *            The argument to check
     * @return true if the argument matches a block tag
     */
    public static boolean checkTags(String argument) {
        return resolveTagMaterials(argument) != null;
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
    public static boolean checkTags(String argument, Map<Object, Boolean> list) {
        Set<Material> materials = resolveTagMaterials(argument);
        if (materials == null) {
            return false;
        }
        for (Material block : materials) {
            list.put(block, false);
        }
        return true;
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
    public static boolean checkTags(String argument, List<Object> list) {
        Set<Material> materials = resolveTagMaterials(argument);
        if (materials == null) {
            return false;
        }
        list.addAll(materials);
        return true;
    }

    /**
     * Check if a string represents a block or entity
     * 
     * @param argument
     *            The string to check
     * @return true if the string represents a block or entity
     */
    public static boolean isBlockOrEntity(String argument) {
        return checkTags(argument)
                || MaterialUtils.getType(argument) != null
                || BlockTypeUtils.hasBlockType(argument)
                || EntityUtils.getEntityType(argument) != null;
    }
}
