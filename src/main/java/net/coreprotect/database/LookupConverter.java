package net.coreprotect.database;

import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.BlockUtils;

public class LookupConverter {

    public static List<String[]> convertRawLookup(Statement statement, List<Object[]> list) {
        List<String[]> newList = new ArrayList<>();

        if (list == null) {
            return null;
        }

        for (Object[] map : list) {
            int newLength = map.length - 1;
            String[] results = new String[newLength];

            for (int i = 0; i < map.length; i++) {
                try {
                    int newId = i - 1;
                    if (i == 2) {
                        if (map[i] instanceof Integer) {
                            int userId = (Integer) map[i];
                            if (ConfigHandler.playerIdCacheReversed.get(userId) == null) {
                                UserStatement.loadName(statement.getConnection(), userId);
                            }
                            String userResult = ConfigHandler.playerIdCacheReversed.get(userId);
                            results[newId] = userResult;
                        }
                        else {
                            results[newId] = (String) map[i];
                        }
                    }
                    else if (i == 13 && map[i] instanceof byte[]) {
                        results[newId] = BlockUtils.byteDataToString((byte[]) map[i], (int) map[6]);
                    }
                    else if (i > 0) { // skip rowid
                        if (map[i] instanceof Integer) {
                            results[newId] = map[i].toString();
                        }
                        else if (map[i] instanceof String) {
                            results[newId] = (String) map[i];
                        }
                        else if (map[i] instanceof byte[]) {
                            results[newId] = new String((byte[]) map[i], StandardCharsets.ISO_8859_1);
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            newList.add(results);
        }

        return newList;
    }
}
