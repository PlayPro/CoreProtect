package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.model.entity.EntityInteractionAction;
import net.coreprotect.model.entity.EntitySpawnRecord;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.WorldUtils;

public final class EntityInteractionLookup {

    private EntityInteractionLookup() {
        throw new IllegalStateException("Lookup class");
    }

    public static List<String> performLookup(String command, Statement statement, CommandSender commandSender, int page, int limit, Integer entitySpawnRowId) {
        return performLookup(command, statement, commandSender, page, limit, entitySpawnRowId, null);
    }

    public static List<String> performLookup(String command, Statement statement, CommandSender commandSender, int page, int limit, Integer entitySpawnRowId, Location liveLocation) {
        List<String> result = new ArrayList<>();
        if (entitySpawnRowId == null) {
            clearLookup(commandSender);
            result.add(noData());
            return result;
        }

        try {
            String resolvedCommand = resolveCommand(command, commandSender);
            int currentTime = (int) (System.currentTimeMillis() / 1000L);
            int rowMax = page * limit;
            int pageStart = rowMax - limit;
            int count;
            String where = "entity_spawn_rowid='" + entitySpawnRowId + "'";

            try (ResultSet results = statement.executeQuery("SELECT count() AS count FROM " + ConfigHandler.prefix + "entity_interaction FINAL WHERE " + where)) {
                count = results.next() ? results.getInt("count") : 0;
            }

            EntitySpawnRecord record = EntitySpawnStatement.loadLocationRecords(statement.getConnection(), Collections.singleton(entitySpawnRowId)).get(entitySpawnRowId);
            DisplayLocation displayLocation = resolveDisplayLocation(record, liveLocation);
            boolean found = false;
            String query = "SELECT time,user,wid,x,y,z,type,action,rolled_back FROM " + ConfigHandler.prefix + "entity_interaction FINAL WHERE " + where + " ORDER BY time DESC,rowid DESC LIMIT " + limit + " OFFSET " + pageStart + " SETTINGS output_format_json_quote_64bit_integers=0";
            try (ResultSet results = statement.executeQuery(query)) {
                while (results.next()) {
                    int userId = results.getInt("user");
                    String resultUser = ConfigHandler.playerIdCacheReversed.get(userId);
                    if (resultUser == null) {
                        resultUser = UserStatement.loadName(statement.getConnection(), userId);
                    }

                    int originWorldId = results.getInt("wid");
                    int originX = results.getInt("x");
                    int originY = results.getInt("y");
                    int originZ = results.getInt("z");
                    int displayWorldId = displayLocation == null ? originWorldId : displayLocation.worldId;
                    int displayX = displayLocation == null ? originX : displayLocation.x;
                    int displayY = displayLocation == null ? originY : displayLocation.y;
                    int displayZ = displayLocation == null ? originZ : displayLocation.z;
                    if (!found) {
                        result.add(Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.INTERACTIONS_HEADER) + Color.WHITE + " ----- " + ChatUtils.getCoordinates(resolvedCommand, displayWorldId, displayX, displayY, displayZ, false, false));
                    }
                    found = true;

                    int actionId = results.getInt("action");
                    int typeId = results.getInt("type");
                    int rolledBack = results.getInt("rolled_back");
                    long resultTime = results.getLong("time");
                    String rollbackFormat = rolledBack == 1 || rolledBack == 3 ? Color.STRIKETHROUGH : "";
                    String target = entityName(typeId);
                    String coordinateInfo = displayWorldId == originWorldId && displayX == originX && displayY == originY && displayZ == originZ ? "" : ChatUtils.getCoordinateTooltip(originWorldId, originX, originY, originZ, Phrase.build(Phrase.LOOKUP_ENTITY_INTERACTION_ORIGIN), true);
                    String selector = actionSelector(actionId);
                    String timeAgo = ChatUtils.getTimeSince(resultTime, currentTime, true);
                    result.add(timeAgo + " " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_ENTITY_INTERACTION, Color.DARK_AQUA + rollbackFormat + resultUser + Color.WHITE + rollbackFormat, Color.DARK_AQUA + rollbackFormat + target + Color.WHITE + coordinateInfo, selector));
                    PluginChannelListener.getInstance().sendData(commandSender, resultTime, Phrase.LOOKUP_ENTITY_INTERACTION, selector, resultUser, target, -1, displayX, displayY, displayZ, displayWorldId, rollbackFormat, false, false);
                }
            }

            if (found && count > limit) {
                int totalPages = (int) Math.ceil(count / (limit + 0.0));
                result.add(Color.WHITE + "-----");
                result.add(ChatUtils.getPageNavigation(resolvedCommand, page, totalPages));
            }
            else if (!found) {
                result.add(rowMax > count && count > 0 ? Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.SECOND) : noData());
            }

            ConfigHandler.lookupEntityInteraction.put(commandSender.getName(), entitySpawnRowId);
            ConfigHandler.lookupType.put(commandSender.getName(), 9);
            ConfigHandler.lookupPage.put(commandSender.getName(), page);
            ConfigHandler.lookupCommand.put(commandSender.getName(), displayLocation == null ? "0.0.0.0.9." + limit : displayLocation.x + "." + displayLocation.y + "." + displayLocation.z + "." + displayLocation.worldId + ".9." + limit);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        return result;
    }

    public static String actionSelector(int actionId) {
        switch (EntityInteractionAction.fromId(actionId)) {
            case SHEAR:
                return Selector.SECOND;
            case LEASH:
                return Selector.THIRD;
            case UNLEASH:
                return Selector.FOURTH;
            case GENERIC:
            default:
                return Selector.FIRST;
        }
    }

    public static String entityName(int typeId) {
        EntityType type = EntityUtils.getEntityType(typeId);
        return type == null ? "unknown" : type.name().toLowerCase(Locale.ROOT);
    }

    private static DisplayLocation resolveDisplayLocation(EntitySpawnRecord record, Location liveLocation) throws Exception {
        if (liveLocation != null && liveLocation.getWorld() != null) {
            return new DisplayLocation(WorldUtils.getWorldId(liveLocation.getWorld().getName()), liveLocation.getBlockX(), liveLocation.getBlockY(), liveLocation.getBlockZ());
        }
        if (record == null) {
            return null;
        }

        int worldId = record.getWorldId();
        int x = (int) Math.floor(record.getX());
        int y = (int) Math.floor(record.getY());
        int z = (int) Math.floor(record.getZ());
        Map<UUID, Location> loadedLocations = EntitySpawnTracking.findLoadedLocations(Collections.singleton(record.getUuid()));
        Location loaded = loadedLocations.get(record.getUuid());
        if (loaded != null && loaded.getWorld() != null) {
            worldId = WorldUtils.getWorldId(loaded.getWorld().getName());
            x = loaded.getBlockX();
            y = loaded.getBlockY();
            z = loaded.getBlockZ();
        }
        return new DisplayLocation(worldId, x, y, z);
    }

    private static String resolveCommand(String command, CommandSender commandSender) {
        if (command != null) {
            return command;
        }
        if (commandSender.hasPermission("coreprotect.co")) {
            return "co";
        }
        if (commandSender.hasPermission("coreprotect.core")) {
            return "core";
        }
        return commandSender.hasPermission("coreprotect.coreprotect") ? "coreprotect" : "co";
    }

    private static String noData() {
        return Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.THIRD);
    }

    private static void clearLookup(CommandSender commandSender) {
        ConfigHandler.lookupEntityInteraction.remove(commandSender.getName());
        ConfigHandler.lookupType.remove(commandSender.getName());
        ConfigHandler.lookupCommand.remove(commandSender.getName());
    }

    private static final class DisplayLocation {
        private final int worldId;
        private final int x;
        private final int y;
        private final int z;

        private DisplayLocation(int worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
