package net.coreprotect.listener.entity;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.BlockLookup;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.player.PlayerInteractEntityListener;
import net.coreprotect.listener.player.inspector.BaseInspector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.LookupThrottle;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.EntityUtils;

public final class HangingBreakByEntityListener extends Queue implements Listener {

    static void inspectItemFrame(final BlockState block, final Player player) {
        inspectEntity(block, player, null);
    }

    public static void inspectEntity(final BlockState block, final Player player, final UUID entityUuid) {
        // block check
        if (!player.hasPermission("coreprotect.inspect")) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            ConfigHandler.inspecting.put(player.getName(), false);
            return;
        }
        if (ConfigHandler.converterRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
            return;
        }
        if (ConfigHandler.purgeRunning) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
            return;
        }
        LookupThrottle.Claim claim = LookupThrottle.acquire(player.getName(), 100);
        if (claim == null) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
            return;
        }

        class BasicThread implements Runnable {
            @Override
            public void run() {
                try (Connection connection = Database.getConnection(true)) {
                    if (connection != null) {
                        try (Statement statement = connection.createStatement()) {
                            String blockData = entityUuid == null ? BlockLookup.performLookup(null, statement, block, player, 0, 1, 7) : BlockLookup.performEntityLookup(null, statement, block, player, 1, 7, entityUuid);
                            if (blockData.contains("\n")) {
                                for (String b : blockData.split("\n")) {
                                    Chat.sendComponent(player, b);
                                }
                            }
                            else if (!blockData.isEmpty()) {
                                Chat.sendComponent(player, blockData);
                            }
                        }
                    }
                    else {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                    }
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
        BaseInspector.runLookup(player, claim, new BasicThread());
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
        if (event.isCancelled()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof ItemFrame || entity instanceof Painting) {
            Entity remover = event.getRemover();
            Entity attacker = EntityUtils.getEntitySource(remover);
            boolean logDrops = !(attacker instanceof Player) || ((Player) attacker).getGameMode() != GameMode.CREATIVE;
            String culprit = EntityUtils.getBreakUser(remover, "#entity");
            BlockState blockEvent = entity.getLocation().getBlock().getState();

            String blockData = null;
            Material material;
            int itemData = 0;
            if (entity instanceof ItemFrame) {
                material = BukkitAdapter.ADAPTER.getFrameType(entity);
                ItemFrame itemframe = (ItemFrame) entity;
                blockData = "FACING=" + itemframe.getFacing().name();

                if (Config.getConfig(entity.getWorld()).ITEM_TRANSACTIONS) {
                    if (itemframe.getItem().getType() != Material.AIR) {
                        ItemStack[] oldState = new ItemStack[] { itemframe.getItem().clone() };
                        ItemStack[] newState = new ItemStack[] { new ItemStack(Material.AIR) };
                        PlayerInteractEntityListener.queueContainerSpecifiedItems(culprit, Material.ITEM_FRAME, new Object[] { oldState, newState, itemframe.getFacing() }, itemframe.getLocation(), logDrops);
                    }
                }
            }
            else {
                material = Material.PAINTING;
                Painting painting = (Painting) entity;
                blockData = "FACING=" + painting.getFacing().name();
                try {
                    itemData = MaterialUtils.getArtId(MaterialUtils.getPaintingArtName(painting), true);
                }
                catch (IncompatibleClassChangeError e) {
                    // 1.21.2+
                }
            }

            if (Config.getConfig(blockEvent.getWorld()).BLOCK_BREAK) {
                Queue.queueBlockBreak(culprit, blockEvent, material, blockData, itemData);
            }
        }
    }

}
