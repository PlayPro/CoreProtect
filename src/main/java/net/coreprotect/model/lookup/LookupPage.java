package net.coreprotect.model.lookup;

import java.util.Collections;
import java.util.List;

public final class LookupPage {

    private final long totalRows;
    private final List<String[]> rows;
    private final LookupCursor nextCursor;

    public LookupPage(long totalRows, List<String[]> rows) {
        this(totalRows, rows, null);
    }

    public LookupPage(long totalRows, List<String[]> rows, LookupCursor nextCursor) {
        this.totalRows = totalRows;
        this.rows = Collections.unmodifiableList(rows);
        this.nextCursor = nextCursor;
    }

    public long getTotalRows() {
        return totalRows;
    }

    public List<String[]> getRows() {
        return rows;
    }

    public LookupCursor getNextCursor() {
        return nextCursor;
    }
}
