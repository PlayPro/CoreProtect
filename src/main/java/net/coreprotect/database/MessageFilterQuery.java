package net.coreprotect.database;

import java.util.ArrayList;
import java.util.List;

import net.coreprotect.config.ConfigHandler;

/** Shared message prefix predicates for command and typed API lookups. */
public final class MessageFilterQuery {
    private MessageFilterQuery() {
    }

    public static String append(String baseQuery, List<String> messageFilters, String table, List<String> bindings) {
        if (messageFilters == null || messageFilters.isEmpty()) {
            return baseQuery;
        }

        List<String> included = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        for (String filter : messageFilters) {
            if (filter != null && filter.startsWith("-")) {
                excluded.add(filter.substring(1));
            }
            else {
                included.add(filter);
            }
        }

        boolean sign = table.equals("sign");
        String query = sign ? appendSignMessagePrefixes(baseQuery, included, bindings) : appendMessagePrefixes(baseQuery, included, table, bindings);
        return appendMessageExclusions(query, excluded, sign, bindings);
    }

    private static String appendMessagePrefixes(String baseQuery, List<String> messageFilters, String table, List<String> bindings) {
        if (messageFilters.isEmpty()) {
            return baseQuery;
        }

        if (ConfigHandler.databaseType.isDuckDB()) {
            StringBuilder query = new StringBuilder(baseQuery).append(" AND (");
            for (int index = 0; index < messageFilters.size(); index++) {
                if (index > 0) {
                    query.append(" OR ");
                }
                query.append("message ILIKE ? ESCAPE '~'");
                String filter = messageFilters.get(index) == null ? "" : messageFilters.get(index);
                bindings.add(escapeLike(filter) + "%");
            }
            return query.append(')').toString();
        }

        String alias = table + "FilterRows";
        String likeOperator = ConfigHandler.databaseType.isColumnar() ? " ILIKE " : " LIKE ";
        String escapeClause = ConfigHandler.databaseType.isClickHouse() ? "" : " ESCAPE '~'";
        StringBuilder query = new StringBuilder(baseQuery)
                .append(" AND rowid IN (SELECT ").append(alias).append(".rowid FROM ")
                .append(ConfigHandler.prefix).append(table).append(" ").append(alias).append(" WHERE (");
        for (int index = 0; index < messageFilters.size(); index++) {
            if (index > 0) {
                query.append(" OR ");
            }

            String prefixExpression = messagePrefix(alias + ".message");
            query.append("(").append(prefixExpression).append(likeOperator).append("?").append(escapeClause).append(" AND ")
                    .append(alias).append(".message").append(likeOperator).append("?").append(escapeClause).append(")");

            String filter = messageFilters.get(index) == null ? "" : messageFilters.get(index);
            bindings.add(escapeLike(firstCodePoints(filter, 16)) + "%");
            bindings.add(escapeLike(filter) + "%");
        }
        return query.append("))").toString();
    }

    private static String appendSignMessagePrefixes(String baseQuery, List<String> messageFilters, List<String> bindings) {
        if (messageFilters.isEmpty()) {
            return baseQuery;
        }

        if (ConfigHandler.databaseType.isDuckDB()) {
            StringBuilder query = new StringBuilder(baseQuery).append(" AND (");
            for (int filterIndex = 0; filterIndex < messageFilters.size(); filterIndex++) {
                if (filterIndex > 0) {
                    query.append(" OR ");
                }
                query.append("((face=0 AND (");
                appendDuckDBSignLines(query, 1, 4);
                query.append(")) OR (face<>0 AND (");
                appendDuckDBSignLines(query, 5, 8);
                query.append(")))");

                String filter = messageFilters.get(filterIndex) == null ? "" : messageFilters.get(filterIndex);
                String message = escapeLike(filter) + "%";
                for (int line = 1; line <= 8; line++) {
                    bindings.add(message);
                }
            }
            return query.append(')').toString();
        }

        String alias = "signFilterRows";
        String likeOperator = ConfigHandler.databaseType.isColumnar() ? " ILIKE " : " LIKE ";
        String escapeClause = ConfigHandler.databaseType.isClickHouse() ? "" : " ESCAPE '~'";
        StringBuilder query = new StringBuilder(baseQuery).append(" AND rowid IN (");
        boolean union = false;
        for (String filter : messageFilters) {
            String prefix = escapeLike(firstCodePoints(filter, 16)) + "%";
            String message = escapeLike(filter) + "%";
            for (int line = 1; line <= 8; line++) {
                if (union) {
                    query.append(" UNION ALL ");
                }

                String column = "line_" + line;
                String prefixExpression = messagePrefix(alias + "." + column);
                query.append("SELECT ").append(alias).append(".rowid FROM ")
                        .append(ConfigHandler.prefix).append("sign ").append(alias)
                        .append(" WHERE ").append(alias).append(line <= 4 ? ".face = 0" : ".face <> 0")
                        .append(" AND (").append(prefixExpression).append(likeOperator).append("?").append(escapeClause).append(" AND ")
                        .append(alias).append(".").append(column).append(likeOperator).append("?").append(escapeClause).append(")");
                bindings.add(prefix);
                bindings.add(message);
                union = true;
            }
        }
        return query.append(")").toString();
    }

    private static String appendMessageExclusions(String baseQuery, List<String> excluded, boolean sign, List<String> bindings) {
        if (excluded.isEmpty()) {
            return baseQuery;
        }

        String match = (ConfigHandler.databaseType.isColumnar() ? " NOT ILIKE ?" : " NOT LIKE ?")
                + (ConfigHandler.databaseType.isClickHouse() ? "" : " ESCAPE '~'");
        StringBuilder query = new StringBuilder(baseQuery);
        for (String filter : excluded) {
            String pattern = escapeLike(filter) + "%";
            if (sign) {
                query.append(" AND (face IS NULL OR (face=0 AND (");
                appendExcludedSignLines(query, 1, 4, match);
                query.append(")) OR (face<>0 AND (");
                appendExcludedSignLines(query, 5, 8, match);
                query.append(")))");
                for (int line = 1; line <= 8; line++) {
                    bindings.add(pattern);
                }
            }
            else {
                query.append(" AND (message IS NULL OR message").append(match).append(')');
                bindings.add(pattern);
            }
        }
        return query.toString();
    }

    private static void appendExcludedSignLines(StringBuilder query, int firstLine, int lastLine, String match) {
        for (int line = firstLine; line <= lastLine; line++) {
            if (line > firstLine) {
                query.append(" AND ");
            }
            query.append("(line_").append(line).append(" IS NULL OR line_").append(line).append(match).append(')');
        }
    }

    private static void appendDuckDBSignLines(StringBuilder query, int firstLine, int lastLine) {
        for (int line = firstLine; line <= lastLine; line++) {
            if (line > firstLine) {
                query.append(" OR ");
            }
            query.append("line_").append(line).append(" ILIKE ? ESCAPE '~'");
        }
    }

    private static String firstCodePoints(String value, int maximum) {
        if (value == null) {
            return "";
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= maximum) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximum));
    }

    private static String messagePrefix(String column) {
        if (ConfigHandler.databaseType.isMySQL()) {
            return column;
        }
        if (ConfigHandler.databaseType.isClickHouse()) {
            return "substringUTF8(" + column + ",1,16)";
        }
        return "substr(" + column + ",1,16)";
    }

    private static String escapeLike(String value) {
        if (ConfigHandler.databaseType.isClickHouse()) {
            return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        }
        return value.replace("~", "~~").replace("%", "~%").replace("_", "~_");
    }

}
