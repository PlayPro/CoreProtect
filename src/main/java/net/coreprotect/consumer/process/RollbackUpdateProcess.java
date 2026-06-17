package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.data.lookup.type.CommonLookupData;
import net.coreprotect.model.rollback.RollbackUpdateTargets;
import net.coreprotect.utility.MaterialUtils;

class RollbackUpdateProcess {

    @SuppressWarnings("unchecked")
    static void process(Statement statement, int processId, int id, int action, int table) {
        final Map<Integer, Object> updateLists = Consumer.consumerObjects.get(processId);
        final List<CommonLookupData> list = (List<CommonLookupData>) updateLists.remove(id);

        if (list != null && !list.isEmpty()) {
            // adapted from Database#performUpdate
            String tableName;
            String rows;
            if (table == 1 || table == 3) { // the numbers mason
                tableName = "container";
                rows = "rowid,time,user,wid,x,y,z,type,data,action,rolled_back,version,amount,metadata";
            } else if (table == 2) {
                tableName = "item";
                rows = "rowid,time,user,wid,x,y,z,type,data,action,rolled_back,version,amount";
            } else {
                tableName = "block";
                rows = "rowid,time,user,wid,x,y,z,type,data,action,rolled_back,version,meta,blockdata";
            }

            String values = String.join(",", Collections.nCopies(rows.split(",").length, "?"));

            tableName = ConfigHandler.prefix + tableName;

            // Re-insert the same row with an incremented version
            try (PreparedStatement preparedStatement = statement.getConnection().prepareStatement("INSERT INTO " + tableName + " (" + rows + ") VALUES (" + values + ") ")) {
                long batchCount = 0;

                for (CommonLookupData listRow : list) {
                    int rb = listRow.rolledBack();
                    if (MaterialUtils.rolledBack(rb, RollbackUpdateTargets.usesInventoryRollbackState(table)) == action) { // 1 = restore, 0 = rollback
                        int rolledBack = MaterialUtils.toggleRolledBack(rb, RollbackUpdateTargets.usesInventoryRollbackState(table)); // co_item, co_container, co_block

                        preparedStatement.setLong(1, listRow.rowId());
                        preparedStatement.setLong(2, listRow.time());
                        preparedStatement.setInt(3, listRow.userId());
                        preparedStatement.setInt(4, listRow.worldId());
                        preparedStatement.setInt(5, listRow.x());
                        preparedStatement.setInt(6, listRow.y());
                        preparedStatement.setInt(7, listRow.z());
                        preparedStatement.setInt(8, listRow.type());
                        preparedStatement.setInt(9, listRow.data());
                        preparedStatement.setInt(10, listRow.action());
                        preparedStatement.setInt(11, rolledBack);
                        preparedStatement.setInt(12, listRow.version() + 1);

                        if (tableName.endsWith("block")) {
                            preparedStatement.setString(13, listRow.metadata());
                            preparedStatement.setString(14, listRow.blockData());
                        } else if (tableName.endsWith("item") || tableName.endsWith("container")) {
                            preparedStatement.setInt(13, listRow.amount());

                            if (tableName.endsWith("container")) {
                                preparedStatement.setString(14, listRow.metadata());
                            }
                        }

                        preparedStatement.addBatch();

                        if (++batchCount % 10_000 == 0) {
                            preparedStatement.executeBatch();
                        }
                    }
                }

                preparedStatement.executeBatch();
            } catch (SQLException e) {
                CoreProtect.getInstance().getSLF4JLogger().warn("Exception while batch updating rolled_back column for table {}", tableName, e);
            }
        }
    }
}
