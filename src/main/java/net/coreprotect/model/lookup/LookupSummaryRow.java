package net.coreprotect.model.lookup;

public final class LookupSummaryRow {
    private final int userId;
    private final int materialId;
    private final long removedAmount;
    private final long placedAmount;
    private final long netAmount;

    public LookupSummaryRow(int userId, int materialId, long removedAmount, long placedAmount, long netAmount) {
        this.userId = userId;
        this.materialId = materialId;
        this.removedAmount = removedAmount;
        this.placedAmount = placedAmount;
        this.netAmount = netAmount;
    }

    public int getUserId() {
        return userId;
    }

    public int getMaterialId() {
        return materialId;
    }

    public long getRemovedAmount() {
        return removedAmount;
    }

    public long getPlacedAmount() {
        return placedAmount;
    }

    public long getAmount() {
        return netAmount;
    }
}
