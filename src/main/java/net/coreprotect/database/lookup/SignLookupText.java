package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringJoiner;

public final class SignLookupText {
    public static final String HAS_VISIBLE_TEXT = "(((face=0 OR face IS NULL) AND (" + nonemptyLines(1) + ")) OR (face<>0 AND (" + nonemptyLines(5) + ")))";

    private SignLookupText() {
    }

    private static String nonemptyLines(int firstLine) {
        StringJoiner predicate = new StringJoiner(" OR ");
        for (int line = firstLine; line < firstLine + 4; line++) {
            predicate.add("LENGTH(line_" + line + ") > 0");
        }
        return predicate.toString();
    }

    public static String read(ResultSet results, boolean front) throws SQLException {
        StringBuilder message = new StringBuilder();
        int firstLine = front ? 1 : 5;
        for (int line = firstLine; line < firstLine + 4; line++) {
            String value = results.getString("line_" + line);
            if (value != null && !value.isEmpty()) {
                message.append(value);
                if (!value.endsWith(" ")) {
                    message.append(' ');
                }
            }
        }
        return message.toString();
    }
}
