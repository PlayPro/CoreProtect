package net.coreprotect.listener.entity;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.PlayerInteractEntityListener;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.ItemUtils;

public final class EntityDamageByEntityListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    protected void onInspectDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if ((entity instanceof ItemFrame || entity instanceof ArmorStand || entity instanceof EnderCrystal) && event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()))) {
                event.setCancelled(true);
                HangingBreakByEntityListener.inspectItemFrame(entity.getLocation().getBlock().getState(), player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        Entity damager = event.getDamager();
        if (event.isCancelled() || damager == null || entity.isDead()
                || !(entity instanceof ItemFrame || entity instanceof ArmorStand || entity instanceof EnderCrystal)) {
            return;
        }

        Config config = Config.getConfig(entity.getWorld());
        Entity attacker = EntityUtils.getEntitySource(damager);
        boolean creative = attacker instanceof Player && ((Player) attacker).getGameMode() == GameMode.CREATIVE;
        if (!creative && entity.isInvulnerable()) {
            return;
        }
        String user = EntityUtils.getBreakUser(damager, "#entity");
        if (entity instanceof ItemFrame && config.ITEM_TRANSACTIONS) {
            ItemFrame frame = (ItemFrame) entity;
            if (event.getCause() != DamageCause.ENTITY_EXPLOSION && event.getCause() != DamageCause.BLOCK_EXPLOSION) {
                logFrameItemRemoval(frame, user, !creative);
            }
        }
        else if (entity instanceof EnderCrystal && config.BLOCK_BREAK) {
            EnderCrystal crystal = (EnderCrystal) entity;
            Queue.queueBlockBreak(user, crystal.getLocation().getBlock().getState(), Material.END_CRYSTAL, null, crystal.isShowingBottom() ? 1 : 0);
        }
        else if (entity instanceof ArmorStand && config.ITEM_TRANSACTIONS) {
            ItemStack[] contents = ItemUtils.getContainerContents(Material.ARMOR_STAND, entity, entity.getLocation().getBlock().getLocation());
            EntityDeathListener.trackArmorStandBreak(entity, event, user, contents);
        }
    }

    static void logFrameItemRemoval(ItemFrame frame, String user, boolean logDrops) {
        if (frame.isFixed()) {
            return;
        }
        ItemStack item = frame.getItem();
        if (item != null && !BlockUtils.isAir(item.getType()) && item.getAmount() > 0) {
            ItemStack[] before = { item.clone() };
            ItemStack[] after = new ItemStack[0];
            PlayerInteractEntityListener.queueContainerSpecifiedItems(user, Material.ITEM_FRAME, new Object[] { before, after, frame.getFacing() }, frame.getLocation(), logDrops);
        }
    }
}
