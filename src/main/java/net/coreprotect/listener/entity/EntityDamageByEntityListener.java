package net.coreprotect.listener.entity;

import java.util.Locale;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.listener.player.PlayerInteractEntityListener;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.ItemUtils;

public final class EntityDamageByEntityListener extends Queue implements Listener {

    // EntityPickupItemEvent resulting from this event can trigger BEFORE this event if both are set to MONITOR
    @EventHandler(priority = EventPriority.HIGHEST)
    protected void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        if (event.getEntity() instanceof ItemFrame || event.getEntity() instanceof ArmorStand || event.getEntity() instanceof EnderCrystal) {
            boolean inspecting = false;
            String user = "#entity";

            if (damager != null) {
                final Entity entity = event.getEntity();
                Location entityLocation = entity.getLocation();
                Block block = entityLocation.getBlock();
                boolean logDrops = true;

                if (damager instanceof Player) {
                    Player player = (Player) damager;
                    user = player.getName();
                    logDrops = player.getGameMode() != GameMode.CREATIVE;

                    if (ConfigHandler.inspecting.get(player.getName()) != null) {
                        if (ConfigHandler.inspecting.get(player.getName())) {
                            if (entity instanceof ArmorStand) {
                                entityLocation.setY(entityLocation.getY() + 0.99);
                            }

                            HangingBreakByEntityListener.inspectItemFrame(entityLocation.getBlock().getState(), player);
                            event.setCancelled(true);
                            inspecting = true;
                        }
                    }
                }
                else if (damager instanceof AbstractArrow) {
                    AbstractArrow arrow = (AbstractArrow) damager;
                    ProjectileSource source = arrow.getShooter();

                    if (source instanceof Player) {
                        Player player = (Player) source;
                        user = player.getName();
                    }
                    else if (source instanceof LivingEntity) {
                        EntityType entityType = ((LivingEntity) source).getType();
                        if (entityType != null) { // Check for MyPet plugin
                            String name = entityType.name().toLowerCase(Locale.ROOT);
                            user = "#" + name;
                        }
                    }
                }
                else if (damager instanceof TNTPrimed) {
                    user = "#tnt";
                }
                else if (damager instanceof Minecart) {
                    String name = damager.getType().name();
                    if (name.contains("TNT")) {
                        user = "#tnt";
                    }
                }
                else if (damager instanceof Creeper) {
                    user = "#creeper";
                }
                else if (damager instanceof EnderDragon || damager instanceof EnderDragonPart) {
                    user = "#enderdragon";
                }
                else if (damager instanceof Wither || damager instanceof WitherSkull) {
                    user = "#wither";
                }
                else if (damager.getType() != null) {
                    user = "#" + damager.getType().name().toLowerCase(Locale.ROOT);
                }

                if (!event.isCancelled() && !inspecting) {
                    if (entity instanceof ItemFrame && Config.getConfig(entityLocation.getWorld()).ITEM_TRANSACTIONS) {
                        ItemFrame frame = (ItemFrame) entity;
                        if (frame.getItem().getType() != Material.AIR) {
                            ItemStack[] oldState = new ItemStack[] { frame.getItem().clone() };
                            ItemStack[] newState = new ItemStack[] { new ItemStack(Material.AIR) };
                            PlayerInteractEntityListener.queueContainerSpecifiedItems(user, Material.ITEM_FRAME, new Object[] { oldState, newState, frame.getFacing() }, frame.getLocation(), logDrops);
                        }
                    }
                    else if (entity instanceof EnderCrystal && Config.getConfig(entity.getWorld()).BLOCK_BREAK) {
                        EnderCrystal crystal = (EnderCrystal) event.getEntity();
                        Queue.queueBlockBreak(user, block.getState(), Material.END_CRYSTAL, null, crystal.isShowingBottom() ? 1 : 0);
                    }
                    else if (entity instanceof ArmorStand && Config.getConfig(entity.getWorld()).BLOCK_BREAK) {
                        // Do this here, as we're unable to read armor stand contents on EntityDeathEvent (in survival mode)
                        if (Config.getConfig(entityLocation.getWorld()).ITEM_TRANSACTIONS) {
                            String killer = user;
                            ItemStack[] contents = ItemUtils.getContainerContents(Material.ARMOR_STAND, entity, block.getLocation());
                            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                                if (entity != null && entity.isDead()) {
                                    entityLocation.setY(entityLocation.getY() + 0.99);
                                    Database.containerBreakCheck(killer, Material.ARMOR_STAND, entity, contents, block.getLocation());
                                    Queue.queueBlockBreak(killer, block.getState(), Material.ARMOR_STAND, null, (int) entityLocation.getYaw());
                                }
                            }, entity);
                        }
                    }
                }
            }
        }
    }
}
