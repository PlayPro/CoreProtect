package net.coreprotect.command.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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

    private static final Map<String, Supplier<Set<Material>>> TAGS = Map.of(
            "#button", () -> BlockGroup.BUTTONS,
            "#sign", () -> BlockGroup.SIGNS,
            "#container", () -> BlockGroup.CONTAINERS,
            "#door", () -> BlockGroup.DOORS,
            "#natural", () -> BlockGroup.NATURAL_BLOCKS,
            "#pressure_plate", () -> BlockGroup.PRESSURE_PLATES,
            "#shulker_box", () -> BlockGroup.SHULKER_BOXES,
            "#bundle", () -> BlockGroup.BUNDLES.isEmpty() ? null : BlockGroup.BUNDLES);

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
        String pendingPrefix = null;

        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);

            if (ParserUtils.equalsAny(argument, EXCLUDE_PREFIXES)) {
                if (pendingPrefix != null) {
                    rejectTarget(player, pendingPrefix);
                    return null;
                }
                pendingPrefix = argument;
                next = 5;
                continue;
            }

            String stripped = stripExcludePrefix(argument);
            boolean startsParameter = argument.indexOf(':') >= 0 && MessageFilterParser.isLookupTerminator(argument);
            if (pendingPrefix != null && startsParameter) {
                rejectTarget(player, pendingPrefix);
                return null;
            }
            if (stripped == null && next == 5 && startsParameter) {
                next = 0;
                continue;
            }
            if (next == 5 || stripped != null) {
                pendingPrefix = null;
                String spec = (stripped != null) ? stripped : argument;
                if (spec.contains(",")) {
                    for (String part : spec.split(",")) {
                        String candidate = part.trim();
                        if (!candidate.isEmpty() && !addExcludedTarget(player, candidate, argAction, excluded)) {
                            return null;
                        }
                    }
                    next = spec.endsWith(",") ? 5 : 0;
                }
                else {
                    if (!addExcludedTarget(player, spec, argAction, excluded)) {
                        return null;
                    }
                    next = 0;
                }
            }
            else {
                next = 0;
            }
        }
        if (pendingPrefix != null) {
            rejectTarget(player, pendingPrefix);
            return null;
        }
        return excluded;
    }

    private static boolean addRestrictedTarget(CommandSender player, String token, List<Integer> argAction, List<Object> restricted) {
        if (checkTags(token, restricted)) {
            return true;
        }
        Object target = hasInvalidNamespace(token) ? null : resolveTarget(token, argAction);
        if (target != null) {
            restricted.add(target);
            return true;
        }
        return rejectTarget(player, token);
    }

    private static boolean addExcludedTarget(CommandSender player, String token, List<Integer> argAction, Map<Object, Boolean> excluded) {
        if (hasInvalidNamespace(token)) {
            return rejectTarget(player, token);
        }
        if (!checkTags(token, excluded)) {
            Object target = resolveTarget(token, argAction);
            if (target != null) {
                excluded.put(target, false);
            }
            else if (token.indexOf(':') >= 0) {
                return rejectTarget(player, token);
            }
        }
        return true;
    }

    private static boolean rejectTarget(CommandSender player, String token) {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_INCLUDE, token));
        return false;
    }

    private static boolean hasInvalidNamespace(String token) {
        int separator = token.indexOf(':');
        return separator >= 0 && (separator == token.length() - 1 || token.indexOf(':', separator + 1) >= 0);
    }

    private static String normalizeDefaultNamespace(String token) {
        return token.startsWith(":") ? token.substring(1) : token;
    }

    private static Object resolveTarget(String token, List<Integer> argAction) {
        token = normalizeDefaultNamespace(token);
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
        TAGS.forEach((tag, supplier) -> {
            Set<Material> materials = supplier.get();
            if (materials != null) {
                tagMap.put(tag, materials);
            }
        });
        return tagMap;
    }

    private static Set<Material> resolveTagMaterials(String argument) {
        Supplier<Set<Material>> supplier = argument == null ? null : TAGS.get(argument);
        return supplier == null ? null : supplier.get();
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
        if (argument == null || hasInvalidNamespace(argument)) {
            return false;
        }
        String target = normalizeDefaultNamespace(argument);
        return checkTags(argument)
                || MaterialUtils.getType(target) != null
                || BlockTypeUtils.hasBlockType(target)
                || EntityUtils.getEntityType(target) != null;
    }
}
