package net.coreprotect.command.parser;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;

/**
 * Parser for user-related command arguments
 */
public class UserParser {

    private static final String[] USER_PREFIXES = {
            "users:",
            "user:",
            "p:",
            "u:"
    };

    private static final String[] EXCLUDE_PREFIXES = {
            "exclude:",
            "e:"
    };

    /**
     * Parse users from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return A list of parsed users
     */
    public static List<String> parseUsers(String[] inputArguments) {
        List<String> users = new ArrayList<>();
        int next = 0;

        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);

            boolean startsParameter = MessageFilterParser.isLookupTerminator(argument);
            if (next == 2 && !startsParameter) {
                next = argument.endsWith(",") ? 2 : 0;
                continue;
            }
            if (startsParameter && (next != 1 || argument.indexOf(':') >= 0)) {
                next = 0;
            }
            if (ParserUtils.equalsAny(argument, USER_PREFIXES)) {
                next = 1;
                continue;
            }

            String stripped = ParserUtils.stripPrefix(argument, USER_PREFIXES);
            if (next == 1 || stripped != null) {
                String userSpec = (stripped != null) ? stripped : argument;
                if (userSpec.contains(",")) {
                    for (String part : userSpec.split(",")) {
                        parseUser(users, part, true);
                    }
                    next = userSpec.endsWith(",") ? 1 : 0;
                }
                else {
                    parseUser(users, userSpec, true);
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
                parseUser(users, argument, false);
                next = 0;
            }
        }
        return users;
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
    public static List<String> parseExcludedUsers(CommandSender player, String[] inputArguments) {
        List<String> excluded = new ArrayList<>();
        int next = 0;

        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);

            if (ParserUtils.equalsAny(argument, EXCLUDE_PREFIXES)) {
                next = 5;
                continue;
            }

            String stripped = ParserUtils.stripPrefix(argument, EXCLUDE_PREFIXES);
            if (stripped == null && next == 5 && argument.indexOf(':') >= 0 && MessageFilterParser.isLookupTerminator(argument)) {
                next = 0;
                continue;
            }
            if (next == 5 || stripped != null) {
                String excludeSpec = (stripped != null) ? stripped : argument;
                if (excludeSpec.contains(",")) {
                    for (String token : excludeSpec.split(",")) {
                        addExcludedUserIfNotMaterial(excluded, token);
                    }
                    next = excludeSpec.endsWith(",") ? 5 : 0;
                }
                else {
                    addExcludedUserIfNotMaterial(excluded, excludeSpec);
                    next = 0;
                }
            }
            else {
                next = 0;
            }
        }
        return excluded;
    }

    private static void addExcludedUserIfNotMaterial(List<String> excluded, String token) {
        String candidate = token.trim();
        if (!candidate.isEmpty() && candidate.indexOf(':') < 0 && !MaterialParser.isBlockOrEntity(candidate)) {
            excluded.add(candidate);
        }
    }

    private static void parseUser(List<String> users, String user, boolean explicitPrefix) {
        String check = user.replaceAll("[\\s'\"]", "");
        if (!check.equals(user) || check.isEmpty()) {
            return;
        }
        if (user.equalsIgnoreCase("#global")) {
            users.add("#global");
            return;
        }
        boolean isModifier = ActionParser.isModifier(user) || RollbackStateParser.isModifier(user);
        if (isModifier && (!explicitPrefix || user.startsWith("#"))) {
            return;
        }
        users.add(user);
    }
}
