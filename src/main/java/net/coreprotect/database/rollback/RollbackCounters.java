package net.coreprotect.database.rollback;

public final class RollbackCounters {

    private int items;
    private int blocks;
    private int entities;

    public void addItems(int amount) {
        items += amount;
    }

    public void addBlocks(int amount) {
        blocks += amount;
    }

    public void addEntities(int amount) {
        entities += amount;
    }

    public int getItems() {
        return items;
    }

    public int getBlocks() {
        return blocks;
    }

    public int getEntities() {
        return entities;
    }
}
