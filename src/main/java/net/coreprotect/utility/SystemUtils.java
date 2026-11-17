package net.coreprotect.utility;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public class SystemUtils {

    private static final Pattern GHZ_PATTERN = Pattern.compile("(\\d+\\.\\d+)GHz");
    private static volatile boolean testMode = Boolean.getBoolean("net.coreprotect.test");
    private static volatile String processorInfo = null;
    private static volatile boolean log4jInitialized = false;
    private static volatile Double appleProcessorSpeed = null;

    private SystemUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static final class ProcessorInfo {
        private final String name;
        private final long maxFrequency;

        private ProcessorInfo(String name, long maxFrequency) {
            this.name = name;
            this.maxFrequency = maxFrequency;
        }

        public String getName() {
            return name;
        }

        public long getMaxFrequency() {
            return maxFrequency;
        }
    }

    /**
     * Set test mode to skip actual hardware operations
     * 
     * @param enabled
     *            Whether to enable test mode
     */
    public static void setTestMode(boolean enabled) {
        testMode = enabled;
        if (enabled) {
            processorInfo = "Test Processor";
        }
    }

    /**
     * Check if running on Apple Silicon
     * 
     * @return True if running on Apple Silicon
     */
    public static boolean isAppleSilicon() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        return osName.contains("mac") && (osArch.contains("aarch64") || osArch.contains("arm"));
    }

    /**
     * Get processor clock speed for Apple Silicon
     * 
     * @return Processor speed in GHz, or null if unavailable
     */
    public static Double getAppleSiliconSpeed() {
        if (appleProcessorSpeed != null) {
            return appleProcessorSpeed;
        }

        if (testMode) {
            return 3.2; // Default test value
        }

        String modelName = getProcessorModel();
        if (modelName == null) {
            return 3.2; // Default fallback
        }

        Double resolved = resolveAppleSiliconSpeed(modelName);
        appleProcessorSpeed = resolved;
        return resolved;
    }

    public static Double resolveAppleSiliconSpeed(String modelName) {
        if (modelName == null) {
            return 3.2;
        }

        if (modelName.contains("GHz")) {
            try {
                Matcher matcher = GHZ_PATTERN.matcher(modelName);
                if (matcher.find()) {
                    return Double.parseDouble(matcher.group(1));
                }
            }
            catch (Exception e) {
                // Failed to extract frequency from model name
            }
        }

        if (modelName.contains("M5")) {
            return getMSeriesSpeed("M5", modelName);
        }
        else if (modelName.contains("M4")) {
            return getMSeriesSpeed("M4", modelName);
        }
        else if (modelName.contains("M3")) {
            return getMSeriesSpeed("M3", modelName);
        }
        else if (modelName.contains("M2")) {
            return getMSeriesSpeed("M2", modelName);
        }
        else if (modelName.contains("M1")) {
            return getMSeriesSpeed("M1", modelName);
        }

        return 3.0; // Generic fallback
    }

    /**
     * Get the processor speed based on the M-series chip variant
     * 
     * @param series
     *            The M-series (M1, M2, M3, M4, M5)
     * @param chipInfo
     *            The full chip info string
     * @return The processor speed in GHz
     */
    private static Double getMSeriesSpeed(String series, String chipInfo) {
        switch (series) {
            case "M1":
                if (chipInfo.contains("Pro") || chipInfo.contains("Max") || chipInfo.contains("Ultra")) {
                    return 3.23; // M1 Pro/Max/Ultra P cores
                }
                return 3.2; // Base M1 P cores
            case "M2":
                if (chipInfo.contains("Pro") || chipInfo.contains("Max") || chipInfo.contains("Ultra")) {
                    return 3.7; // M2 Pro/Max/Ultra P cores
                }
                return 3.5; // Base M2 P cores
            case "M3":
                if (chipInfo.contains("Pro") || chipInfo.contains("Max")) {
                    return 4.05; // M3 Pro/Max P cores
                }
                return 4.0; // Base M3 P cores
            case "M4":
                if (chipInfo.contains("Max") || chipInfo.contains("Pro")) {
                    return 4.5; // M4 Pro/Max P cores
                }
                return 4.3; // Base M4 P cores
            case "M5":
                if (chipInfo.contains("Max") || chipInfo.contains("Pro") || chipInfo.contains("Ultra")) {
                    return 4.6; // M5 Pro/Max/Ultra P cores
                }
                return 4.5; // Base M5 P cores
            default:
                return 3.2; // Default
        }
    }

    /**
     * Get processor model name for Apple Silicon
     * 
     * @return Processor model name, or null if unavailable
     */
    private static String getProcessorModel() {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string");
            process = pb.start();

            if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    return reader.readLine();
                }
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            // Failed to get CPU model
        }
        finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }

        return null;
    }

    public static ProcessorInfo getProcessorInfo() {
        // In test mode, don't actually try to initialize hardware components
        if (testMode || isLog4jDisabled()) {
            return null;
        }

        try {
            Class.forName("com.sun.jna.Platform");
            String osName = System.getProperty("os.name", "");
            String archDataModel = System.getProperty("sun.arch.data.model", "");
            String javaRuntimeName = System.getProperty("java.runtime.name", "");
            if (osName.startsWith("Windows") && !archDataModel.equals("64")) {
                Class.forName("com.sun.jna.platform.win32.Win32Exception");
            }
            else if (osName.toLowerCase(Locale.ROOT).contains("android") || javaRuntimeName.toLowerCase(Locale.ROOT).contains("android")) {
                return null;
            }

            try {
                if (!log4jInitialized) {
                    Configurator.setLevel("oshi.hardware.common.AbstractCentralProcessor", Level.OFF);
                    log4jInitialized = true;
                }
            }
            catch (Exception e) {
                // log4j configuration failure, continue without it
            }

            Class<?> systemInfoClass = Class.forName("oshi.SystemInfo");
            Object systemInfo = systemInfoClass.getDeclaredConstructor().newInstance();
            Object hardware = systemInfoClass.getMethod("getHardware").invoke(systemInfo);
            Object processor = Class.forName("oshi.hardware.HardwareAbstractionLayer").getMethod("getProcessor").invoke(hardware);
            Object identifier = Class.forName("oshi.hardware.CentralProcessor").getMethod("getProcessorIdentifier").invoke(processor);
            String name = String.valueOf(identifier.getClass().getMethod("getName").invoke(identifier));
            Object maxFrequency = Class.forName("oshi.hardware.CentralProcessor").getMethod("getMaxFreq").invoke(processor);

            return new ProcessorInfo(name, ((Number) maxFrequency).longValue());
        }
        catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
            // unable to read processor information
        }

        return null;
    }

    /**
     * Get processor information string (for testing)
     * 
     * @return The processor information string
     */
    public static String getProcessorInfoString() {
        if (processorInfo != null) {
            return processorInfo;
        }

        ProcessorInfo processor = getProcessorInfo();
        if (processor != null) {
            processorInfo = processor.getName();
            return processorInfo;
        }

        return "Unknown";
    }

    /**
     * Check if Log4j is disabled via system properties
     * 
     * @return true if Log4j is disabled
     */
    private static boolean isLog4jDisabled() {
        return Boolean.getBoolean("log4j2.disable") || Boolean.getBoolean("net.coreprotect.disable.log4j") || System.getProperty("log4j.configurationFile", "").contains("no-log4j2.xml");
    }
}
