package net.coreprotect.database.statement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.database.LocationQuery;
import net.coreprotect.model.SignState;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.model.rollback.SignRollbackChange;
import net.coreprotect.utility.ErrorReporter;

public class SignStatement {

    private SignStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(ConsumerWriteBatch batch, int batchCount, int time, int id, int wid, int x, int y, int z, int action, int color, int colorSecondary, int data, int waxed, int face, String line1, String line2, String line3, String line4, String line5, String line6, String line7, String line8) {
        try {
            batch.addSign(batchCount, time, id, wid, x, y, z, action, color, colorSecondary, data, waxed, face, new String[] { line1, line2, line3, line4, line5, line6, line7, line8 });
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

    public static void getData(Statement statement, BlockState block, String query) {
        try {
            if (!(block instanceof Sign)) {
                return;
            }

            try (ResultSet resultSet = statement.executeQuery(query)) {
                while (resultSet.next()) {
                    SignState.read(resultSet).apply((Sign) block);
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static void loadRollbackStates(Statement statement, List<Object[]> rows) throws SQLException {
        for (int start = 0; start < rows.size(); start += 64) {
            int end = Math.min(start + 64, rows.size());
            Map<Long, Object[]> selected = new HashMap<>();
            StringBuilder query = new StringBuilder();
            for (int index = start; index < end; index++) {
                Object[] row = rows.get(index);
                long id = ((Number) row[0]).longValue();
                selected.put(id, row);
                if (query.length() > 0) {
                    query.append(" UNION ALL ");
                }
                query.append("SELECT previous_sign.*, ").append(id).append(" AS after_id FROM (SELECT rowid, time, ")
                        .append(ConfigHandler.databaseType.getUserColumn()).append(" AS actor, action, face, color, color_secondary, data, waxed, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8 FROM ")
                        .append(ConfigHandler.prefix).append("sign WHERE ").append(LocationQuery.predicate("wid", "=" + row[10]))
                        .append(" AND ").append(LocationQuery.predicate("x", "=" + row[3])).append(" AND y=").append(row[4])
                        .append(" AND ").append(LocationQuery.predicate("z", "=" + row[5]))
                        .append(" AND rowid<").append(id).append(" ORDER BY rowid DESC LIMIT 1) previous_sign");
            }
            try (ResultSet result = statement.executeQuery(query.toString())) {
                while (result.next()) {
                    Object[] row = selected.get(result.getLong("after_id"));
                    SignRollbackChange change = (SignRollbackChange) row[14];
                    int action = result.getInt("action");
                    int elapsed = (Integer) row[1] - result.getInt("time");
                    if ((action == SignActions.BEFORE || action == SignActions.BREAK && elapsed == 1)
                            && result.getInt("actor") == (Integer) row[2] && (result.getInt("face") == 0) == change.isFront()) {
                        change.setBefore(SignState.read(result));
                    }
                }
            }
        }
    }
}
