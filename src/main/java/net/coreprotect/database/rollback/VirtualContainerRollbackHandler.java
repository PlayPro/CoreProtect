package net.coreprotect.database.rollback;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.coreprotect.CoreProtect;
import net.coreprotect.api.VirtualContainerChange;
import net.coreprotect.api.VirtualContainerHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.database.statement.ContainerStatement;
import net.coreprotect.model.item.ItemTransactionActions;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.MaterialUtils;

/**
 * Rolls back and restores virtual containers through the handlers their plugins registered. Only the rows a handler
 * applied are marked, so a later rollback or restore retries the rest.
 */
public final class VirtualContainerRollbackHandler extends Queue {

    private static final Map<String, Registration> registrations = new ConcurrentHashMap<>();
    private static final Set<Integer> activeContainers = ConcurrentHashMap.newKeySet();

    private VirtualContainerRollbackHandler() {
        throw new IllegalStateException("Database class");
    }

    public static void register(Plugin plugin, String type, VirtualContainerHandler handler) {
        registrations.put(type, new Registration(plugin, handler));
    }

    /**
     * Groups the rows by container in the order they are applied: newest first for a rollback, oldest first for a
     * restore. Rows that are already rolled back or restored are left out. Cancels the context when another rollback
     * or restore holds one of the containers.
     */
    static Work prepare(Connection connection, List<Object[]> rows, int rollbackType, EntitySpawnRollbackHandler.Context context) throws SQLException {
        Map<Integer, List<Object[]>> rowsByContainer = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (MaterialUtils.rolledBack((Integer) row[9], false) == rollbackType) {
                rowsByContainer.computeIfAbsent((Integer) row[15], key -> new ArrayList<>()).add(row);
            }
        }

        Work work = new Work(rollbackType);
        Map<Integer, String> keys = ContainerStatement.loadVirtualKeys(connection, rowsByContainer.keySet());
        for (Map.Entry<Integer, List<Object[]>> entry : rowsByContainer.entrySet()) {
            String key = keys.get(entry.getKey());
            if (key == null) {
                continue;
            }
            if (!activeContainers.add(entry.getKey())) {
                context.reportFailure("Virtual container " + key + " is already being rolled back or restored", null);
                context.cancel();
                break;
            }
            work.claimedContainers.add(entry.getKey());
            if (rollbackType == 1) {
                Collections.reverse(entry.getValue());
            }
            work.containers.add(new Container(key, entry.getValue()));
        }
        return work;
    }

    /**
     * Applies the containers on the server thread, a batch per tick, as mutations of the rollback context.
     */
    static void process(EntitySpawnRollbackHandler.Context context, Work work, String userString) {
        if (work.containers.isEmpty()) {
            return;
        }

        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        context.addPending(completion);
        scheduleBatch(context, work, userString, completion);
    }

    /**
     * Logs how many changes of each type were skipped, and returns the total.
     */
    static int reportSkipped(Work work, String userString) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : work.skippedChanges.entrySet()) {
            int count = entry.getValue();
            String reason = work.missingTypes.contains(entry.getKey()) ? ": no enabled plugin has registered a handler for it." : ".";
            Chat.console("Rollback/restore for " + userString + " skipped " + count + " virtual container " + (count == 1 ? "change" : "changes") + " of type " + entry.getKey() + reason);
            total += count;
        }
        return total;
    }

    static void release(Work work) {
        activeContainers.removeAll(work.claimedContainers);
    }

    private static void scheduleBatch(EntitySpawnRollbackHandler.Context context, Work work, String userString, CompletableFuture<Boolean> completion) {
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            // Nothing is changed once the rollback is cancelled, and cleanup waits for a started mutation to finish
            if (!context.beginMutation()) {
                completion.complete(false);
                return;
            }

            try {
                applyBatch(context, work, userString);
                if (work.index < work.containers.size()) {
                    scheduleBatch(context, work, userString, completion);
                }
                else {
                    completion.complete(true);
                }
            }
            catch (Exception e) {
                context.cancel();
                ErrorReporter.report(e);
                completion.complete(false);
            }
            finally {
                context.endMutation();
            }
        }, null, 0);
    }

    private static void applyBatch(EntitySpawnRollbackHandler.Context context, Work work, String userString) {
        List<Object[]> updates = new ArrayList<>();
        long batchBudget = Rollback.adaptiveBatchBudgetNanos();
        long batchStart = System.nanoTime();
        try {
            do {
                updates.addAll(apply(context, work, work.containers.get(work.index++)));
            }
            while (work.index < work.containers.size() && !context.isCancelled() && (System.nanoTime() - batchStart) < batchBudget);
        }
        finally {
            if (!updates.isEmpty()) {
                queueRollbackUpdate(userString, null, updates, Process.VIRTUAL_CONTAINER_ROLLBACK_UPDATE, work.rollbackType);
            }
        }
    }

    /**
     * @return The rows the handler applied
     */
    static List<Object[]> apply(EntitySpawnRollbackHandler.Context context, Work work, Container container) {
        Registration registration = registrations.get(container.type);
        if (registration == null || !registration.plugin.isEnabled()) {
            work.skip(container.type, container.rows.size(), true);
            return Collections.emptyList();
        }

        List<Object[]> rows = new ArrayList<>(container.rows.size());
        List<VirtualContainerChange> changes = new ArrayList<>(container.rows.size());
        for (Object[] row : container.rows) {
            Material type = MaterialUtils.getType((Integer) row[6]);
            if (type == null) {
                continue;
            }
            List<Object> metadata = RollbackUtil.deserializeMetadata((byte[]) row[12]);
            ItemStack item = new ItemStack(type, (Integer) row[11]);
            if (metadata != null) {
                item = (ItemStack) RollbackUtil.populateItemStack(item, metadata)[2];
            }
            boolean addition = (Integer) row[8] == (work.rollbackType == 0 ? ItemTransactionActions.REMOVE : ItemTransactionActions.ADD);
            changes.add(new VirtualContainerChange(item, addition, getSlot(metadata)));
            rows.add(row);
        }

        int applied = 0;
        try {
            applied = registration.handler.apply(container.id, Collections.unmodifiableList(changes));
            if (applied < 0 || applied > changes.size()) {
                registration.plugin.getLogger().warning("Virtual container handler returned " + applied + " for " + changes.size() + " CoreProtect changes to " + container.key + ", so none of them were marked.");
                applied = 0;
            }
        }
        catch (Exception e) {
            registration.plugin.getLogger().log(Level.WARNING, "Virtual container handler failed to apply CoreProtect changes to " + container.key + ", so none of them were marked.", e);
        }

        work.skip(container.type, container.rows.size() - applied, false);
        List<Object[]> appliedRows = rows.subList(0, applied);
        int items = 0;
        for (Object[] row : appliedRows) {
            items += (Integer) row[11];
        }
        context.addItemCount(items);
        return appliedRows;
    }

    /**
     * @return The slot stored in the metadata of a row, or -1 for a row logged without one. populateItemStack reports
     *         a missing slot as 0.
     */
    private static int getSlot(List<Object> metadata) {
        if (metadata != null) {
            for (Object entry : metadata) {
                if (entry instanceof List && !((List<?>) entry).isEmpty() && ((List<?>) entry).get(0) instanceof Map) {
                    Object slot = ((Map<?, ?>) ((List<?>) entry).get(0)).get("slot");
                    if (slot instanceof Integer) {
                        return (Integer) slot;
                    }
                }
            }
        }
        return -1;
    }

    static final class Work {
        private final int rollbackType;
        private final List<Container> containers = new ArrayList<>();
        private final Set<Integer> claimedContainers = new HashSet<>();
        private final Map<String, Integer> skippedChanges = new ConcurrentHashMap<>();
        private final Set<String> missingTypes = ConcurrentHashMap.newKeySet();
        private int index;

        private Work(int rollbackType) {
            this.rollbackType = rollbackType;
        }

        private void skip(String type, int count, boolean missing) {
            if (count > 0) {
                skippedChanges.merge(type, count, Integer::sum);
            }
            if (missing) {
                missingTypes.add(type);
            }
        }
    }

    static final class Container {
        private final String key;
        private final String type;
        private final String id;
        private final List<Object[]> rows;

        private Container(String key, List<Object[]> rows) {
            // a key splits into type and id at its only "/"
            int separator = key.indexOf('/');
            this.key = key;
            this.type = key.substring(0, separator);
            this.id = key.substring(separator + 1);
            this.rows = rows;
        }
    }

    private static final class Registration {
        private final Plugin plugin;
        private final VirtualContainerHandler handler;

        private Registration(Plugin plugin, VirtualContainerHandler handler) {
            this.plugin = plugin;
            this.handler = handler;
        }
    }
}
