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
                SignTextLogger.log(preparedStmt, batchCount, user, location, action, color, (Integer) SIGN_DATA[0], (Integer) SIGN_DATA[1], (Boolean) SIGN_DATA[2], (Boolean) SIGN_DATA[3], (String) SIGN_DATA[4], (String) SIGN_DATA[5], (String) SIGN_DATA[6], (String) SIGN_DATA[7], (String) SIGN_DATA[8], (String) SIGN_DATA[9], (String) SIGN_DATA[10], (String) SIGN_DATA[11], forceData);
                signs.remove(id);
            }
        }
    }
}
