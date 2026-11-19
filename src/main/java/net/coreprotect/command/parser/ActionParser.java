package net.coreprotect.command.parser;

import java.util.ArrayList;
import java.util.List;

import net.coreprotect.config.Config;
import net.coreprotect.model.action.EntityActionFilter;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.action.SessionActions;
import net.coreprotect.model.item.ItemTransactionActions;

/**
 * Parser for action-related command arguments
 */
public class ActionParser {

    private static final String[] ACTION_PREFIXES = {
            "action:",
            "a:"
    };

    /**
     * Parse page from command arguments
     * 
     * @param argumentArray
     *            The command arguments
     * @return The processed argument array
     */
    public static String[] parsePage(String[] argumentArray) {
        if (argumentArray.length == 2) {
            String raw = argumentArray[1];
            String cleaned = ParserUtils.stripQuotesAndBackslashes(raw);
            String normalized = ParserUtils.normalize(raw);
            String stripped = ParserUtils.stripPrefixPreservingCase(cleaned, normalized, "page:");
            if (stripped != null) {
                String[] copy = argumentArray.clone();
                copy[1] = stripped;
                return copy;
            }
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
        return parseAction(inputArguments, false);
    }

    public static List<Integer> parseAction(String[] inputArguments, boolean allowMultiple) {
        return parseActions(inputArguments, allowMultiple).getActions();
    }

    public static ParseResult parseActions(String[] inputArguments, boolean allowMultiple) {
        List<Integer> result = new ArrayList<>();
        EntityActionFilter entityActionFilter = EntityActionFilter.DEFAULT;
        int next = 0;

        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);

            if (next == 1 && MessageFilterParser.isLookupTerminator(argument)) {
                return invalidAction();
            }
            if (ParserUtils.equalsAny(argument, ACTION_PREFIXES)) {
                next = 1;
                continue;
            }

            String stripped = ParserUtils.stripPrefix(argument, ACTION_PREFIXES);
            if (next == 1 || stripped != null) {
                String actionSpec = (stripped != null) ? stripped : argument;
                result.clear();
                entityActionFilter = EntityActionFilter.NONE;
                if (actionSpec.startsWith("#")) {
                    actionSpec = actionSpec.substring(1);
                }
                if (allowMultiple && actionSpec.contains(",")) {
                    for (String action : actionSpec.split(",", -1)) {
                        if (!action.isEmpty()) {
                            ParseResult parsedAction = parseActions(new String[] { "lookup", "a:" + action }, false);
                            if (parsedAction.getActions().contains(-1)) {
                                return invalidAction();
                            }
                            result.addAll(parsedAction.getActions());
                            entityActionFilter = entityActionFilter.merge(parsedAction.getEntityActionFilter());
                        }
                    }
                    if (result.isEmpty()) {
                        return invalidAction();
                    }
                    next = 0;
                    continue;
                }
                entityActionFilter = applySingleAction(actionSpec, result);
                if (result.contains(-1)) {
                    return invalidAction();
                }
                next = 0;
            }
            else {
                next = 0;
            }
        }
        return next == 1 ? invalidAction() : new ParseResult(result, entityActionFilter);
    }

    private static ParseResult invalidAction() {
        return new ParseResult(List.of(-1), EntityActionFilter.NONE);
    }

    private static EntityActionFilter applySingleAction(String argument, List<Integer> result) {
        if (argument.equals("broke") || argument.equals("break") || argument.equals("remove") || argument.equals("destroy") || argument.equals("block-break") || argument.equals("block-remove") || argument.equals("-block") || argument.equals("-blocks") || argument.equals("block-")) {
            result.add(LookupActions.BLOCK_BREAK);
            return EntityActionFilter.ALIASED;
        }
        if (argument.equals("placed") || argument.equals("place") || argument.equals("block-place") || argument.equals("+block") || argument.equals("+blocks") || argument.equals("block+")) {
            result.add(LookupActions.BLOCK_PLACE);
            return EntityActionFilter.ALIASED;
        }
        if (argument.equals("block") || argument.equals("blocks") || argument.equals("block-change") || argument.equals("change") || argument.equals("changes")) {
            result.add(LookupActions.BLOCK_BREAK);
            result.add(LookupActions.BLOCK_PLACE);
            return EntityActionFilter.ALIASED;
        }
        if (argument.equals("click") || argument.equals("clicks") || argument.equals("interact") || argument.equals("interaction") || argument.equals("player-interact") || argument.equals("player-interaction") || argument.equals("player-click")) {
            result.add(LookupActions.INTERACTION);
        }
        else if (argument.equals("death") || argument.equals("deaths") || argument.equals("entity-death") || argument.equals("entity-deaths") || argument.equals("kill") || argument.equals("kills") || argument.equals("entity-kill") || argument.equals("entity-kills")) {
            result.add(LookupActions.ENTITY_KILL);
        }
        else if (argument.equals("spawn") || argument.equals("spawns") || argument.equals("entity-spawn") || argument.equals("entity-spawns")) {
            result.add(LookupActions.ENTITY_SPAWN);
            return EntityActionFilter.SPAWNED;
        }
        else if (argument.equals("container") || argument.equals("container-change") || argument.equals("containers") || argument.equals("chest") || argument.equals("transaction") || argument.equals("transactions")) {
            result.add(LookupActions.CONTAINER);
        }
        else if (argument.equals("-container") || argument.equals("container-") || argument.equals("remove-container")) {
            result.add(LookupActions.CONTAINER);
            result.add(ItemTransactionActions.REMOVE);
        }
        else if (argument.equals("+container") || argument.equals("container+") || argument.equals("container-add") || argument.equals("add-container")) {
            result.add(LookupActions.CONTAINER);
            result.add(ItemTransactionActions.ADD);
        }
        else if (argument.equals("chat") || argument.equals("chats") || argument.equals("message") || argument.equals("messages")) {
            result.add(LookupActions.CHAT);
        }
        else if (argument.equals("command") || argument.equals("commands")) {
            result.add(LookupActions.COMMAND);
        }
        else if (argument.equals("logins") || argument.equals("login") || argument.equals("+session") || argument.equals("+sessions") || argument.equals("session+") || argument.equals("+connection") || argument.equals("connection+")) {
            result.add(LookupActions.SESSION);
            result.add(SessionActions.LOGIN);
        }
        else if (argument.equals("logout") || argument.equals("logouts") || argument.equals("-session") || argument.equals("-sessions") || argument.equals("session-") || argument.equals("-connection") || argument.equals("connection-")) {
            result.add(LookupActions.SESSION);
            result.add(SessionActions.LOGOUT);
        }
        else if (argument.equals("session") || argument.equals("sessions") || argument.equals("connection") || argument.equals("connections")) {
            result.add(LookupActions.SESSION);
        }
        else if (argument.equals("username") || argument.equals("usernames") || argument.equals("user") || argument.equals("users") || argument.equals("name") || argument.equals("names") || argument.equals("uuid") || argument.equals("uuids") || argument.equals("username-change") || argument.equals("username-changes") || argument.equals("name-change") || argument.equals("name-changes")) {
            result.add(LookupActions.USERNAME);
        }
        else if (argument.equals("sign") || argument.equals("signs")) {
            result.add(LookupActions.SIGN);
        }
        else if (argument.equals("inv") || argument.equals("inventory") || argument.equals("inventories")) {
            result.add(LookupActions.CONTAINER);
            result.add(LookupActions.ITEM);
        }
        else if (argument.equals("-inv") || argument.equals("inv-") || argument.equals("-inventory") || argument.equals("inventory-") || argument.equals("-inventories")) {
            result.add(LookupActions.CONTAINER);
            result.add(LookupActions.ITEM);
            result.add(ItemTransactionActions.ADD);
        }
        else if (argument.equals("+inv") || argument.equals("inv+") || argument.equals("+inventory") || argument.equals("inventory+") || argument.equals("+inventories")) {
            result.add(LookupActions.CONTAINER);
            result.add(LookupActions.ITEM);
            result.add(ItemTransactionActions.REMOVE);
        }
        else if (argument.equals("item") || argument.equals("items")) {
            result.add(LookupActions.ITEM);
        }
        else if (argument.equals("-item") || argument.equals("item-") || argument.equals("-items") || argument.equals("items-") || argument.equals("drop") || argument.equals("drops") || argument.equals("deposit") || argument.equals("deposits") || argument.equals("deposited")) {
            result.add(LookupActions.ITEM);
            result.add(ItemTransactionActions.REMOVE);
        }
        else if (argument.equals("+item") || argument.equals("item+") || argument.equals("+items") || argument.equals("items+") || argument.equals("pickup") || argument.equals("pickups") || argument.equals("withdraw") || argument.equals("withdraws") || argument.equals("withdrew")) {
            result.add(LookupActions.ITEM);
            result.add(ItemTransactionActions.ADD);
        }
        else {
            result.add(-1);
        }
        return EntityActionFilter.NONE;
    }

    public static final class ParseResult {

        private final List<Integer> actions;
        private final EntityActionFilter entityActionFilter;

        private ParseResult(List<Integer> actions, EntityActionFilter entityActionFilter) {
            this.actions = actions;
            this.entityActionFilter = entityActionFilter;
        }

        public List<Integer> getActions() {
            return actions;
        }

        public EntityActionFilter getEntityActionFilter() {
            return entityActionFilter;
        }
    }

    /**
     * Parse count flag from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return true if the count flag is present
     */
    public static boolean parseCount(String[] inputArguments) {
        for (int index = 1; index < inputArguments.length; index++) {
            if (isCountFlag(ParserUtils.normalize(inputArguments[index]))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse summary output flag from command arguments
     *
     * @param inputArguments
     *            The command arguments
     * @return true if the summary flag is present
     */
    public static boolean parseSummary(String[] inputArguments) {
        for (int index = 1; index < inputArguments.length; index++) {
            if (isSummaryFlag(ParserUtils.normalize(inputArguments[index]))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse noisy flag from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return 1 if noisy/verbose mode is enabled, 0 otherwise
     */
    public static int parseNoisy(String[] inputArguments) {
        int noisy = Config.getGlobal().VERBOSE ? 1 : 0;
        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);
            if (isVerboseFlag(argument)) {
                noisy = 1;
            }
            else if (isSilentFlag(argument)) {
                noisy = 0;
            }
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
        int result = 0;
        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);
            if (isPreviewNormalized(argument)) {
                result = 1;
            }
            else if (isPreviewCancelNormalized(argument)) {
                result = 2;
            }
        }
        return result;
    }

    public static boolean isPreviewArgument(String rawArgument) {
        return isPreviewNormalized(ParserUtils.normalize(rawArgument));
    }

    public static boolean isModifier(String rawArgument) {
        String normalized = ParserUtils.normalize(rawArgument);
        return isCountFlag(normalized)
                || isSummaryFlag(normalized)
                || isVerboseFlag(normalized)
                || isSilentFlag(normalized)
                || isPreviewNormalized(normalized)
                || isPreviewCancelNormalized(normalized);
    }

    private static boolean isCountFlag(String normalized) {
        return normalized.equals("#count") || normalized.equals("#sum") || normalized.equals("count") || normalized.equals("sum");
    }

    private static boolean isSummaryFlag(String normalized) {
        return normalized.equals("#summary");
    }

    private static boolean isVerboseFlag(String normalized) {
        return normalized.equals("n") || normalized.equals("noisy") || normalized.equals("v") || normalized.equals("verbose") || normalized.equals("#v") || normalized.equals("#verbose");
    }

    private static boolean isSilentFlag(String normalized) {
        return normalized.equals("#silent") || normalized.equals("silent");
    }

    private static boolean isPreviewNormalized(String normalized) {
        return normalized.equals("#preview") || normalized.equals("preview");
    }

    private static boolean isPreviewCancelNormalized(String normalized) {
        return normalized.equals("#preview_cancel") || normalized.equals("#preview-cancel") || normalized.equals("preview_cancel") || normalized.equals("preview-cancel");
    }
}
