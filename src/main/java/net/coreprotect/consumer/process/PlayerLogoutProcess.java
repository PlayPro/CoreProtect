package net.coreprotect.consumer.process;

import net.coreprotect.database.logger.PlayerSessionLogger;
import org.bukkit.Location;

import java.sql.PreparedStatement;

class PlayerLogoutProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, Object object, int time, String user) {
        if (object instanceof Location) {
            Location location = (Location) object;
            PlayerSessionLogger.log(preparedStmt, batchCount, user, location, time, 0);
        }
    }
}
