package net.coreprotect.listener.entity;

import java.util.Locale;

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

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.listener.player.PlayerInteractEntityListener;

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
                PlayerInteractEntityListener.queueFrameTransaction(user, frame, false);
            }
        }
        else if (entity instanceof ArmorStand && Config.getConfig(entity.getWorld()).BLOCK_BREAK) {
            Database.containerBreakCheck(user, Material.ARMOR_STAND, entity, null, block.getLocation());
            Queue.queueBlockBreak(user, block.getState(), Material.ARMOR_STAND, null, (int) entity.getLocation().getYaw());
        }
        else if (entity instanceof EnderCrystal && Config.getConfig(entity.getWorld()).BLOCK_BREAK) {
            EnderCrystal crystal = (EnderCrystal) event.getEntity();
            Queue.queueBlockBreak(user, block.getState(), Material.END_CRYSTAL, null, crystal.isShowingBottom() ? 1 : 0);
        }
    }

}
