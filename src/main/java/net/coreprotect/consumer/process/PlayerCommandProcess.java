package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.Map;

import org.bukkit.Location;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.logger.CommandLogger;

class PlayerCommandProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, int processId, int id, Object object, int time, String user) {
        if (object instanceof Location) {
            Map<Integer, String> strings = Consumer.consumerStrings.get(processId);
            if (strings.get(id) != null) {
                String message = strings.get(id);
                Location location = (Location) object;
                CommandLogger.log(preparedStmt, batchCount, time, location, user, message);
                strings.remove(id);
            }
        }
    }
}
