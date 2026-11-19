package net.coreprotect.listener.entity;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Turtle;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.WorldUtils;

public final class EntityChangeBlockListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Entity entity = event.getEntity();
        Block block = event.getBlock();
        World world = block.getWorld();
        Material type = block.getType();
        Material newType = event.getTo();
        Config config = Config.getConfig(world);
        if (entity instanceof Player && BukkitAdapter.ADAPTER.isSuspiciousBlock(type)) {
            logBrushCompletion((Player) entity, block, block.getState(), newType, config);
            return;
        }
        if (type == Material.FARMLAND && newType == Material.DIRT && (entity instanceof Player || config.ENTITY_CHANGE)) {
            logTrample(EntityUtils.getBreakUser(entity, "#entity"), block, newType, config);
            return;
        }
        if (!config.ENTITY_CHANGE) {
            return;
        }

        if (type == newType && (type == Material.REDSTONE_ORE || type.name().equals("DEEPSLATE_REDSTONE_ORE") || type.name().equals("BIG_DRIPLEAF"))) {
            return;
        }

        if (entity instanceof FallingBlock) {
            if (!config.BLOCK_MOVEMENT) {
                CacheHandler.fallingBlockSpawnCache.remove(entity.getUniqueId().toString());
                return;
            }
            handleFallingBlock(event, (FallingBlock) entity, block, newType, type, config);
            return;
        }

        String user = getBlockChangeActor(entity, type);
        if (user == null) {
            return;
        }
        BlockState before = block.getState();
        if (entity instanceof Projectile && BukkitAdapter.ADAPTER.isDecoratedPot(type) && type != newType && config.ITEM_TRANSACTIONS) {
            Database.containerBreakCheck(user, type, block, null, before.getLocation());
        }
        if (BlockUtils.isAir(newType)) {
            if (config.BLOCK_BREAK) {
                Queue.queueBlockBreak(user, before, type, block.getBlockData().getAsString(), 0);
            }
        }
        else if (config.BLOCK_PLACE) {
            Queue.queueBlockPlace(user, before, type, before, newType, -1, 0, event.getBlockData().getAsString());
        }
    }

    private static String getBlockChangeActor(Entity entity, Material type) {
        if (entity instanceof Enderman) {
            return "#enderman";
        }
        if (entity instanceof EnderDragon) {
            return "#enderdragon";
        }
        if (entity instanceof Fox) {
            return "#fox";
        }
        if (entity instanceof Wither) {
            return "#wither";
        }
        if (entity instanceof Turtle) {
            return "#turtle";
        }
        if (entity instanceof Ravager) {
            return "#ravager";
        }
        if (entity instanceof Zombie) {
            return "#zombie";
        }
        if (entity instanceof Silverfish) {
            return "#silverfish";
        }
        if (entity instanceof Projectile || entity instanceof Sheep || entity instanceof Rabbit || entity instanceof Villager
                || (entity instanceof LivingEntity && type.name().equals("POWDER_SNOW"))) {
            return EntityUtils.getBreakUser(entity, "#entity");
        }
        return null;
    }

    private static void logTrample(String user, Block block, Material expected, Config config) {
        if (!config.BLOCK_BREAK && !config.BLOCK_PLACE) {
            return;
        }
        BlockState before = block.getState();
        Block above = block.getY() + 1 < block.getWorld().getMaxHeight() ? block.getRelative(BlockFace.UP) : null;
        BlockState crop = above != null && !BlockUtils.isAir(above.getType()) ? above.getState() : null;
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            BlockState after = block.getState();
            if (after.getType() == expected) {
                if (config.BLOCK_BREAK) {
                    if (crop != null && BlockUtils.isAir(above.getType())) {
                        Queue.queueBlockBreak(user, crop, crop.getType(), crop.getBlockData().getAsString(), 0);
                    }
                    Queue.queueBlockBreak(user, before, before.getType(), before.getBlockData().getAsString(), 0);
                }
                if (config.BLOCK_PLACE && !BlockUtils.isAir(expected)) {
                    Queue.queueBlockPlace(user, after, expected, null, null, -1, 0, after.getBlockData().getAsString());
                }
            }
        }, before.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onBrushItemDrop(BlockDropItemEvent event) {
        BlockState before = event.getBlockState();
        if (ConfigHandler.isPaper || !BukkitAdapter.ADAPTER.isSuspiciousBlock(before.getType())) {
            return;
        }
        Material expected = before.getType().name().equals("SUSPICIOUS_SAND") ? Material.SAND : Material.GRAVEL;
        logBrushCompletion(event.getPlayer(), event.getBlock(), before, expected, Config.getConfig(before.getWorld()));
    }

    private static void logBrushCompletion(Player player, Block block, BlockState before, Material expected, Config config) {
        if (!config.BLOCK_PLACE || (expected != Material.SAND && expected != Material.GRAVEL)) {
            return;
        }
        String user = player.getName();
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            BlockState after = block.getState();
            if (after.getType() == expected) {
                Queue.queueBlockPlace(user, before, before.getType(), before, expected, -1, 0, after.getBlockData().getAsString());
            }
        }, before.getLocation());
    }

    private static void handleFallingBlock(EntityChangeBlockEvent event, FallingBlock fallingBlock, Block block, Material newType, Material type, Config config) {
        if (BlockUtils.isAir(newType)) {
            if (!config.BLOCK_BREAK) {
                return;
            }

            int worldId = WorldUtils.getWorldId(block.getWorld().getName());
            String coordKey = block.getX() + "." + block.getY() + "." + block.getZ() + "." + worldId;
            String user = lookupCachedUser(coordKey, type);
            if (user == null) {
                if (isTrackedFallingAttachment(type)) {
                    return;
                }
                user = "#gravity";
            }

            Queue.queueBlockBreak(user, block.getState(), type, block.getBlockData().getAsString(), 0);
            return;
        }

        Object[] originData = CacheHandler.fallingBlockSpawnCache.remove(fallingBlock.getUniqueId().toString());
        if (!config.BLOCK_PLACE) {
            return;
        }

        String user = "#gravity";
        if (originData != null) {
            String originKey = (String) originData[1];
            BlockData source = fallingBlock.getBlockData();
            String lookupUser = source == null ? null : lookupCachedUser(originKey, source.getMaterial());
            if (lookupUser != null) {
                user = lookupUser;
            }
        }

        BlockState before = block.getState();
        Queue.queueBlockPlace(user, before, type, before, newType, -1, 0, event.getBlockData().getAsString());
    }

    private static String lookupCachedUser(String coordKey, Material expectedType) {
        Object[] data = CacheHandler.lookupCache.get(coordKey);
        if (data == null) {
            return null;
        }
        if (expectedType != null && data[2] instanceof Material && !data[2].equals(expectedType)) {
            return null;
        }
        return (String) data[1];
    }

    private static boolean isTrackedFallingAttachment(Material type) {
        String name = type.name();
        return name.equals("POINTED_DRIPSTONE") || name.equals("SULFUR_SPIKE");
    }

}
