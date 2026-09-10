package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

final class HopperPullAccumulator {

    private final List<MoveRun> runs = new ArrayList<>();
    private long moveCount;

    void add(String user, ItemStack item) {
        if (user == null || user.isEmpty() || item == null || item.getAmount() <= 0) {
            return;
        }

        int amount = item.getAmount();
        MoveRun tail = runs.isEmpty() ? null : runs.get(runs.size() - 1);
        if (tail != null && tail.user.equals(user) && tail.amount == amount && tail.item.isSimilar(item)) {
            tail.repetitions++;
        }
        else {
            ItemStack prototype = item.clone();
            prototype.setAmount(amount);
            runs.add(new MoveRun(user, prototype, amount));
        }
        moveCount++;
    }

    boolean isEmpty() {
        return runs.isEmpty();
    }

    int runCount() {
        return runs.size();
    }

    long moveCount() {
        return moveCount;
    }

    void replay(ItemStack[] checkpoint, int inventoryMaxStackSize, MoveConsumer consumer) {
        if (checkpoint == null || consumer == null || runs.isEmpty()) {
            return;
        }

        ItemStack[] state = checkpoint.clone();
        for (int i = runs.size() - 1; i >= 0; i--) {
            MoveRun run = runs.get(i);
            state = addToState(state, run.item, run.totalAmount(), inventoryMaxStackSize);
        }

        for (MoveRun run : runs) {
            for (long repetition = 0; repetition < run.repetitions; repetition++) {
                ItemStack movedItem = run.item.clone();
                movedItem.setAmount(run.amount);
                consumer.accept(run.user, state, movedItem);
                state = removeFromState(state, movedItem);
            }
        }
    }

    private static ItemStack[] addToState(ItemStack[] state, ItemStack item, long amount, int inventoryMaxStackSize) {
        ItemStack[] result = state.clone();
        long remaining = amount;
        int maxStackSize = maxStackSize(item, inventoryMaxStackSize);

        for (int i = 0; i < result.length && remaining > 0; i++) {
            ItemStack existing = result[i];
            if (existing == null || existing.getAmount() <= 0 || !existing.isSimilar(item) || existing.getAmount() >= maxStackSize) {
                continue;
            }

            int added = (int) Math.min(remaining, maxStackSize - existing.getAmount());
            ItemStack updated = existing.clone();
            updated.setAmount(existing.getAmount() + added);
            result[i] = updated;
            remaining -= added;
        }

        for (int i = 0; i < result.length && remaining > 0; i++) {
            ItemStack existing = result[i];
            if (existing != null && existing.getAmount() > 0) {
                continue;
            }

            int added = (int) Math.min(remaining, maxStackSize);
            ItemStack inserted = item.clone();
            inserted.setAmount(added);
            result[i] = inserted;
            remaining -= added;
        }

        if (remaining <= 0) {
            return result;
        }

        long additionalSlots = (remaining + maxStackSize - 1L) / maxStackSize;
        if (additionalSlots > Integer.MAX_VALUE - result.length) {
            throw new IllegalStateException("Hopper pull reconstruction exceeds maximum inventory size");
        }

        ItemStack[] expanded = new ItemStack[result.length + (int) additionalSlots];
        System.arraycopy(result, 0, expanded, 0, result.length);
        int slot = result.length;
        while (remaining > 0) {
            int added = (int) Math.min(remaining, maxStackSize);
            ItemStack inserted = item.clone();
            inserted.setAmount(added);
            expanded[slot] = inserted;
            slot++;
            remaining -= added;
        }
        return expanded;
    }

    private static ItemStack[] removeFromState(ItemStack[] state, ItemStack item) {
        ItemStack[] result = state.clone();
        int remaining = item.getAmount();
        for (int i = 0; i < result.length && remaining > 0; i++) {
            ItemStack existing = result[i];
            if (existing == null || existing.getAmount() <= 0 || !existing.isSimilar(item)) {
                continue;
            }

            int removed = Math.min(remaining, existing.getAmount());
            if (removed == existing.getAmount()) {
                result[i] = null;
            }
            else {
                ItemStack updated = existing.clone();
                updated.setAmount(existing.getAmount() - removed);
                result[i] = updated;
            }
            remaining -= removed;
        }
        return result;
    }

    private static int maxStackSize(ItemStack item, int inventoryMaxStackSize) {
        int maxStackSize = item.getMaxStackSize();
        if (inventoryMaxStackSize > 0 && (inventoryMaxStackSize < maxStackSize || maxStackSize < 1)) {
            maxStackSize = inventoryMaxStackSize;
        }
        return Math.max(1, maxStackSize);
    }

    @FunctionalInterface
    interface MoveConsumer {
        void accept(String user, ItemStack[] sourceBefore, ItemStack movedItem);
    }

    private static final class MoveRun {
        private final String user;
        private final ItemStack item;
        private final int amount;
        private long repetitions = 1;

        private MoveRun(String user, ItemStack item, int amount) {
            this.user = user;
            this.item = item;
            this.amount = amount;
        }

        private long totalAmount() {
            try {
                return Math.multiplyExact((long) amount, repetitions);
            }
            catch (ArithmeticException e) {
                throw new IllegalStateException("Hopper pull amount overflow", e);
            }
        }
    }
}
