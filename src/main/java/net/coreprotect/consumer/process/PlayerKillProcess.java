package net.coreprotect.consumer.process;

import net.coreprotect.database.ConsumerWriteBatch;

import org.bukkit.Location;

import net.coreprotect.database.logger.PlayerKillLogger;

class PlayerKillProcess {

    static void process(ConsumerWriteBatch preparedStmt, int batchCount, int id, Object object, String user) {
        if (object instanceof Object[]) {
            Object[] values = (Object[]) object;
            if (values.length <= 1 || !(values[1] instanceof String)) {
                return;
            }

            Location location = EntityKillProcess.getLocation(values[0]);
            if (location == null) {
                return;
            }
            String player = (String) values[1];
            PlayerKillLogger.log(preparedStmt, batchCount, user, location, player);
        }
    }

}
