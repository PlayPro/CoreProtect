package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;

import org.bukkit.Location;

import net.coreprotect.database.logger.PlayerSessionLogger;

class PlayerLogoutProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, Object object, int time, String user) {
        if (object instanceof Location) {
            Location location = (Location) object;
            PlayerSessionLogger.log(preparedStmt, batchCount, user, location, time, 0);
        }
    }
}
