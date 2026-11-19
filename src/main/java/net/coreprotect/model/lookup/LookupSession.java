package net.coreprotect.model.lookup;

public final class LookupSession {
    public static final int STANDARD_LOOKUP = 5;

    public final StandardLookupRequest request;
    private final Long[] rowCounts;
    public final LookupCursor cursor;

    public LookupSession(StandardLookupRequest request, Long[] rowCounts, LookupCursor cursor) {
        this.request = request;
        this.rowCounts = rowCounts.clone();
        this.cursor = cursor;
    }

    public Long[] rowCounts() {
        return rowCounts.clone();
    }
}
