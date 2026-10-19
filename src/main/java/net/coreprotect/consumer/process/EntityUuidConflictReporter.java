package net.coreprotect.consumer.process;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

final class EntityUuidConflictReporter {
    private static final long INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(5);
    private final LongSupplier clock;
    private final Consumer<String> warning;
    private boolean warned;
    private long lastWarning;
    private long interactions;
    private long removals;
    private UUID lastUuid;

    EntityUuidConflictReporter() {
        this(System::nanoTime, message -> Logger.getLogger("CoreProtect").warning("[CoreProtect] " + message));
    }

    EntityUuidConflictReporter(LongSupplier clock, Consumer<String> warning) {
        this.clock = clock;
        this.warning = warning;
    }

    synchronized void record(boolean removal, UUID uuid) {
        if (removal) removals++;
        else interactions++;
        lastUuid = uuid;
        flush();
    }

    synchronized void flush() {
        if (interactions == 0 && removals == 0) return;
        long now = clock.getAsLong();
        if (warned && now - lastWarning < INTERVAL_NANOS) return;
        String skipped = interactions == 0 ? "" : interactions + " entity interaction record(s)";
        if (removals > 0) skipped += (skipped.isEmpty() ? "" : " and ") + removals + " entity removal record(s)";
        warning.accept("Skipped " + skipped + " due to unresolved DuckDB UUID conflicts. "
                + "These records were not saved; consumer processing continues. Last UUID: " + lastUuid
                + ". Repeated warnings are summarized every 5 minutes.");
        warned = true;
        lastWarning = now;
        interactions = 0;
        removals = 0;
        lastUuid = null;
    }
}
