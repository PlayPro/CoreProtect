package net.coreprotect.listener.entity;

import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.player.inspector.BlockInspector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.entity.HangingUtil;

public final class HangingBreakByEntityListener implements Listener {

    private static final BlockInspector blockInspector = new BlockInspector();

    public static void inspectEntity(BlockState block, Player player, UUID entityUuid) {
        if (!player.hasPermission("coreprotect.inspect")) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            ConfigHandler.inspecting.put(player.getName(), false);
            return;
        }
        blockInspector.performEntityLookup(player, block, entityUuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    protected void onInspectHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player) {
            Player player = (Player) event.getRemover();
            if (Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()))) {
                Entity entity = event.getEntity();
                event.setCancelled(true);
                inspectEntity(entity.getLocation().getBlock().getState(), player, EntitySpawnTracking.isCushion(entity) ? entity.getUniqueId() : null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        Hanging entity = event.getEntity();
        if (event.isCancelled() || !HangingUtil.isSupportedDecoration(entity)) {
            return;
        }

        Entity remover = event.getRemover();
        String culprit = EntityUtils.getBreakUser(remover, "#entity");
        if (entity instanceof ItemFrame && Config.getConfig(entity.getWorld()).ITEM_TRANSACTIONS) {
            Entity attacker = EntityUtils.getEntitySource(remover);
            boolean logDrops = !(attacker instanceof Player) || ((Player) attacker).getGameMode() != GameMode.CREATIVE;
            HangingUtil.logFrameItemRemoval((ItemFrame) entity, culprit, logDrops);
        }

        BlockState blockEvent = entity.getLocation().getBlock().getState();
        if (Config.getConfig(blockEvent.getWorld()).BLOCK_BREAK) {
            Queue.queueBlockBreak(culprit, blockEvent, HangingUtil.getMaterial(entity), HangingUtil.getFacingData(entity), HangingUtil.getArtId(entity));
        }
    }

}
