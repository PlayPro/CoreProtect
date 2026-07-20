package net.coreprotect.database.rollback;

import java.util.Arrays;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.EntityInteractionListener;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.ErrorReporter;

public class RollbackEntityHandler {

    /**
     * Processes an entity-related rollback operation.
     *
     * @param bukkitWorld
     *            The world the entity change belongs to
     * @param rowTypeRaw
     *            The raw type value
     * @param rowData
     *            The data value
     * @param rowAction
     *            The action value
     * @param rowRolledBack
     *            Whether the entity was already rolled back
     * @param rowX
     *            The X coordinate
     * @param rowY
     *            The Y coordinate
     * @param rowZ
     *            The Z coordinate
     * @param rowWorldId
     *            The world ID
     * @param rowUser
     *            The username associated with this entity change
     * @return The number of entities affected (1 if successful, 0 otherwise)
     */
    public static int processEntity(World bukkitWorld, int oldTypeRaw, int rowTypeRaw, int rowData, int rowAction, int rowRolledBack, int rowX, int rowY, int rowZ, int rowWorldId, String rowUser) {
        try {
            // Entity kill
            if (rowAction == LookupActions.ENTITY_KILL) {
                int entityId = -1;
                EntityType targetType = null;
                if (rowTypeRaw <= 0 && rowRolledBack == 1) {
                    entityId = getCachedEntityId(rowX, rowY, rowZ, rowWorldId, oldTypeRaw);
                    UUID entityUuid = getCachedEntityUuid(rowX, rowY, rowZ, rowWorldId, oldTypeRaw);
                    targetType = EntityUtils.getEntityType(oldTypeRaw);
                    if (removeCachedEntity(entityUuid, targetType)) {
                        return 1;
                    }
                }

                Block block = bukkitWorld.getBlockAt(rowX, rowY, rowZ);
                if (!bukkitWorld.isChunkLoaded(block.getChunk())) {
                    bukkitWorld.getChunkAt(block.getLocation());
                }

                if (rowTypeRaw > 0) {
                    // Spawn in entity
                    if (rowRolledBack == 0) {
                        EntityType entityType = EntityUtils.getEntityType(rowTypeRaw);
                        // Use the spawnEntity method from the RollbackUtil class instead of Queue
                        spawnEntity(rowUser, block.getState(), entityType, rowData);
                        return 1;
                    }
                }
                else if (rowTypeRaw <= 0) {
                    // Attempt to remove entity
                    if (rowRolledBack == 1) {
                        int xmin = rowX - 5;
                        int xmax = rowX + 5;
                        int ymin = rowY - 1;
                        int ymax = rowY + 1;
                        int zmin = rowZ - 5;
                        int zmax = rowZ + 5;

                        boolean removed = removeMatchingEntity(Arrays.asList(block.getChunk().getEntities()), entityId, targetType, xmin, xmax, ymin, ymax, zmin, zmax, true);
                        if (!removed && entityId > -1 && !ConfigHandler.isFolia) {
                            // On non-Folia, keep world-wide fallback for moved entities.
                            removed = removeMatchingEntity(block.getWorld().getEntities(), entityId, targetType, xmin, xmax, ymin, ymax, zmin, zmax, false);
                        }

                        if (removed) {
                            return 1;
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return 0;
    }

    private static int getCachedEntityId(int rowX, int rowY, int rowZ, int rowWorldId, int oldTypeRaw) {
        Object[] cachedEntity = getCachedEntity(rowX, rowY, rowZ, rowWorldId, oldTypeRaw);
        if (cachedEntity != null && cachedEntity.length > 1 && cachedEntity[1] instanceof Integer) {
            return (Integer) cachedEntity[1];
        }
        return -1;
    }

    private static UUID getCachedEntityUuid(int rowX, int rowY, int rowZ, int rowWorldId, int oldTypeRaw) {
        Object[] cachedEntity = getCachedEntity(rowX, rowY, rowZ, rowWorldId, oldTypeRaw);
        if (cachedEntity != null && cachedEntity.length > 2 && cachedEntity[2] instanceof UUID) {
            return (UUID) cachedEntity[2];
        }
        return null;
    }

    private static Object[] getCachedEntity(int rowX, int rowY, int rowZ, int rowWorldId, int oldTypeRaw) {
        String entityName = EntityUtils.getEntityType(oldTypeRaw).name();
        String token = rowX + "." + rowY + "." + rowZ + "." + rowWorldId + "." + entityName;
        return CacheHandler.entityCache.get(token);
    }

    private static boolean removeCachedEntity(UUID uuid, EntityType targetType) {
        if (uuid == null) {
            return false;
        }

        Entity entity = Bukkit.getEntity(uuid);
        if (entity == null || !entity.getType().equals(targetType)) {
            return false;
        }
        if (ConfigHandler.isFolia && !PaperAdapter.ADAPTER.isOwnedByCurrentRegion(entity)) {
            return PaperAdapter.ADAPTER.executeEntityTask(CoreProtect.getInstance(), entity, () -> removeEntity(entity), null);
        }

        removeEntity(entity);
        return true;
    }

    private static boolean removeMatchingEntity(Iterable<? extends Entity> entities, int cachedEntityId, EntityType targetType, int xmin, int xmax, int ymin, int ymax, int zmin, int zmax, boolean useBounds) {
        for (Entity entity : entities) {
            if (cachedEntityId > -1) {
                if (entity.getEntityId() == cachedEntityId && entity.getType().equals(targetType)) {
                    removeEntity(entity);
                    return true;
                }
                continue;
            }

            if (!entity.getType().equals(targetType)) {
                continue;
            }
            if (useBounds && !isWithinBounds(entity.getLocation(), xmin, xmax, ymin, ymax, zmin, zmax)) {
                continue;
            }

            removeEntity(entity);
            return true;
        }
        return false;
    }

    private static void removeEntity(Entity entity) {
        if (EntitySpawnTracking.isTrackedOrPendingIdentity(entity)) {
            EntityInteractionListener.flushPendingInteractions(entity);
            Queue.queueEntitySpawnRemoved(entity);
            EntitySpawnTracking.clearTracking(entity.getUniqueId());
            EntitySpawnTracking.removeWithoutRemovalLog(entity);
        }
        else {
            entity.remove();
        }
    }

    private static boolean isWithinBounds(Location location, int xmin, int xmax, int ymin, int ymax, int zmin, int zmax) {
        int entityX = location.getBlockX();
        int entityY = location.getBlockY();
        int entityZ = location.getBlockZ();
        return entityX >= xmin && entityX <= xmax && entityY >= ymin && entityY <= ymax && entityZ >= zmin && entityZ <= zmax;
    }

    /**
     * Spawns an entity at the given block location.
     * 
     * @param user
     *            The username of the player
     * @param block
     *            The block state where the entity should be spawned
     * @param type
     *            The type of entity to spawn
     * @param data
     *            Additional data for the entity
     */
    public static void spawnEntity(String user, BlockState block, EntityType type, int data) {
        // Create a new helper method that will delegate to Queue
        RollbackUtil.queueEntitySpawn(user, block, type, data);
    }
}
