package net.coreprotect.listener.entity;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Turtle;
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
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.WorldUtils;

public final class EntityChangeBlockListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onEntityChangeBlock(EntityChangeBlockEvent event) {
        World world = event.getBlock().getWorld();
        if (event.isCancelled()) {
            return;
        }

        Config config = Config.getConfig(world);
        if (event.getEntity() instanceof Player && BukkitAdapter.ADAPTER.isSuspiciousBlock(event.getBlock().getType())) {
            logBrushCompletion((Player) event.getEntity(), event.getBlock(), event.getBlock().getState(), event.getTo(), config);
            return;
        }
        if (event.getEntity() instanceof Player && event.getBlock().getType() == Material.FARMLAND && event.getTo() != Material.FARMLAND) {
            logPlayerTrample((Player) event.getEntity(), event.getBlock(), event.getTo(), config);
            return;
        }
        if (!config.ENTITY_CHANGE) {
            return;
        }

        Entity entity = event.getEntity();
        Block block = event.getBlock();
        Material newtype = event.getTo();
        Material type = event.getBlock().getType();

        if (type == newtype && (type == Material.REDSTONE_ORE || type.name().equals("DEEPSLATE_REDSTONE_ORE") || type.name().equals("BIG_DRIPLEAF"))) {
            return;
        }

        if (entity instanceof FallingBlock) {
            if (!config.BLOCK_MOVEMENT) {
                CacheHandler.fallingBlockSpawnCache.remove(entity.getUniqueId().toString());
                return;
            }
            handleFallingBlock(event, (FallingBlock) entity, block, newtype, type, world);
            return;
        }

        String e = "";
        if (entity instanceof Enderman) {
            e = "#enderman";
        }
        else if (entity instanceof EnderDragon) {
            e = "#enderdragon";
        }
        else if (entity instanceof Fox) {
            e = "#fox";
        }
        else if (entity instanceof Wither) {
            e = "#wither";
        }
        else if (entity instanceof Turtle) {
            e = "#turtle";
        }
        else if (entity instanceof Ravager) {
            e = "#ravager";
        }
        else if (entity instanceof Zombie) {
            e = "#zombie";
        }
        else if (entity instanceof Silverfish) {
            e = "#silverfish";
        }
        else if (entity.getType().name().equals("WIND_CHARGE")) {
            e = "#windcharge";
        }
        else if (entity.getType().name().equals("BREEZE_WIND_CHARGE")) {
            e = "#breezewindcharge";
        }
        if (e.length() > 0) {
            if (newtype.equals(Material.AIR) || newtype.equals(Material.CAVE_AIR)) {
                Queue.queueBlockBreak(e, block.getState(), type, block.getBlockData().getAsString(), 0);
            }
            else {
                queueBlockPlace(e, block.getState(), type, block.getState(), newtype, -1, 0, event.getBlockData().getAsString());
            }
        }
    }

    private static void logPlayerTrample(Player player, Block block, Material expected, Config config) {
        if (!config.BLOCK_BREAK && !config.BLOCK_PLACE) {
            return;
        }
        BlockState before = block.getState();
        Block above = block.getY() + 1 < block.getWorld().getMaxHeight() ? block.getRelative(BlockFace.UP) : null;
        BlockState crop = above != null && !BlockUtils.isAir(above.getType()) ? above.getState() : null;
        String user = player.getName();
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

    private void handleFallingBlock(EntityChangeBlockEvent event, FallingBlock fallingBlock, Block block, Material newtype, Material type, World world) {
        int worldId = WorldUtils.getWorldId(world.getName());

        if (newtype.equals(Material.AIR) || newtype.equals(Material.CAVE_AIR)) {
            if (!Config.getConfig(world).BLOCK_BREAK) {
                return;
            }

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

        if (!Config.getConfig(world).BLOCK_PLACE) {
            return;
        }

        Object[] originData = CacheHandler.fallingBlockSpawnCache.remove(fallingBlock.getUniqueId().toString());
        String user = "#gravity";
        if (originData != null) {
            String originKey = (String) originData[1];
            String lookupUser = lookupCachedUser(originKey, newtype);
            if (lookupUser != null) {
                user = lookupUser;
            }
        }

        queueBlockPlace(user, block.getState(), type, block.getState(), newtype, -1, 0, event.getBlockData().getAsString());
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
