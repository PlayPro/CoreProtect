package net.coreprotect.command.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.CommandSender;

/**
 * Parser for user-related command arguments
 */
public class UserParser {

    /**
     * Parse users from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return A list of parsed users
     */
    public static List<String> parseUsers(String[] inputArguments) {
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
                            boolean isBlock = MaterialParser.isBlockOrEntity(i3);
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
                        boolean isBlock = MaterialParser.isBlockOrEntity(argument);
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

    /**
     * Process a user string and add it to the users list if valid
     * 
     * @param users
     *            The list to add the user to
     * @param user
     *            The user string to process
     */
    private static void parseUser(List<String> users, String user) {
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
}
