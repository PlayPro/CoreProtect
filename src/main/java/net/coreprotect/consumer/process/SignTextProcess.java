package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.Map;

import org.bukkit.Location;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.logger.SignTextLogger;

class SignTextProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, int processId, int id, int forceData, String user, Object object, int action, int color) {
        if (object instanceof Location) {
            Location location = (Location) object;
            Map<Integer, Object[]> signs = Consumer.consumerSigns.get(processId);
            if (signs.get(id) != null) {
                Object[] SIGN_DATA = signs.get(id);
                SignTextLogger.log(preparedStmt, batchCount, user, location, action, color, (Integer) SIGN_DATA[0], (String) SIGN_DATA[1], (String) SIGN_DATA[2], (String) SIGN_DATA[3], (String) SIGN_DATA[4], forceData);
                signs.remove(id);
            }
        }
    }
}
