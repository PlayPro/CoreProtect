package net.coreprotect.consumer.process;


import org.bukkit.Location;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.QueuePayload;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.logger.PlayerSessionLogger;
import net.coreprotect.database.logger.UsernameLogger;
import net.coreprotect.model.action.SessionActions;

class PlayerLoginProcess {

    static void process(ConsumerWriteBatch batch, int batchCount, int processId, int id, Object object, int configSessions, int configUsernames, int time, String user) {
        if (object instanceof Location) {
            String uuid = QueuePayload.detail(Consumer.consumerObjects.get(processId).get(id));
            if (uuid != null) {
                Location location = (Location) object;
                UsernameLogger.log(batch, user, uuid, configUsernames, time);
                if (configSessions == 1) {
                    PlayerSessionLogger.log(batch, batchCount, user, location, time, SessionActions.LOGIN);
                }
            }
        }
    }
}
