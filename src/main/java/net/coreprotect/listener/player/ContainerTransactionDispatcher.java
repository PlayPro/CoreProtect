package net.coreprotect.listener.player;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;

import net.coreprotect.CoreProtect;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.HopperTransactionUtils;

final class ContainerTransactionDispatcher {

    private static final int STRIPE_COUNT = 4;
    private static final Stripe[] STRIPES = new Stripe[STRIPE_COUNT];

    static {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            STRIPES[i] = new Stripe();
        }
    }

    private ContainerTransactionDispatcher() {
        throw new IllegalStateException("Utility class");
    }

    static void submit(Location location, Runnable task) {
        if (location == null || location.getWorld() == null || task == null) {
            return;
        }

        String transactionId = HopperTransactionUtils.getTransactionId(location);
        Stripe stripe = STRIPES[(transactionId.hashCode() & Integer.MAX_VALUE) % STRIPE_COUNT];
        stripe.submit(task);
    }

    static int pendingTasks() {
        int pending = 0;
        for (Stripe stripe : STRIPES) {
            pending += stripe.queue.size();
        }
        return pending;
    }

    static void drainForShutdown() {
        for (Stripe stripe : STRIPES) {
            stripe.drain();
        }
    }

    private static final class Stripe {
        private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean running = new AtomicBoolean(false);

        private void submit(Runnable task) {
            queue.add(task);
            start();
        }

        private void start() {
            if (!running.compareAndSet(false, true)) {
                return;
            }

            Scheduler.runTaskAsynchronously(CoreProtect.getInstance(), this::drain);
        }

        private void drain() {
            synchronized (this) {
                try {
                    Runnable task;
                    while ((task = queue.poll()) != null) {
                        try {
                            task.run();
                        }
                        catch (Exception e) {
                            ErrorReporter.report(e);
                        }
                    }
                }
                finally {
                    running.set(false);
                    if (!queue.isEmpty()) {
                        start();
                    }
                }
            }
        }
    }
}
