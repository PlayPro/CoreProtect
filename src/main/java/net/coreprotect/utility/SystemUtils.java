package net.coreprotect.utility;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

public class SystemUtils {

    private SystemUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static CentralProcessor getProcessorInfo() {
        CentralProcessor result = null;
        try {
            Class.forName("com.sun.jna.Platform");
            if (System.getProperty("os.name").startsWith("Windows") && !System.getProperty("sun.arch.data.model").equals("64")) {
                Class.forName("com.sun.jna.platform.win32.Win32Exception");
            }
            else if (System.getProperty("os.name").toLowerCase().contains("android") || System.getProperty("java.runtime.name").toLowerCase().contains("android")) {
                return null;
            }
            Configurator.setLevel("oshi.hardware.common.AbstractCentralProcessor", Level.OFF);
            SystemInfo systemInfo = new SystemInfo();
            result = systemInfo.getHardware().getProcessor();
        }
        catch (Exception e) {
            // unable to read processor information
        }

        return result;
    }
} 