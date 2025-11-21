package net.coreprotect.language;

import java.util.concurrent.ConcurrentHashMap;

public class Language {

    private static final ConcurrentHashMap<Phrase, String> PHRASES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Phrase, String> USER_PHRASES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Phrase, String> TRANSLATED_PHRASES = new ConcurrentHashMap<>();

    protected static String getPhrase(Phrase phrase) {
        return PHRASES.get(phrase);
    }

    protected static String getUserPhrase(Phrase phrase) {
        return USER_PHRASES.get(phrase);
    }

    protected static String getTranslatedPhrase(Phrase phrase) {
        return TRANSLATED_PHRASES.get(phrase);
    }

    protected static void setUserPhrase(Phrase phrase, String value) {
        USER_PHRASES.put(phrase, value);
    }

    protected static void setTranslatedPhrase(Phrase phrase, String value) {
        TRANSLATED_PHRASES.put(phrase, value);
    }

    public static void loadPhrases() {
        PHRASES.put(Phrase.ACTION_NOT_SUPPORTED, "That action is not supported by the command.");
        PHRASES.put(Phrase.AMOUNT_BLOCK, "{0} {block|blocks}");
        PHRASES.put(Phrase.AMOUNT_CHUNK, "{0} {chunk|chunks}");
        PHRASES.put(Phrase.AMOUNT_ENTITY, "{0} {entity|entities}");
        PHRASES.put(Phrase.AMOUNT_ITEM, "{0} {item|items}");
        PHRASES.put(Phrase.API_TEST, "API test successful.");
        PHRASES.put(Phrase.CACHE_ERROR, "WARNING: Error while validating {0} cache.");
        PHRASES.put(Phrase.CACHE_RELOAD, "Forcing reload of {mapping|world} caches from database.");
        PHRASES.put(Phrase.CHECK_CONFIG, "Please check config.yml");
        PHRASES.put(Phrase.COMMAND_CONSOLE, "Please run the command from the console.");
        PHRASES.put(Phrase.COMMAND_NOT_FOUND, "Command \"{0}\" not found.");
        PHRASES.put(Phrase.COMMAND_THROTTLED, "Please wait a moment and try again.");
        PHRASES.put(Phrase.CONSUMER_ERROR, "Consumer queue processing already {paused|resumed}.");
        PHRASES.put(Phrase.CONSUMER_TOGGLED, "Consumer queue processing has been {paused|resumed}.");
        PHRASES.put(Phrase.CONTAINER_HEADER, "Container Transactions");
        PHRASES.put(Phrase.CPU_CORES, "CPU cores.");
        PHRASES.put(Phrase.DATABASE_BUSY, "Database busy. Please try again later.");
        PHRASES.put(Phrase.DATABASE_INDEX_ERROR, "Unable to validate database indexes.");
        PHRASES.put(Phrase.DATABASE_LOCKED_1, "Database locked. Waiting up to 15 seconds...");
        PHRASES.put(Phrase.DATABASE_LOCKED_2, "Database is already in use. Please try again.");
        PHRASES.put(Phrase.DATABASE_LOCKED_3, "To disable database locking, set \"database-lock: false\".");
        PHRASES.put(Phrase.DATABASE_LOCKED_4, "Disabling database locking can result in data corruption.");
        PHRASES.put(Phrase.DATABASE_UNREACHABLE, "Database is unreachable. Discarding data and shutting down.");
        PHRASES.put(Phrase.DEVELOPMENT_BRANCH, "Development branch detected, skipping patch scripts.");
        PHRASES.put(Phrase.DIRT_BLOCK, "Placed a temporary safety block under you.");
        PHRASES.put(Phrase.DISABLE_SUCCESS, "Success! Disabled {0}");
        PHRASES.put(Phrase.DONATION_KEY_REQUIRED, "A valid donation key is required for that command.");
        PHRASES.put(Phrase.ENABLE_FAILED, "{0} was unable to start.");
        PHRASES.put(Phrase.ENABLE_SUCCESS, "{0} has been successfully enabled!");
        PHRASES.put(Phrase.ENJOY_COREPROTECT, "Enjoy {0}? Join our Discord!");
        PHRASES.put(Phrase.FINISHING_CONVERSION, "Finishing up data conversion. Please wait...");
        PHRASES.put(Phrase.FINISHING_LOGGING, "Finishing up data logging. Please wait...");
        PHRASES.put(Phrase.FIRST_VERSION, "Initial DB: {0}");
        PHRASES.put(Phrase.GLOBAL_LOOKUP, "Don't specify a radius to do a global lookup.");
        PHRASES.put(Phrase.GLOBAL_ROLLBACK, "Use \"{0}\" to do a global {rollback|restore}");
        PHRASES.put(Phrase.HELP_ACTION_1, "Restrict the lookup to a certain action.");
        PHRASES.put(Phrase.HELP_ACTION_2, "Examples: [a:block], [a:+block], [a:-block] [a:click], [a:container], [a:inventory], [a:item], [a:kill], [a:chat], [a:command], [a:sign], [a:session], [a:username]");
        PHRASES.put(Phrase.HELP_COMMAND, "Display more info for that command.");
        PHRASES.put(Phrase.HELP_EXCLUDE_1, "Exclude blocks/users.");
        PHRASES.put(Phrase.HELP_EXCLUDE_2, "Examples: [e:stone], [e:Notch], [e:stone,Notch]");
        PHRASES.put(Phrase.HELP_HEADER, "{0} Help");
        PHRASES.put(Phrase.HELP_INCLUDE_1, "Include specific blocks/entities.");
        PHRASES.put(Phrase.HELP_INCLUDE_2, "Examples: [i:stone], [i:zombie], [i:stone,wood,bedrock]");
        PHRASES.put(Phrase.HELP_INSPECT_1, "With the inspector enabled, you can do the following:");
        PHRASES.put(Phrase.HELP_INSPECT_2, "Left-click a block to see who placed that block.");
        PHRASES.put(Phrase.HELP_INSPECT_3, "Right-click a block to see what adjacent block was broken.");
        PHRASES.put(Phrase.HELP_INSPECT_4, "Place a block to see what block was broken at that location.");
        PHRASES.put(Phrase.HELP_INSPECT_5, "Place a block in liquid (etc) to see who placed it.");
        PHRASES.put(Phrase.HELP_INSPECT_6, "Right-click on a door, chest, etc, to see who last used it.");
        PHRASES.put(Phrase.HELP_INSPECT_7, "Tip: You can use just \"/co i\" for quicker access.");
        PHRASES.put(Phrase.HELP_INSPECT_COMMAND, "Turns the block inspector on or off.");
        PHRASES.put(Phrase.HELP_LIST, "Displays a list of all commands.");
        PHRASES.put(Phrase.HELP_LOOKUP_1, "Command shortcut.");
        PHRASES.put(Phrase.HELP_LOOKUP_2, "Use after inspecting a block to view logs.");
        PHRASES.put(Phrase.HELP_LOOKUP_COMMAND, "Advanced block data lookup.");
        PHRASES.put(Phrase.HELP_NO_INFO, "Information for command \"{0}\" not found.");
        PHRASES.put(Phrase.HELP_PARAMETER, "Please see \"{0}\" for detailed parameter info.");
        PHRASES.put(Phrase.HELP_PARAMS_1, "Perform the {lookup|rollback|restore}.");
        PHRASES.put(Phrase.HELP_PARAMS_2, "Specify the user(s) to {lookup|rollback|restore}.");
        PHRASES.put(Phrase.HELP_PARAMS_3, "Specify the amount of time to {lookup|rollback|restore}.");
        PHRASES.put(Phrase.HELP_PARAMS_4, "Specify a radius area to limit the {lookup|rollback|restore} to.");
        PHRASES.put(Phrase.HELP_PARAMS_5, "Restrict the {lookup|rollback|restore} to a certain action.");
        PHRASES.put(Phrase.HELP_PARAMS_6, "Include specific blocks/entities in the {lookup|rollback|restore}.");
        PHRASES.put(Phrase.HELP_PARAMS_7, "Exclude blocks/users from the {lookup|rollback|restore}.");
        PHRASES.put(Phrase.HELP_PURGE_1, "Delete data older than specified time.");
        PHRASES.put(Phrase.HELP_PURGE_2, "For example, \"{0}\" will delete all data older than one month, and only keep the last 30 days of data.");
        PHRASES.put(Phrase.HELP_PURGE_COMMAND, "Delete old block data.");
        PHRASES.put(Phrase.HELP_RADIUS_1, "Specify a radius area.");
        PHRASES.put(Phrase.HELP_RADIUS_2, "Examples: [r:10] (Only make changes within 10 blocks of you)");
        PHRASES.put(Phrase.HELP_RELOAD_COMMAND, "Reloads the configuration file.");
        PHRASES.put(Phrase.HELP_RESTORE_COMMAND, "Restore block data.");
        PHRASES.put(Phrase.HELP_ROLLBACK_COMMAND, "Rollback block data.");
        PHRASES.put(Phrase.HELP_STATUS, "View the plugin status and version information.");
        PHRASES.put(Phrase.HELP_STATUS_COMMAND, "Displays the plugin status.");
        PHRASES.put(Phrase.HELP_TELEPORT, "Teleport to a location.");
        PHRASES.put(Phrase.HELP_TIME_1, "Specify the amount of time to lookup.");
        PHRASES.put(Phrase.HELP_TIME_2, "Examples: [t:2w,5d,7h,2m,10s], [t:5d2h], [t:2.50h]");
        PHRASES.put(Phrase.HELP_USER_1, "Specify the user(s) to lookup.");
        PHRASES.put(Phrase.HELP_USER_2, "Examples: [u:Notch], [u:Notch,#enderman]");
        PHRASES.put(Phrase.INCOMPATIBLE_ACTION, "\"{0}\" can't be used with that action.");
        PHRASES.put(Phrase.INSPECTOR_ERROR, "Inspector already {enabled|disabled}.");
        PHRASES.put(Phrase.INSPECTOR_TOGGLED, "Inspector now {enabled|disabled}.");
        PHRASES.put(Phrase.INTEGRATION_ERROR, "Unable to {initialize|disable} {0} logging.");
        PHRASES.put(Phrase.INTEGRATION_SUCCESS, "{0} logging successfully {initialized|disabled}.");
        PHRASES.put(Phrase.INTEGRATION_VERSION, "Invalid {0} version found.");
        PHRASES.put(Phrase.INTERACTIONS_HEADER, "Player Interactions");
        PHRASES.put(Phrase.INVALID_ACTION, "That is not a valid action.");
        PHRASES.put(Phrase.INVALID_BRANCH_1, "Invalid plugin version (branch has not been set).");
        PHRASES.put(Phrase.INVALID_BRANCH_2, "To continue, set project branch to \"development\".");
        PHRASES.put(Phrase.INVALID_BRANCH_3, "Running development code may result in data corruption.");
        PHRASES.put(Phrase.INVALID_CONTAINER, "Please inspect a valid container first.");
        PHRASES.put(Phrase.INVALID_DONATION_KEY, "Invalid donation key.");
        PHRASES.put(Phrase.INVALID_INCLUDE, "\"{0}\" is an invalid block/entity name.");
        PHRASES.put(Phrase.INVALID_INCLUDE_COMBO, "That is an invalid block/entity combination.");
        PHRASES.put(Phrase.INVALID_RADIUS, "Please enter a valid radius.");
        PHRASES.put(Phrase.INVALID_SELECTION, "{0} selection not found.");
        PHRASES.put(Phrase.INVALID_USERNAME, "\"{0}\" is an invalid username.");
        PHRASES.put(Phrase.INVALID_WORLD, "Please specify a valid world.");
        PHRASES.put(Phrase.LATEST_VERSION, "Latest Version: {0}");
        PHRASES.put(Phrase.LINK_DISCORD, "Discord: {0}");
        PHRASES.put(Phrase.LINK_DOWNLOAD, "Download: {0}");
        PHRASES.put(Phrase.LINK_PATREON, "Patreon: {0}");
        PHRASES.put(Phrase.LINK_WIKI_BLOCK, "Block Names: {0}");
        PHRASES.put(Phrase.LINK_WIKI_ENTITY, "Entity Names: {0}");
        PHRASES.put(Phrase.LOGGING_ITEMS, "{0} items left to log. Please wait...");
        PHRASES.put(Phrase.LOGGING_TIME_LIMIT, "Logging time limit reached. Discarding data and shutting down.");
        PHRASES.put(Phrase.LOOKUP_BLOCK, "{0} {placed|broke} {1}.");
        PHRASES.put(Phrase.LOOKUP_CONTAINER, "{0} {added|removed} {1} {2}.");
        PHRASES.put(Phrase.LOOKUP_HEADER, "{0} Lookup Results");
        PHRASES.put(Phrase.LOOKUP_INTERACTION, "{0} {clicked|killed} {1}.");
        PHRASES.put(Phrase.LOOKUP_ITEM, "{0} {picked up|dropped} {1} {2}.");
        PHRASES.put(Phrase.LOOKUP_LOGIN, "{0} logged {in|out}.");
        PHRASES.put(Phrase.LOOKUP_PAGE, "Page {0}");
        PHRASES.put(Phrase.LOOKUP_PROJECTILE, "{0} {threw|shot} {1} {2}.");
        PHRASES.put(Phrase.LOOKUP_ROWS_FOUND, "{0} {row|rows} found.");
        PHRASES.put(Phrase.LOOKUP_SEARCHING, "Lookup searching. Please wait...");
        PHRASES.put(Phrase.LOOKUP_STORAGE, "{0} {deposited|withdrew} {1} {2}.");
        PHRASES.put(Phrase.LOOKUP_TIME, "{0} ago");
        PHRASES.put(Phrase.LOOKUP_USERNAME, "{0} logged in as {1}.");
        PHRASES.put(Phrase.MAXIMUM_RADIUS, "The maximum {lookup|rollback|restore} radius is {0}.");
        PHRASES.put(Phrase.MISSING_ACTION_USER, "To use that action, please specify a user.");
        PHRASES.put(Phrase.MISSING_LOOKUP_TIME, "Please specify the amount of time to {lookup|rollback|restore}.");
        PHRASES.put(Phrase.MISSING_LOOKUP_USER, "Please specify a user or {block|radius} to lookup.");
        PHRASES.put(Phrase.MISSING_PARAMETERS, "Please use \"{0}\".");
        PHRASES.put(Phrase.MISSING_ROLLBACK_RADIUS, "You did not specify a {rollback|restore} radius.");
        PHRASES.put(Phrase.MISSING_ROLLBACK_USER, "You did not specify a {rollback|restore} user.");
        PHRASES.put(Phrase.MYSQL_UNAVAILABLE, "Unable to connect to MySQL server.");
        PHRASES.put(Phrase.NETWORK_CONNECTION, "Connection by {0} {successful|failed}. Using {1} {2}.");
        PHRASES.put(Phrase.NETWORK_TEST, "Network test data has been successful sent.");
        PHRASES.put(Phrase.NO_DATA, "No data found at {0}.");
        PHRASES.put(Phrase.NO_DATA_LOCATION, "No {data|transactions|interactions|messages} found at this location.");
        PHRASES.put(Phrase.NO_PERMISSION, "You do not have permission to do that.");
        PHRASES.put(Phrase.NO_RESULTS, "No results found.");
        PHRASES.put(Phrase.NO_RESULTS_PAGE, "No {results|data} found for that page.");
        PHRASES.put(Phrase.NO_ROLLBACK, "No {pending|previous} rollback/restore found.");
        PHRASES.put(Phrase.PATCH_INTERRUPTED, "Upgrade interrupted. Will try again on restart.");
        PHRASES.put(Phrase.PATCH_OUTDATED_1, "Unable to upgrade databases older than {0}.");
        PHRASES.put(Phrase.PATCH_OUTDATED_2, "Please upgrade with a supported version of CoreProtect.");
        PHRASES.put(Phrase.PATCH_PROCESSING, "Processing new data. Please wait...");
        PHRASES.put(Phrase.PATCH_SKIP_UPDATE, "Skipping {table|index} {update|creation|removal} on {0}.");
        PHRASES.put(Phrase.PATCH_STARTED, "Performing {0} upgrade. Please wait...");
        PHRASES.put(Phrase.PATCH_SUCCESS, "Successfully upgraded to {0}.");
        PHRASES.put(Phrase.PATCH_UPGRADING, "Database upgrade in progress. Please wait...");
        PHRASES.put(Phrase.PLEASE_SELECT, "Please select: \"{0}\" or \"{1}\".");
        PHRASES.put(Phrase.PREVIEW_CANCELLED, "Preview cancelled.");
        PHRASES.put(Phrase.PREVIEW_CANCELLING, "Cancelling preview...");
        PHRASES.put(Phrase.PREVIEW_IN_GAME, "You can only preview rollbacks in-game.");
        PHRASES.put(Phrase.PREVIEW_TRANSACTION, "You can't preview {container|inventory} transactions.");
        PHRASES.put(Phrase.PRIMARY_THREAD_ERROR, "That API method can't be used on the primary thread.");
        PHRASES.put(Phrase.PURGE_ABORTED, "Purge failed. Database may be corrupt.");
        PHRASES.put(Phrase.PURGE_ERROR, "Unable to process {0} data!");
        PHRASES.put(Phrase.PURGE_FAILED, "Purge failed. Please try again later.");
        PHRASES.put(Phrase.PURGE_IN_PROGRESS, "Purge in progress. Please try again later.");
        PHRASES.put(Phrase.PURGE_MINIMUM_TIME, "You can only purge data older than {0} {days|hours}.");
        PHRASES.put(Phrase.PURGE_NOTICE_1, "Please note that this may take some time.");
        PHRASES.put(Phrase.PURGE_NOTICE_2, "Do not restart your server until completed.");
        PHRASES.put(Phrase.PURGE_OPTIMIZING, "Optimizing database. Please wait...");
        PHRASES.put(Phrase.PURGE_PROCESSING, "Processing {0} data...");
        PHRASES.put(Phrase.PURGE_REPAIRING, "Attempting to repair. This may take some time...");
        PHRASES.put(Phrase.PURGE_ROWS, "{0} {row|rows} of data deleted.");
        PHRASES.put(Phrase.PURGE_STARTED, "Data purge started on \"{0}\".");
        PHRASES.put(Phrase.PURGE_SUCCESS, "Data purge successful.");
        PHRASES.put(Phrase.RAM_STATS, "{0}GB / {1}GB RAM");
        PHRASES.put(Phrase.RELOAD_STARTED, "Reloading configuration - please wait.");
        PHRASES.put(Phrase.RELOAD_SUCCESS, "Configuration successfully reloaded.");
        PHRASES.put(Phrase.ROLLBACK_ABORTED, "Rollback or restore aborted.");
        PHRASES.put(Phrase.ROLLBACK_CHUNKS_FOUND, "Found {0} {chunk|chunks} to modify.");
        PHRASES.put(Phrase.ROLLBACK_CHUNKS_MODIFIED, "Modified {0}/{1} {chunk|chunks}.");
        PHRASES.put(Phrase.ROLLBACK_COMPLETED, "{Rollback|Restore|Preview} completed for \"{0}\".");
        PHRASES.put(Phrase.ROLLBACK_EXCLUDED_USERS, "Excluded {user|users}: \"{0}\".");
        PHRASES.put(Phrase.ROLLBACK_INCLUDE, "{Included|Excluded} {block|entity|target} {type|types}: \"{0}\".");
        PHRASES.put(Phrase.ROLLBACK_IN_PROGRESS, "A rollback/restore is already in progress.");
        PHRASES.put(Phrase.ROLLBACK_LENGTH, "Time taken: {0} {second|seconds}.");
        PHRASES.put(Phrase.ROLLBACK_MODIFIED, "{Modified|Modifying} {0}.");
        PHRASES.put(Phrase.ROLLBACK_RADIUS, "Radius: {0} {block|blocks}.");
        PHRASES.put(Phrase.ROLLBACK_SELECTION, "Radius set to \"{0}\".");
        PHRASES.put(Phrase.ROLLBACK_STARTED, "{Rollback|Restore|Preview} started on \"{0}\".");
        PHRASES.put(Phrase.ROLLBACK_TIME, "Time range: {0}.");
        PHRASES.put(Phrase.ROLLBACK_WORLD_ACTION, "Restricted to {world|action} \"{0}\".");
        PHRASES.put(Phrase.SIGN_HEADER, "Sign Messages");
        PHRASES.put(Phrase.STATUS_CONSUMER, "Consumer: {0} {item|items} in queue.");
        PHRASES.put(Phrase.STATUS_DATABASE, "Database: Using {0}.");
        PHRASES.put(Phrase.STATUS_INTEGRATION, "{0}: Integration {enabled|disabled}.");
        PHRASES.put(Phrase.STATUS_LICENSE, "License: {0}");
        PHRASES.put(Phrase.STATUS_SYSTEM, "System: {0}");
        PHRASES.put(Phrase.STATUS_VERSION, "Version: {0}");
        PHRASES.put(Phrase.TELEPORTED, "Teleported to {0}.");
        PHRASES.put(Phrase.TELEPORTED_SAFETY, "Teleported you to safety.");
        PHRASES.put(Phrase.TELEPORT_PLAYERS, "Teleport command can only be used by players.");
        PHRASES.put(Phrase.TIME_DAYS, "{0} {day|days}");
        PHRASES.put(Phrase.TIME_HOURS, "{0} {hour|hours}");
        PHRASES.put(Phrase.TIME_MINUTES, "{0} {minute|minutes}");
        PHRASES.put(Phrase.TIME_MONTHS, "{0} {month|months}");
        PHRASES.put(Phrase.TIME_SECONDS, "{0} {second|seconds}");
        PHRASES.put(Phrase.TIME_UNITS, "{/m|/h|/d}");
        PHRASES.put(Phrase.TIME_WEEKS, "{0} {week|weeks}");
        PHRASES.put(Phrase.TIME_YEARS, "{0} {year|years}");
        PHRASES.put(Phrase.UPDATE_ERROR, "An error occurred while checking for updates.");
        PHRASES.put(Phrase.UPDATE_HEADER, "{0} Update");
        PHRASES.put(Phrase.UPDATE_NOTICE, "Notice: {0} is now available.");
        PHRASES.put(Phrase.UPGRADE_IN_PROGRESS, "Upgrade in progress. Please try again later.");
        PHRASES.put(Phrase.USER_NOT_FOUND, "User \"{0}\" not found.");
        PHRASES.put(Phrase.USER_OFFLINE, "The user \"{0}\" is not online.");
        PHRASES.put(Phrase.USING_MYSQL, "Using MySQL for data storage.");
        PHRASES.put(Phrase.USING_SQLITE, "Using SQLite for data storage.");
        PHRASES.put(Phrase.VALID_DONATION_KEY, "Valid donation key.");
        PHRASES.put(Phrase.VERSION_NOTICE, "Version {0} is now available.");
        PHRASES.put(Phrase.VERSION_INCOMPATIBLE, "{0} {1} is not supported.");
        PHRASES.put(Phrase.VERSION_REQUIRED, "{0} {1} or higher is required.");
        PHRASES.put(Phrase.WORLD_NOT_FOUND, "World \"{0}\" not found.");

        USER_PHRASES.putAll(PHRASES);
        TRANSLATED_PHRASES.putAll(PHRASES);
    }

}
