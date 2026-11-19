package net.coreprotect.consumer.process;

import net.coreprotect.database.ConsumerWriteBatch;

import org.bukkit.Location;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.QueuePayload;
import net.coreprotect.database.logger.SignTextLogger;

class SignTextProcess {

    static void process(ConsumerWriteBatch preparedStmt, int batchCount, int processId, int id, int forceData, String user, Object object, int action, int color) {
        if (object instanceof Location) {
            Location location = (Location) object;
            Object[] SIGN_DATA = QueuePayload.detail(Consumer.consumerObjects.get(processId).get(id));
            if (SIGN_DATA != null) {
                SignTextLogger.log(preparedStmt, batchCount, user, location, action, color, (Integer) SIGN_DATA[0], (Integer) SIGN_DATA[1], (Boolean) SIGN_DATA[2], (Boolean) SIGN_DATA[3], (String) SIGN_DATA[4], (String) SIGN_DATA[5], (String) SIGN_DATA[6], (String) SIGN_DATA[7], (String) SIGN_DATA[8], (String) SIGN_DATA[9], (String) SIGN_DATA[10], (String) SIGN_DATA[11], forceData);
            }
        }
    }
}
