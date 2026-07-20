package net.coreprotect.consumer.process;

import java.util.Map;

import org.bukkit.Location;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.logger.PlayerSessionLogger;
import net.coreprotect.database.logger.UsernameLogger;
import net.coreprotect.model.action.SessionActions;

class PlayerLoginProcess {

    static void process(ConsumerWriteBatch batch, int batchCount, int processId, int id, Object object, int configSessions, int configUsernames, int time, String user) {
        if (object instanceof Location) {
            Map<Integer, String> strings = Consumer.consumerStrings.get(processId);
            if (strings.get(id) != null) {
                String uuid = strings.get(id);
                Location location = (Location) object;
                UsernameLogger.log(batch, user, uuid, configUsernames, time);
                if (configSessions == 1) {
                    PlayerSessionLogger.log(batch, batchCount, user, location, time, SessionActions.LOGIN);
                }
            }
        }
    }
}
