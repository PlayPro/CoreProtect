package net.coreprotect.paper.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.utility.SyntheticUsernames;

public final class CopperGolemChestListener implements Listener {

    private static final String COPPER_GOLEM_NAME = "COPPER_GOLEM";
    private static final String USERNAME = "#copper_golem";
    private static final long DELAY_TICKS = 60L;

    private final CoreProtect plugin;
    private final Map<UUID, PendingTransaction> pendingTransactions = new ConcurrentHashMap<>();

    public CopperGolemChestListener(CoreProtect plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onValidate(ItemTransportingEntityValidateTargetEvent event) {
        if (!event.isAllowed()) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity == null || entity.getType() == null || !COPPER_GOLEM_NAME.equals(entity.getType().name())) {
            return;
        }

        Block block = event.getBlock();
        if (block == null) {
            return;
        }

        BlockState blockState = block.getState();
        if (!(blockState instanceof InventoryHolder)) {
            return;
        }

        Location location = blockState.getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }

        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        scheduleTransaction(entity, location);
    }

    private void scheduleTransaction(Entity entity, Location location) {
        UUID entityId = entity.getUniqueId();
        String username = SyntheticUsernames.qualifyWithUuid(USERNAME, entityId);
        PendingTransaction pendingTransaction = pendingTransactions.remove(entityId);
        if (pendingTransaction != null) {
            pendingTransaction.cancel();
        }

        Location targetLocation = location.clone();
        PendingTransaction scheduled = new PendingTransaction();
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!pendingTransactions.remove(entityId, scheduled)) {
                return;
            }

            Entity trackedEntity = plugin.getServer().getEntity(entityId);
            if (trackedEntity == null || !trackedEntity.isValid()) {
                return;
            }

            InventoryChangeListener.inventoryTransaction(username, targetLocation, null);
        }, DELAY_TICKS);

        scheduled.setTask(task);
        pendingTransactions.put(entityId, scheduled);
    }

    private static final class PendingTransaction {

        private BukkitTask task;

        private void cancel() {
            if (task != null) {
                task.cancel();
            }
        }

        private void setTask(BukkitTask task) {
            this.task = task;
        }
    }
}
