package net.coreprotect.model.lookup;

import java.util.Collections;
import java.util.List;

public final class LookupSummaryPage {
    private final long totalRows;
    private final long recordRows;
    private final List<LookupSummaryRow> rows;

    public LookupSummaryPage(long totalRows, List<LookupSummaryRow> rows) {
        this(totalRows, 0L, rows);
    }

    public LookupSummaryPage(long totalRows, long recordRows, List<LookupSummaryRow> rows) {
        this.totalRows = totalRows;
        this.recordRows = recordRows;
        this.rows = Collections.unmodifiableList(rows);
    }

    public long getTotalRows() {
        return totalRows;
    }

    public long getRecordRows() {
        return recordRows;
    }

    public List<LookupSummaryRow> getRows() {
        return rows;
    }
}
