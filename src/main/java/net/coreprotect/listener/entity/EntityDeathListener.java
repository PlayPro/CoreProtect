package net.coreprotect.listener.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.coreprotect.utility.EntityUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.EntityInteractionListener;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.EntitySpawnTracking;

public final class EntityDeathListener extends Queue implements Listener {

    private static final int ENTITY_KILL_DUPLICATE_THRESHOLD = 256;
    private static final int ENTITY_KILL_DUPLICATE_WINDOW_SECONDS = 900;

    public static void parseEntityKills(String message) {
        message = message.trim().toLowerCase(Locale.ROOT);
        if (!message.contains(" ")) {
            return;
        }

        String[] args = message.split(" ");
        if (args.length < 2 || !args[0].replaceFirst("/", "").equals("kill") || !args[1].startsWith("@e")) {
            return;
        }

        List<LivingEntity> entityList = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            List<LivingEntity> livingEntities = world.getLivingEntities();
            for (LivingEntity entity : livingEntities) {
                if (entity instanceof Player) {
                    continue;
                }

                if (entity.isValid()) {
                    entityList.add(entity);
                }
            }
        }

        for (LivingEntity entity : entityList) {
            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                if (entity != null && entity.isDead()) {
                    logEntityDeath(entity, "#command");
                }
            }, entity);
        }
    }

    protected static void logEntityDeath(LivingEntity entity, String e) {
        if (!Config.getConfig(entity.getWorld()).ENTITY_KILLS) {
            return;
        }

        EntityDamageEvent damage = entity.getLastDamageCause();
        if (damage == null && e == null) {
            return;
        }

        EntityDamageEvent.DamageCause cause = damage == null ? null : damage.getCause();
        boolean isCommand = (cause == DamageCause.VOID && entity.getLocation().getBlockY() >= BukkitAdapter.ADAPTER.getMinHeight(entity.getWorld()));
        if (e == null) {
            e = isCommand ? "#command" : "";
        }

        if (entity.getType() == EntityType.GLOW_SQUID && cause == DamageCause.DROWNING) {
            return;
        }

        if (Config.getConfig(entity.getWorld()).SKIP_GENERIC_DATA) {
            // Skip logging deaths for short-lived (< 5 minutes) spawner entities.
            if (entity.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER && entity.getTicksLived() <= 6000) {
                return;
            }

            // Skip entities burning in daylight
            if (cause == DamageCause.FIRE_TICK && ((entity instanceof Zombie zombie && zombie.shouldBurnInDay()) || (entity instanceof AbstractSkeleton skeleton && skeleton.shouldBurnInDay())) && entity.getWorld().isDayTime()) {
                return;
            }

            // A surprising amount of bats die from flying right into lava
            if (entity.getType() == EntityType.BAT && (cause == DamageCause.LAVA || cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK)) {
                return;
            }
        }

        if (damage instanceof EntityDamageByEntityEvent attack) {
            Entity attacker = attack.getDamager();

            if (attacker instanceof Player player) {
                e = player.getName();
            }
            else if (attacker instanceof AbstractArrow || attacker instanceof ThrownPotion) {
                ProjectileSource shooter = ((Projectile) attacker).getShooter();

                if (shooter instanceof Player player) {
                    e = player.getName();
                } else if (shooter instanceof LivingEntity livingEntity) {
                    EntityType entityType = livingEntity.getType();
                    String name = entityType.name().toLowerCase(Locale.ROOT);
                    e = "#" + name;
                }
            }
            else {
                e = "#" + attacker.getType().name().toLowerCase(Locale.ROOT);
            }
        }
        else {
            if (cause.equals(EntityDamageEvent.DamageCause.FIRE) || cause == DamageCause.FIRE_TICK) {
                e = "#fire";
            }
            else if (cause.equals(EntityDamageEvent.DamageCause.LAVA)) {
                e = "#lava";
            }
            else if (cause.equals(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
                e = "#explosion";
            }
            else if (cause.equals(EntityDamageEvent.DamageCause.MAGIC)) {
                e = "#magic";
            }
            else if (cause.equals(EntityDamageEvent.DamageCause.WITHER)) {
                e = "#wither_effect";
            } else if (cause == DamageCause.HOT_FLOOR) {
                e = "#magma_block";
            } else if (cause == DamageCause.SONIC_BOOM) {
                e = "#warden";
            }
            else if (!cause.name().contains("_")) {
                e = "#" + cause.name().toLowerCase(Locale.ROOT);
            }
        }

        if (entity instanceof ArmorStand) {
            Location entityLocation = entity.getLocation();
            if (!Config.getConfig(entityLocation.getWorld()).ITEM_TRANSACTIONS) {
                entityLocation.setY(entityLocation.getY() + 0.99);
                Block block = entityLocation.getBlock();
                Queue.queueBlockBreak(e, block.getState(), Material.ARMOR_STAND, null, (int) entityLocation.getYaw());
            }
            /*
            else if (isCommand) {
                entityLocation.setY(entityLocation.getY() + 0.99);
                Block block = entityLocation.getBlock();
                Database.containerBreakCheck(e, Material.ARMOR_STAND, entity, null, block.getLocation());
                Queue.queueBlockBreak(e, block.getState(), Material.ARMOR_STAND, null, (int) entityLocation.getYaw());
            }
            */
            return;
        }

        EntityType entity_type = entity.getType();
        if (e.isEmpty()) {
            // assume killed self
            if (!(entity instanceof Player) && entity_type.name() != null) {
                // Player player = (Player)entity;
                // e = player.getName();
                e = "#" + entity_type.name().toLowerCase(Locale.ROOT);
            }
            else if (entity instanceof Player) {
                e = entity.getName();
            }
        }

        if (e.startsWith("#wither") && !e.equals("#wither_effect")) {
            e = "#wither";
        }

        if (e.startsWith("#enderdragon")) {
            e = "#enderdragon";
        }

        if (e.startsWith("#primedtnt") || e.startsWith("#tnt")) {
            e = "#tnt";
        }

        if (e.startsWith("#lightning")) {
            e = "#lightning";
        }

        if (e.isEmpty()) {
            return;
        }

        if (Config.getConfig(entity.getWorld()).DUPLICATE_SUPPRESSION && shouldSuppressEntityKill(e, entity, cause)) {
            return;
        }

        if (!(entity instanceof Player)) {
            Queue.queueEntityKill(e, entity.getLocation(), entity.getType(), EntityUtils.serializeEntity(entity));
        }
        else {
            Queue.queuePlayerKill(e, entity.getLocation(), entity.getName());
        }
    }

    private static boolean shouldSuppressEntityKill(String user, LivingEntity entity, DamageCause cause) {
        if (user == null || !user.startsWith("#") || "#command".equals(user)) {
            return false;
        }

        Location location = entity.getLocation();
        String causeName = cause == null ? "UNKNOWN" : cause.name();
        String customName = entity.getCustomName();
        String signature = location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ() + "." + user + "." + entity.getType().name() + "." + causeName + "." + (customName == null ? "-" : customName);
        return CacheHandler.shouldSuppressRepeat(CacheHandler.entityKillDuplicateCache, signature, ENTITY_KILL_DUPLICATE_THRESHOLD, ENTITY_KILL_DUPLICATE_WINDOW_SECONDS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) {
            return;
        }

        if (EntitySpawnTracking.isTrackedOrPendingIdentity(entity)) {
            EntityInteractionListener.flushPendingInteractions(entity);
            Queue.queueEntitySpawnRemoved(entity.getUniqueId(), entity.getLocation());
            EntitySpawnTracking.forget(entity.getUniqueId());
        }

        logEntityDeath(entity, null);
    }
}
