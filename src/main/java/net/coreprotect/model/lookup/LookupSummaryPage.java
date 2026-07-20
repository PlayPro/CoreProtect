package net.coreprotect.model.lookup;

import java.util.Collections;
import java.util.List;

public final class LookupSummaryPage {
    private final long totalRows;
    private final List<LookupSummaryRow> rows;

    public LookupSummaryPage(long totalRows, List<LookupSummaryRow> rows) {
        this.totalRows = totalRows;
        this.rows = Collections.unmodifiableList(rows);
    }

    public long getTotalRows() {
        return totalRows;
    }

    public List<LookupSummaryRow> getRows() {
        return rows;
    }
}
