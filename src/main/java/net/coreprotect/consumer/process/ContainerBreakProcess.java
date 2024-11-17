package net.coreprotect.consumer.process;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.logger.ContainerBreakLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.util.Map;

class ContainerBreakProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, int processId, int id, Material type, String user, Object object) {
        if (object instanceof Location) {
            Location location = (Location) object;
            Map<Integer, ItemStack[]> containers = Consumer.consumerContainers.get(processId);
            if (containers.get(id) != null) {
                ItemStack[] container = containers.get(id);
                ContainerBreakLogger.log(preparedStmt, batchCount, user, location, type, container);
                containers.remove(id);
            }
        }
    }
}
