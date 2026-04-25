package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;

import org.bukkit.Location;
import org.bukkit.block.BlockState;

import net.coreprotect.database.logger.PlayerKillLogger;

class PlayerKillProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, int id, Object object, String user) {
        if (object instanceof Object[]) {
            Object[] values = (Object[]) object;
            if (values.length <= 1 || !isLocationData(values[0]) || !(values[1] instanceof String)) {
                return;
            }

            Location location = getLocation(values[0]);
            String player = (String) values[1];
            PlayerKillLogger.log(preparedStmt, batchCount, user, location, player);
        }
    }

    private static boolean isLocationData(Object value) {
        return value instanceof Location || value instanceof BlockState;
    }

    private static Location getLocation(Object value) {
        if (value instanceof Location) {
            return ((Location) value).clone();
        }

        BlockState block = (BlockState) value;
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }
}
