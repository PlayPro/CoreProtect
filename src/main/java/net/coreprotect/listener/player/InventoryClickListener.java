package net.coreprotect.listener.player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.ErrorReporter;

public final class InventoryClickListener implements Listener {

    private final Map<UUID, InventoryResultClick> pendingResults = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        settlePending(playerId);
        Inventory inventory = event.getInventory();
        InventoryResultClick capture;
        if (inventory instanceof AnvilInventory && event.getRawSlot() == 2) {
            capture = InventoryResultClick.capture(event, player, inventory, () -> new ItemStack[] { inventory.getItem(0), inventory.getItem(1) }, false, false);
        }
        else {
            capture = CraftItemListener.captureCraftOrTrade(event, player, inventory);
        }
        CoreProtect plugin = CoreProtect.getInstance();
        if (capture == null || plugin == null) {
            return;
        }

        pendingResults.put(playerId, capture);
        Runnable complete = () -> {
            if (pendingResults.remove(playerId, capture)) {
                capture.settle();
            }
        };
        Runnable retire = () -> pendingResults.remove(playerId, capture);
        try {
            if (ConfigHandler.isFolia) {
                if (!PaperAdapter.ADAPTER.executeEntityTask(plugin, player, complete, retire, 1L)) {
                    retire.run();
                }
            }
            else {
                Scheduler.scheduleSyncDelayedTask(plugin, complete, player, 1);
            }
        }
        catch (RuntimeException e) {
            retire.run();
            ErrorReporter.report(e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onInventoryDrag(InventoryDragEvent event) {
        settlePending(event.getWhoClicked().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity player = event.getPlayer();
        settlePending(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPlayerQuit(PlayerQuitEvent event) {
        settlePending(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onResultDrop(PlayerDropItemEvent event) {
        InventoryResultClick capture = pendingResults.get(event.getPlayer().getUniqueId());
        if (capture != null) {
            capture.observeDrop(event.getItemDrop().getItemStack());
        }
    }

    private void settlePending(UUID playerId) {
        InventoryResultClick pending = pendingResults.remove(playerId);
        if (pending != null) {
            pending.settle();
        }
    }

}
