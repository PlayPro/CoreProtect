package net.coreprotect.language;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.coreprotect.utility.ChatMessage;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.StringUtils;

public enum Phrase {

    ACTION_NOT_SUPPORTED,
    AMOUNT_BLOCK,
    AMOUNT_CHUNK,
    AMOUNT_ENTITY,
    AMOUNT_ITEM,
    API_TEST,
    CACHE_ERROR,
    CACHE_RELOAD,
    CHECK_CONFIG,
    COMMAND_CONSOLE,
    COMMAND_NOT_FOUND,
    COMMAND_THROTTLED,
    CONSUMER_ERROR,
    CONSUMER_TOGGLED,
    CONTAINER_HEADER,
    CPU_CORES,
    DATABASE_BUSY,
    DATABASE_INDEX_ERROR,
    DATABASE_LOCKED_1,
    DATABASE_LOCKED_2,
    DATABASE_LOCKED_3,
    DATABASE_LOCKED_4,
    DATABASE_UNREACHABLE,
    DEVELOPMENT_BRANCH,
    DIRT_BLOCK,
    DISABLE_SUCCESS,
    DONATION_KEY_REQUIRED,
    ENABLE_FAILED,
    ENABLE_SUCCESS,
    ENJOY_COREPROTECT,
    FINISHING_CONVERSION,
    FINISHING_LOGGING,
    FIRST_VERSION,
    GLOBAL_LOOKUP,
    GLOBAL_ROLLBACK,
    HELP_ACTION_1,
    HELP_ACTION_2,
    HELP_COMMAND,
    HELP_EXCLUDE_1,
    HELP_EXCLUDE_2,
    HELP_HEADER,
    HELP_INCLUDE_1,
    HELP_INCLUDE_2,
    HELP_INSPECT_1,
    HELP_INSPECT_2,
    HELP_INSPECT_3,
    HELP_INSPECT_4,
    HELP_INSPECT_5,
    HELP_INSPECT_6,
    HELP_INSPECT_7,
    HELP_INSPECT_COMMAND,
    HELP_LIST,
    HELP_LOOKUP_1,
    HELP_LOOKUP_2,
    HELP_LOOKUP_COMMAND,
    HELP_NO_INFO,
    HELP_PARAMETER,
    HELP_PARAMS_1,
    HELP_PARAMS_2,
    HELP_PARAMS_3,
    HELP_PARAMS_4,
    HELP_PARAMS_5,
    HELP_PARAMS_6,
    HELP_PARAMS_7,
    HELP_PURGE_1,
    HELP_PURGE_2,
    HELP_PURGE_COMMAND,
    HELP_RADIUS_1,
    HELP_RADIUS_2,
    HELP_RELOAD_COMMAND,
    HELP_RESTORE_COMMAND,
    HELP_ROLLBACK_COMMAND,
    HELP_STATUS,
    HELP_STATUS_COMMAND,
    HELP_TELEPORT,
    HELP_TIME_1,
    HELP_TIME_2,
    HELP_USER_1,
    HELP_USER_2,
    INCOMPATIBLE_ACTION,
    INSPECTOR_ERROR,
    INSPECTOR_TOGGLED,
    INTEGRATION_ERROR,
    INTEGRATION_SUCCESS,
    INTEGRATION_VERSION,
    INTERACTIONS_HEADER,
    INVALID_ACTION,
    INVALID_BRANCH_1,
    INVALID_BRANCH_2,
    INVALID_BRANCH_3,
    INVALID_CONTAINER,
    INVALID_DONATION_KEY,
    INVALID_INCLUDE,
    INVALID_INCLUDE_COMBO,
    INVALID_RADIUS,
    INVALID_SELECTION,
    INVALID_USERNAME,
    INVALID_WORLD,
    LATEST_VERSION,
    LINK_DISCORD,
    LINK_DOWNLOAD,
    LINK_PATREON,
    LINK_WIKI_BLOCK,
    LINK_WIKI_ENTITY,
    LOGGING_ITEMS,
    LOGGING_TIME_LIMIT,
    LOOKUP_BLOCK,
    LOOKUP_CONTAINER,
    LOOKUP_HEADER,
    LOOKUP_INTERACTION,
    LOOKUP_ITEM,
    LOOKUP_LOGIN,
    LOOKUP_PAGE,
    LOOKUP_PROJECTILE,
    LOOKUP_ROWS_FOUND,
    LOOKUP_SEARCHING,
    LOOKUP_STORAGE,
    LOOKUP_TIME,
    LOOKUP_USERNAME,
    MAXIMUM_RADIUS,
    MISSING_ACTION_USER,
    MISSING_LOOKUP_TIME,
    MISSING_LOOKUP_USER,
    MISSING_PARAMETERS,
    MISSING_ROLLBACK_RADIUS,
    MISSING_ROLLBACK_USER,
    MYSQL_UNAVAILABLE,
    NETWORK_CONNECTION,
    NETWORK_TEST,
    NO_DATA,
    NO_DATA_LOCATION,
    NO_PERMISSION,
    NO_RESULTS,
    NO_RESULTS_PAGE,
    NO_ROLLBACK,
    PATCH_INTERRUPTED,
    PATCH_OUTDATED_1,
    PATCH_OUTDATED_2,
    PATCH_PROCESSING,
    PATCH_SKIP_UPDATE,
    PATCH_STARTED,
    PATCH_SUCCESS,
    PATCH_UPGRADING,
    PLEASE_SELECT,
    PREVIEW_CANCELLED,
    PREVIEW_CANCELLING,
    PREVIEW_IN_GAME,
    PREVIEW_TRANSACTION,
    PRIMARY_THREAD_ERROR,
    PURGE_ABORTED,
    PURGE_ERROR,
    PURGE_FAILED,
    PURGE_IN_PROGRESS,
    PURGE_MINIMUM_TIME,
    PURGE_NOTICE_1,
    PURGE_NOTICE_2,
    PURGE_OPTIMIZING,
    PURGE_PROCESSING,
    PURGE_REPAIRING,
    PURGE_ROWS,
    PURGE_STARTED,
    PURGE_SUCCESS,
    RAM_STATS,
    RELOAD_STARTED,
    RELOAD_SUCCESS,
    ROLLBACK_ABORTED,
    ROLLBACK_CHUNKS_FOUND,
    ROLLBACK_CHUNKS_MODIFIED,
    ROLLBACK_COMPLETED,
    ROLLBACK_EXCLUDED_USERS,
    ROLLBACK_INCLUDE,
    ROLLBACK_IN_PROGRESS,
    ROLLBACK_LENGTH,
    ROLLBACK_MODIFIED,
    ROLLBACK_RADIUS,
    ROLLBACK_SELECTION,
    ROLLBACK_STARTED,
    ROLLBACK_TIME,
    ROLLBACK_WORLD_ACTION,
    SIGN_HEADER,
    STATUS_CONSUMER,
    STATUS_DATABASE,
    STATUS_INTEGRATION,
    STATUS_LICENSE,
    STATUS_SYSTEM,
    STATUS_VERSION,
    TELEPORTED,
    TELEPORTED_SAFETY,
    TELEPORT_PLAYERS,
    TIME_DAYS,
    TIME_HOURS,
    TIME_MINUTES,
    TIME_MONTHS,
    TIME_SECONDS,
    TIME_WEEKS,
    TIME_YEARS,
    UPDATE_ERROR,
    UPDATE_HEADER,
    UPDATE_NOTICE,
    UPGRADE_IN_PROGRESS,
    USER_NOT_FOUND,
    USER_OFFLINE,
    USING_MYSQL,
    USING_SQLITE,
    VALID_DONATION_KEY,
    VERSION_NOTICE,
    VERSION_INCOMPATIBLE,
    VERSION_REQUIRED,
    WORLD_NOT_FOUND;

    final private static Set<Phrase> HEADERS = new HashSet<>(Arrays.asList(Phrase.CONTAINER_HEADER, Phrase.HELP_HEADER, Phrase.INTERACTIONS_HEADER, Phrase.LOOKUP_HEADER, Phrase.SIGN_HEADER, Phrase.UPDATE_HEADER));
    final private static Set<String> COLORS = new HashSet<>(Arrays.asList(Color.WHITE, Color.DARK_AQUA));
    final private static String SPLIT = ":";
    final private static String FULL_WIDTH_SPLIT = "：";

    public String getPhrase() {
        return Language.getPhrase(this);
    }

    public String getUserPhrase() {
        return Language.getUserPhrase(this);
    }

    public String getTranslatedPhrase() {
        return Language.getTranslatedPhrase(this);
    }

    public static String build(Phrase phrase, String... params) {
        String output = phrase.getTranslatedPhrase();

        // If translated phrase is null, fall back to the default phrase
        if (output == null) {
            output = phrase.getPhrase();
            // If that's still null, use an empty string to avoid NullPointerException
            if (output == null) {
                output = "";
            }
        }

        String color = "";

        if (HEADERS.contains(phrase)) {
            output = StringUtils.capitalize(output, true);
        }

        int index = 0;
        int indexExtra = 0;
        for (String param : params) {
            if (index == 0 && COLORS.contains(param)) {
                color = param;
                indexExtra++;
                continue;
            }

            if (Selector.SELECTORS.contains(param)) {
                output = Selector.processSelection(output, param, color);
                indexExtra++;
                continue;
            }

            if (output.contains("{" + index + "}")) {
                output = output.replace("{" + index + "}", param);
                index++;
            }
        }

        if ((index + indexExtra) != params.length) { // fallback for issues with user modified phrases
            // System.out.println("buildInternal"); // debug
            output = buildInternal(phrase, params, color);
        }

        if (color.length() > 0) {
            output = output.replaceFirst(SPLIT, SPLIT + color);
            output = output.replaceFirst(FULL_WIDTH_SPLIT, FULL_WIDTH_SPLIT + color);
            output = ChatMessage.parseQuotes(output, color);
        }

        return output;
    }

    private static String buildInternal(Phrase phrase, String[] params, String color) {
        String output = phrase.getPhrase(); // get internal phrase

        // If internal phrase is null, use an empty string to avoid NullPointerException
        if (output == null) {
            output = "";
            return output; // Return empty string immediately if no phrase is available
        }

        int index = 0;
        for (String param : params) {
            if (index == 0 && COLORS.contains(param)) {
                continue;
            }
            if (Selector.SELECTORS.contains(param)) {
                output = Selector.processSelection(output, param, color);
                continue;
            }
            output = output.replace("{" + index + "}", param);
            index++;
        }

        return output;
    }

    public static String getPhraseSelector(Phrase phrase, String selector) {
        String translatedPhrase = phrase.getTranslatedPhrase();
        // Return empty string if translated phrase is null
        if (translatedPhrase == null) {
            return "";
        }

        Pattern phrasePattern = Pattern.compile("(\\{[a-zA-Z| ]+})");
        Matcher patternMatch = phrasePattern.matcher(translatedPhrase);
        String match = "";
        if (patternMatch.find()) {
            match = patternMatch.group(1);
            match = Selector.processSelection(match, selector, "");
        }

        return match;
    }
}
