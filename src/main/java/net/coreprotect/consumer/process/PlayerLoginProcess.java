package net.coreprotect.consumer.process;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.logger.PlayerSessionLogger;
import net.coreprotect.database.logger.UsernameLogger;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

class PlayerLoginProcess {

    static void process(Connection connection, PreparedStatement preparedStmt, int batchCount, int processId, int id, Object object, int configSessions, int configUsernames, int time, String user) {
        if (object instanceof Location) {
            Map<Integer, String> strings = Consumer.consumerStrings.get(processId);
            if (strings.get(id) != null) {
                String uuid = strings.get(id);
                Location location = (Location) object;
                UsernameLogger.log(connection, user, uuid, configUsernames, time);
                if (configSessions == 1) {
                    PlayerSessionLogger.log(preparedStmt, batchCount, user, location, time, 1);
                }
                strings.remove(id);
            }
        }
    }
}
