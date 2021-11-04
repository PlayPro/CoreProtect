package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.Map;

import org.bukkit.Location;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.logger.CommandLogger;

class PlayerCommandProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, int processId, int id, Object object, String user) {
        if (!(object instanceof Object[])) {
            return;
        }

        Object[] data = (Object[]) object;
        if (data[1] instanceof Location) {
            Map<Integer, String> strings = Consumer.consumerStrings.get(processId);
            if (strings.get(id) != null) {
                String message = strings.get(id);
                Long timestamp = (Long) data[0];
                Location location = (Location) data[1];
                CommandLogger.log(preparedStmt, batchCount, timestamp, location, user, message);
                strings.remove(id);
            }
        }
    }
}
