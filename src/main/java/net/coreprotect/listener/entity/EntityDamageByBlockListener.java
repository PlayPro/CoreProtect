package net.coreprotect.listener.entity;

import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.listener.player.PlayerInteractEntityListener;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.ItemUtils;

public final class EntityDamageByBlockListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onEntityDamageByBlock(EntityDamageByBlockEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemFrame) && !(entity instanceof ArmorStand) && !(entity instanceof EnderCrystal)) {
            return;
        }

        Block damager = event.getDamager();
        if (damager == null || damager.getType() == Material.MAGMA_BLOCK) {
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        Block block = entity.getLocation().getBlock();
        String user = "#" + damager.getType().name().toLowerCase(Locale.ROOT);
        if (user.contains("tnt")) {
            user = "#tnt";
        }

        if (entity instanceof ItemFrame && Config.getConfig(entity.getWorld()).ITEM_TRANSACTIONS) {
            ItemFrame frame = (ItemFrame) entity;
            if (frame.getItem().getType() != Material.AIR) {
                ItemStack[] oldState = new ItemStack[] { frame.getItem().clone() };
                ItemStack[] newState = new ItemStack[] { new ItemStack(Material.AIR) };
                PlayerInteractEntityListener.queueContainerSpecifiedItems(user, Material.ITEM_FRAME, new Object[] { oldState, newState, frame.getFacing() }, frame.getLocation(), false);
            }
        }
        else if (entity instanceof ArmorStand && Config.getConfig(entity.getWorld()).BLOCK_BREAK) {
            ArmorStand armorStand = (ArmorStand) entity;
            boolean lethalDamage = armorStand.isDead();
            if (!lethalDamage) {
                double finalDamage = event.getFinalDamage();
                lethalDamage = finalDamage > 0.0D && finalDamage >= armorStand.getHealth();
            }

            if (lethalDamage && Config.getConfig(entity.getWorld()).ITEM_TRANSACTIONS) {
                Location entityLocation = armorStand.getLocation();
                ItemStack[] contents = ItemUtils.getContainerContents(Material.ARMOR_STAND, armorStand, block.getLocation());
                String killer = user;

                Scheduler.runTask(CoreProtect.getInstance(), () -> {
                    if (armorStand.isDead()) {
                        entityLocation.setY(entityLocation.getY() + 0.99);
                        Database.containerBreakCheck(killer, Material.ARMOR_STAND, armorStand, contents, block.getLocation());
                        Queue.queueBlockBreak(killer, block.getState(), Material.ARMOR_STAND, null, (int) entityLocation.getYaw());
                    }
                }, armorStand);
            }
        }
        else if (entity instanceof EnderCrystal && Config.getConfig(entity.getWorld()).BLOCK_BREAK) {
            EnderCrystal crystal = (EnderCrystal) event.getEntity();
            Queue.queueBlockBreak(user, block.getState(), Material.END_CRYSTAL, null, crystal.isShowingBottom() ? 1 : 0);
        }
    }

}
