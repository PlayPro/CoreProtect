package net.coreprotect.command.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser for action-related command arguments
 */
public class ActionParser {

    /**
     * Parse page from command arguments
     * 
     * @param argumentArray
     *            The command arguments
     * @return The processed argument array
     */
    public static String[] parsePage(String[] argumentArray) {
        if (argumentArray.length == 2) {
            argumentArray[1] = argumentArray[1].replaceFirst("page:", "");
        }

        return argumentArray;
    }

    /**
     * Parse action from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return A list of action integers
     */
    public static List<Integer> parseAction(String[] inputArguments) {
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

    /**
     * Parse count flag from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return true if the count flag is present
     */
    public static boolean parseCount(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        boolean result = false;
        int count = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");
                if (argument.equals("#count") || argument.equals("#sum") || 
                    argument.equals("count") || argument.equals("sum")) {
                    result = true;
                }
            }
            count++;
        }
        return result;
    }

    /**
     * Parse noisy flag from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return 1 if noisy/verbose mode is enabled, 0 otherwise
     */
    public static int parseNoisy(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        int noisy = 0;
        int count = 0;
        if (net.coreprotect.config.Config.getGlobal().VERBOSE) {
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

    /**
     * Parse preview flag from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return 1 for preview, 2 for preview cancel, 0 otherwise
     */
    public static int parsePreview(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        int result = 0;
        int count = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");
                if (argument.equals("#preview") || argument.equals("preview")) {
                    result = 1;
                }
                else if (argument.equals("#preview_cancel") || argument.equals("#preview-cancel") || 
                         argument.equals("preview_cancel") || argument.equals("preview-cancel")) {
                    result = 2;
                }
            }
            count++;
        }
        return result;
    }
}
