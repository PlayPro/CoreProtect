package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.Map;

import org.bukkit.Location;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.logger.ChatLogger;

class PlayerChatProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, int processId, int id, Object[] object, String user) {
        if (object[1] instanceof Location) {
            Map<Integer, String> strings = Consumer.consumerStrings.get(processId);
            if (strings.get(id) != null) {
                String message = strings.get(id);
                Long timestamp = (Long) object[0];
                Location location = (Location) object[1];
                ChatLogger.log(preparedStmt, batchCount, timestamp, location, user, message);
                strings.remove(id);
            }
        }
    }
}
