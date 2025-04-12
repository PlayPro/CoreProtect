package net.coreprotect.utility;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

public class SystemUtils {

    private static boolean testMode = Boolean.getBoolean("net.coreprotect.test");
    private static String processorInfo = null;
    private static boolean log4jInitialized = false;
    private static Double appleProcessorSpeed = null;

    private SystemUtils() {
        throw new IllegalStateException("Utility class");
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
        String osName = System.getProperty("os.name", "").toLowerCase();
        String osArch = System.getProperty("os.arch", "").toLowerCase();

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

        // Try to determine the CPU model first to use for frequency lookup
        String modelName = getProcessorModel();

        // First attempt: Get frequency from machdep.cpu.brand_string if it contains GHz info
        if (modelName != null && modelName.contains("GHz")) {
            try {
                Pattern pattern = Pattern.compile("(\\d+\\.\\d+)GHz");
                Matcher matcher = pattern.matcher(modelName);
                if (matcher.find()) {
                    appleProcessorSpeed = Double.parseDouble(matcher.group(1));
                    return appleProcessorSpeed;
                }
            }
            catch (Exception e) {
                // Failed to extract frequency from model name
            }
        }

        // Second attempt: Use sysctl method to detect P-core maximum frequencies
        try {
            ProcessBuilder pb = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string");
            Process process = pb.start();

            if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
                String appleChipInfo = null;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    appleChipInfo = reader.readLine();
                }

                if (appleChipInfo != null) {
                    // Extract M-series info
                    if (appleChipInfo.contains("M1")) {
                        return getMSeriesSpeed("M1", appleChipInfo);
                    }
                    else if (appleChipInfo.contains("M2")) {
                        return getMSeriesSpeed("M2", appleChipInfo);
                    }
                    else if (appleChipInfo.contains("M3")) {
                        return getMSeriesSpeed("M3", appleChipInfo);
                    }
                    else if (appleChipInfo.contains("M4")) {
                        return getMSeriesSpeed("M4", appleChipInfo);
                    }
                }
            }
        }
        catch (Exception e) {
            // Failed with sysctl method
        }

        // Fallback to reasonable defaults based on processor model
        if (modelName != null) {
            if (modelName.contains("M1")) {
                appleProcessorSpeed = 3.2;
            }
            else if (modelName.contains("M2")) {
                appleProcessorSpeed = 3.7;
            }
            else if (modelName.contains("M3")) {
                appleProcessorSpeed = 4.05;
            }
            else if (modelName.contains("M4")) {
                appleProcessorSpeed = 4.5;
            }
            else {
                appleProcessorSpeed = 3.0; // Generic fallback
            }
            return appleProcessorSpeed;
        }

        return 3.2; // Default fallback
    }

    /**
     * Get the processor speed based on the M-series chip variant
     * 
     * @param series
     *            The M-series (M1, M2, M3, M4)
     * @param chipInfo
     *            The full chip info string
     * @return The processor speed in GHz
     */
    private static Double getMSeriesSpeed(String series, String chipInfo) {
        switch (series) {
            case "M1":
                if (chipInfo.contains("Pro") || chipInfo.contains("Max") || chipInfo.contains("Ultra")) {
                    appleProcessorSpeed = 3.23; // M1 Pro/Max/Ultra P cores
                }
                else {
                    appleProcessorSpeed = 3.2; // Base M1 P cores
                }
                break;
            case "M2":
                if (chipInfo.contains("Pro") || chipInfo.contains("Max") || chipInfo.contains("Ultra")) {
                    appleProcessorSpeed = 3.7; // M2 Pro/Max/Ultra P cores
                }
                else {
                    appleProcessorSpeed = 3.5; // Base M2 P cores
                }
                break;
            case "M3":
                if (chipInfo.contains("Pro") || chipInfo.contains("Max")) {
                    appleProcessorSpeed = 4.05; // M3 Pro/Max P cores
                }
                else {
                    appleProcessorSpeed = 4.0; // Base M3 P cores
                }
                break;
            case "M4":
                if (chipInfo.contains("Max")) {
                    appleProcessorSpeed = 4.5; // M4 Max P cores
                }
                else if (chipInfo.contains("Pro")) {
                    appleProcessorSpeed = 4.5; // M4 Pro P cores
                }
                else {
                    appleProcessorSpeed = 4.3; // Base M4 P cores
                }
                break;
            default:
                appleProcessorSpeed = 3.2; // Default
                break;
        }
        return appleProcessorSpeed;
    }

    /**
     * Get processor model name for Apple Silicon
     * 
     * @return Processor model name, or null if unavailable
     */
    private static String getProcessorModel() {
        try {
            ProcessBuilder pb = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string");
            Process process = pb.start();

            if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    return reader.readLine();
                }
            }
        }
        catch (Exception e) {
            // Failed to get CPU model
        }

        return null;
    }

    public static CentralProcessor getProcessorInfo() {
        // In test mode, don't actually try to initialize hardware components
        if (testMode || isLog4jDisabled()) {
            return null;
        }

        CentralProcessor result = null;
        try {
            Class.forName("com.sun.jna.Platform");
            if (System.getProperty("os.name").startsWith("Windows") && !System.getProperty("sun.arch.data.model").equals("64")) {
                Class.forName("com.sun.jna.platform.win32.Win32Exception");
            }
            else if (System.getProperty("os.name").toLowerCase().contains("android") || System.getProperty("java.runtime.name").toLowerCase().contains("android")) {
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

            SystemInfo systemInfo = new SystemInfo();
            result = systemInfo.getHardware().getProcessor();
        }
        catch (Exception e) {
            // unable to read processor information
        }

        return result;
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

        CentralProcessor processor = getProcessorInfo();
        if (processor != null) {
            processorInfo = processor.getProcessorIdentifier().getName();
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
