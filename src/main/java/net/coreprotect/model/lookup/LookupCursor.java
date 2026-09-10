package net.coreprotect.model.lookup;

public final class LookupCursor {

    private final int nextPage;
    private final int pageSize;
    private final long time;
    private final int source;
    private final long rowId;
    private final boolean orderByTime;

    public LookupCursor(int nextPage, int pageSize, long time, int source, long rowId, boolean orderByTime) {
        this.nextPage = nextPage;
        this.pageSize = pageSize;
        this.time = time;
        this.source = source;
        this.rowId = rowId;
        this.orderByTime = orderByTime;
    }

    public int getNextPage() {
        return nextPage;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTime() {
        return time;
    }

    public int getSource() {
        return source;
    }

    public long getRowId() {
        return rowId;
    }

    public boolean isOrderByTime() {
        return orderByTime;
    }
}
