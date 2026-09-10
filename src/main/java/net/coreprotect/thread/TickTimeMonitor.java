package net.coreprotect.thread;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.paper.PaperAdapter;

public final class TickTimeMonitor {

    private static final int MONITOR_INTERVAL_TICKS = 40;
    private static final double HEALTHY_TICK_MILLIS = 50.0D;
    private static final double LAGGING_SAMPLE_MILLIS = 51.0D;
    private static final double ESTIMATE_FLOOR_MILLIS = 38.0D;
    private static final double ESTIMATE_DECAY_MILLIS = 3.0D;

    private static volatile double estimatedTickTime = -1.0D;
    private static long lastSampleTime = -1L;

    private TickTimeMonitor() {
        throw new IllegalStateException("TickTimeMonitor class");
    }

    public static void initialize(CoreProtect plugin) {
        if (ConfigHandler.isFolia || PaperAdapter.ADAPTER.getAverageTickTime(plugin.getServer()) >= 0.0D) {
            return;
        }

        estimatedTickTime = -1.0D;
        lastSampleTime = -1L;
        Scheduler.scheduleSyncRepeatingTask(plugin, TickTimeMonitor::recordSample, null, MONITOR_INTERVAL_TICKS, MONITOR_INTERVAL_TICKS);
    }

    public static double getEstimatedTickTime() {
        return estimatedTickTime;
    }

    private static void recordSample() {
        long now = System.nanoTime();
        if (lastSampleTime > 0) {
            double sampleMillis = (now - lastSampleTime) / (MONITOR_INTERVAL_TICKS * 1_000_000.0D);
            estimatedTickTime = nextEstimate(estimatedTickTime, sampleMillis);
        }

        lastSampleTime = now;
    }

    public static double nextEstimate(double currentEstimate, double sampleMillis) {
        if (sampleMillis > LAGGING_SAMPLE_MILLIS) {
            // Ticks are stretching past the 50ms pace, so the wall-clock tick time is the real workload
            return sampleMillis;
        }

        // Ticks are keeping pace, which proves the workload is at most a full tick but hides any idle headroom below that;
        // probe for headroom by decaying the estimate, stopping at the point that maps to a 37ms batch budget
        double provenEstimate = currentEstimate > 0.0D ? Math.min(currentEstimate, HEALTHY_TICK_MILLIS) : HEALTHY_TICK_MILLIS;
        return Math.max(ESTIMATE_FLOOR_MILLIS, provenEstimate - ESTIMATE_DECAY_MILLIS);
    }
}
