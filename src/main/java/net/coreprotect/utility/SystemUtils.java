package net.coreprotect.utility;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

public class SystemUtils {

    private static boolean testMode = Boolean.getBoolean("net.coreprotect.test");
    private static String processorInfo = null;
    private static boolean log4jInitialized = false;

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
