package net.coreprotect.consumer.process;

import org.bukkit.Location;

import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.logger.ChatLogger;
import net.coreprotect.database.logger.CommandLogger;

class PlayerMessageProcess {

    static void process(ConsumerWriteBatch batch, int batchCount, Object object, String user, String message, boolean command) {
        if (!(object instanceof Object[])) {
            return;
        }
        Object[] data = (Object[]) object;
        if (!(data[1] instanceof Location) || message == null) {
            return;
        }
        long timestamp = (Long) data[0];
        Location location = (Location) data[1];
        if (command) {
            CommandLogger.log(batch, batchCount, timestamp, location, user, message);
        }
        else {
            ChatLogger.log(batch, batchCount, timestamp, location, user, message);
        }
    }
}
