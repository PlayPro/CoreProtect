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
import net.coreprotect.utility.LookupThrottle;
import net.coreprotect.database.Database;
import net.coreprotect.database.DuckDBLookupQuery;
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
            String resolvedCommand = InspectorLookup.resolveCommand(command, commandSender);
            long currentTime = System.currentTimeMillis() / 1000L;
            String where = "entity_spawn_rowid=" + entitySpawnRowId;

            EntitySpawnRecord record = EntitySpawnStatement.loadLocationRecords(statement.getConnection(), Collections.singleton(entitySpawnRowId)).get(entitySpawnRowId);
            DisplayLocation displayLocation = resolveDisplayLocation(record, liveLocation);
            boolean found = false;
            String spatialTable = ConfigHandler.databaseType.isDuckDB()
                    ? DuckDBLookupQuery.entityTable(statement.getConnection(), "entity_interaction", Collections.singleton(entitySpawnRowId), "spatial_rows") : null;
            String columns = "time," + ConfigHandler.databaseType.getUserColumn() + ",wid,x,y,z,type,action,rolled_back";
            InspectorLookup.Page pageQuery = InspectorLookup.queryPage(statement, ConfigHandler.prefix + "entity_interaction", spatialTable, "", where, columns,
                    "time DESC,rowid DESC", true, page, limit);
            long count = pageQuery.count;

            try (ResultSet results = statement.executeQuery(pageQuery.query)) {
                while (results.next()) {
                    if (pageQuery.combinedCount) {
                        count = results.getLong("count");
                        if (results.getObject("result_id") == null) {
                            continue;
                        }
                    }
                    int userId = results.getInt("user");
                    String resultUser = UserStatement.getName(statement.getConnection(), userId);

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
                int totalPages = InspectorLookup.totalPages(count, limit);
                result.add(Color.WHITE + "-----");
                result.add(ChatUtils.getPageNavigation(resolvedCommand, page, totalPages));
            }
            else if (!found) {
                result.add(pageQuery.end > count && count > 0 ? Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.SECOND) : noData());
            }

            String name = commandSender.getName();
            String history = displayLocation == null ? "0.0.0.0.9." + limit : displayLocation.x + "." + displayLocation.y + "." + displayLocation.z + "." + displayLocation.worldId + ".9." + limit;
            LookupThrottle.publish(name, () -> {
                ConfigHandler.lookupEntityInteraction.put(name, entitySpawnRowId);
                ConfigHandler.lookupType.put(name, 9);
                ConfigHandler.lookupPage.put(name, page);
                ConfigHandler.lookupCommand.put(name, history);
            });
        }
        catch (Exception e) {
            Database.reportDatabaseFailure(e);
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

    private static String noData() {
        return Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.THIRD);
    }

    private static void clearLookup(CommandSender commandSender) {
        String name = commandSender.getName();
        LookupThrottle.publish(name, () -> {
            ConfigHandler.lookupEntityInteraction.remove(name);
            ConfigHandler.lookupType.remove(name);
            ConfigHandler.lookupCommand.remove(name);
        });
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
