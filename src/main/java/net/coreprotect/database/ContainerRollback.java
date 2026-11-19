package net.coreprotect.database;

import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import net.coreprotect.CoreProtect;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.rollback.Rollback;
import net.coreprotect.database.rollback.RollbackComplete;
import net.coreprotect.database.rollback.RollbackProcessor;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.ErrorReporter;

public class ContainerRollback extends Rollback {

    public static boolean performContainerRollbackRestore(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, String timeString, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, final Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup, boolean verbose, final int rollbackType) {
        return performContainerRollbackRestore(statement, user, checkUuids, checkUsers, timeString, restrictList, excludeList, excludeUserList, actionList, location, radius, startTime, endTime, restrictWorld, lookup, verbose, rollbackType, null);
    }

    public static boolean performContainerRollbackRestore(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, String timeString, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, final Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup, boolean verbose, final int rollbackType, BooleanSupplier admission) {
        try {
            long timeStart = System.currentTimeMillis();

            final List<Object[]> lookupList = Lookup.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup);
            if (lookupList == null) {
                sendAborted(user, "Unable to load container rollback records from the database.");
                return false;
            }
            if (rollbackType == 1) {
                Collections.reverse(lookupList);
            }
            if (Consumer.isPersistenceHalted()) {
                sendAborted(user, "Database persistence halted before container rollback updates could be queued.");
                return false;
            }

            String userString = user == null ? "#server" : user.getName();

            if (location == null || location.getWorld() == null || (admission != null && !admission.getAsBoolean())) {
                return false;
            }
            CompletableFuture<Integer> completion = new CompletableFuture<>();
            AtomicBoolean started = new AtomicBoolean();
            AtomicBoolean cancelled = new AtomicBoolean();
            Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
                if (!started.compareAndSet(false, true)) {
                    return;
                }
                try {
                    if (Consumer.isPersistenceHalted()) {
                        throw new IllegalStateException("Database persistence halted before the container could be modified");
                    }

                    World world = location.getWorld();
                    int chunkX = location.getBlockX() >> 4;
                    int chunkZ = location.getBlockZ() >> 4;
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        world.getChunkAt(chunkX, chunkZ);
                    }
                    int itemCount = RollbackProcessor.replayContainerRows(world, location.getBlock(), lookupList, rollbackType, userString, cancelled::get);
                    completion.complete(itemCount);
                }
                catch (Exception e) {
                    completion.completeExceptionally(e);
                }
            }, location, 0);

            int itemCount;
            try {
                itemCount = completion.get(300, TimeUnit.SECONDS);
            }
            catch (TimeoutException | InterruptedException e) {
                cancelled.set(true);
                if (!started.compareAndSet(false, true)) {
                    completion.handle((count, error) -> null).join();
                }
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                sendAborted(user, e instanceof TimeoutException ? "Timed out waiting for the container task." : "Interrupted while waiting for the container task.");
                return false;
            }
            long timeFinish = System.currentTimeMillis();
            double totalSeconds = (timeFinish - timeStart) / 1000.0;

            if (user != null) {
                int file = -1;
                if (itemCount > 0) {
                    file = 1;
                }
                int blockCount = 0;
                int entityCount = 0;

                RollbackComplete.output(user, location, checkUsers, restrictList, excludeList, excludeUserList, actionList, timeString, file, totalSeconds, itemCount, blockCount, entityCount, rollbackType, radius, verbose, restrictWorld, 0);
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            sendAborted(user);
            return false;
        }
    }

}
