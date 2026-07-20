package net.coreprotect.utility;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public final class HopperTransactionUtils {

    private static final long APPLY_ALL_MARK = -1L;

    private static final ConcurrentHashMap<String, PendingTransaction> pendingTransactions = new ConcurrentHashMap<>();

    private HopperTransactionUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String getTransactionId(Location location) {
        return location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
    }

    public static String getLoggingId(String user, Location location) {
        return getLoggingId(user, getLoggingIdSuffix(location));
    }

    public static String getLoggingId(String user, String locationSuffix) {
        return user.toLowerCase(Locale.ROOT) + locationSuffix;
    }

    public static String getLoggingIdSuffix(Location location) {
        return "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ() + "." + location.getWorld().getUID().toString();
    }

    public static String getHopperPushId(Location location) {
        return "#hopper-push" + getLoggingIdSuffix(location);
    }

    public static String getHopperPullId(Location location) {
        return "#hopper-pull" + getLoggingIdSuffix(location);
    }

    public static boolean hasTransaction(String transactionId) {
        return pendingTransactions.containsKey(transactionId);
    }

    public static int pendingDeltaCount(String transactionId) {
        PendingTransaction transaction = pendingTransactions.get(transactionId);
        if (transaction == null) {
            return -1;
        }

        synchronized (transaction) {
            return transaction.deltas.size();
        }
    }

    public static void registerSnapshot(String transactionId, String loggingId, boolean fresh) {
        PendingTransaction transaction = pendingTransactions.computeIfAbsent(transactionId, k -> new PendingTransaction());
        synchronized (transaction) {
            Deque<Long> marks = transaction.ownerMarks.computeIfAbsent(loggingId, k -> new ArrayDeque<>());
            if (fresh) {
                marks.clear();
            }

            marks.addLast(transaction.nextSeq);
            transaction.lastMarkSeq = transaction.nextSeq;
        }
    }

    public static long peekSnapshotMark(String transactionId, String loggingId) {
        return getSnapshotMark(transactionId, loggingId, 0);
    }

    public static long getSnapshotMark(String transactionId, String loggingId, int index) {
        PendingTransaction transaction = pendingTransactions.get(transactionId);
        if (transaction == null) {
            return APPLY_ALL_MARK;
        }

        synchronized (transaction) {
            Deque<Long> marks = transaction.ownerMarks.get(loggingId);
            if (marks == null || marks.isEmpty()) {
                return APPLY_ALL_MARK;
            }

            if (index > 0 && index < marks.size()) {
                int position = 0;
                for (long mark : marks) {
                    if (position == index) {
                        return mark;
                    }
                    position++;
                }
            }

            return marks.peekFirst();
        }
    }

    public static void consumeSnapshot(String transactionId, String loggingId) {
        PendingTransaction transaction = pendingTransactions.get(transactionId);
        if (transaction == null) {
            return;
        }

        synchronized (transaction) {
            Deque<Long> marks = transaction.ownerMarks.get(loggingId);
            if (marks != null) {
                marks.pollFirst();
            }

            pruneDeltas(transaction);
        }
    }

    public static void removeOwner(String transactionId, String loggingId) {
        PendingTransaction transaction = pendingTransactions.get(transactionId);
        if (transaction == null) {
            return;
        }

        synchronized (transaction) {
            transaction.ownerMarks.remove(loggingId);
            transaction.lastBatchItems.remove(loggingId);
            if (transaction.ownerMarks.isEmpty()) {
                pendingTransactions.remove(transactionId);
                return;
            }

            pruneDeltas(transaction);
        }
    }

    public static void recordItemAdded(String transactionId, ItemStack item) {
        recordDelta(transactionId, item, false, item == null ? 0 : item.getAmount());
    }

    public static void recordItemRemoved(String transactionId, ItemStack item) {
        recordDelta(transactionId, item, true, item == null ? 0 : item.getAmount());
    }

    public static boolean shouldForceBatchBoundary(String transactionId, String loggingId, ItemStack item) {
        if (item == null || item.getAmount() <= 0) {
            return false;
        }

        PendingTransaction transaction = pendingTransactions.computeIfAbsent(transactionId, k -> new PendingTransaction());
        synchronized (transaction) {
            ItemStack previous = transaction.lastBatchItems.put(loggingId, item.clone());
            Deque<Long> marks = transaction.ownerMarks.get(loggingId);
            return previous != null && !previous.isSimilar(item) && marks != null && !marks.isEmpty();
        }
    }

    public static Object[] createAbortState(Object[] previous, ItemStack[] destinationContents, ItemStack movedItem) {
        Set<ItemStack> movedItems = new HashSet<>();
        if (previous != null && previous.length >= 2 && previous[0] instanceof Set && previous[1] instanceof ItemStack[] && Arrays.equals(destinationContents, (ItemStack[]) previous[1])) {
            for (Object item : (Set<?>) previous[0]) {
                if (item instanceof ItemStack) {
                    movedItems.add((ItemStack) item);
                }
            }
        }
        if (movedItem != null) {
            movedItems.add(movedItem.clone());
        }

        return new Object[] { movedItems, ItemUtils.getContainerState(destinationContents) };
    }

    public static ItemStack[] applyPendingChanges(ItemStack[] containerState, String transactionId, long sinceMark) {
        if (containerState == null) {
            return null;
        }

        PendingTransaction transaction = pendingTransactions.get(transactionId);
        if (transaction == null) {
            return containerState;
        }

        List<Delta> pending = new ArrayList<>();
        synchronized (transaction) {
            for (Delta delta : transaction.deltas) {
                if (delta.seq > sinceMark && delta.amount > 0) {
                    pending.add(new Delta(delta.item.clone(), delta.addBack, delta.amount, delta.seq));
                }
            }
        }

        if (pending.isEmpty()) {
            return containerState;
        }

        int addBackCount = 0;
        for (Delta delta : pending) {
            if (delta.addBack) {
                addBackCount++;
            }
        }

        ItemStack[] result = new ItemStack[containerState.length + addBackCount];
        int count = 0;
        for (ItemStack item : containerState) {
            result[count] = item;
            count++;
        }

        for (Delta delta : pending) {
            ItemStack item = delta.item;
            item.setAmount(delta.amount);

            if (delta.addBack) {
                result[count] = item;
                count++;
            }
            else {
                removeSimilarItems(result, item);
            }
        }

        return result;
    }

    private static void recordDelta(String transactionId, ItemStack item, boolean addBack, int amount) {
        if (item == null || amount <= 0 || item.getAmount() <= 0) {
            return;
        }

        PendingTransaction transaction = pendingTransactions.get(transactionId);
        if (transaction == null) {
            return;
        }

        synchronized (transaction) {
            transaction.nextSeq++;

            Delta tail = transaction.deltas.peekLast();
            if (tail != null && tail.addBack == addBack && tail.seq > transaction.lastMarkSeq && tail.item.isSimilar(item)) {
                long combinedAmount = (long) tail.amount + amount;
                if (combinedAmount <= Integer.MAX_VALUE) {
                    tail.amount = (int) combinedAmount;
                    return;
                }

                tail.amount = Integer.MAX_VALUE;
                amount = (int) (combinedAmount - Integer.MAX_VALUE);
            }

            transaction.deltas.addLast(new Delta(item.clone(), addBack, amount, transaction.nextSeq));
        }
    }

    private static void pruneDeltas(PendingTransaction transaction) {
        long minMark = Long.MAX_VALUE;
        boolean hasMark = false;
        for (Deque<Long> marks : transaction.ownerMarks.values()) {
            Long first = marks.peekFirst();
            if (first != null && first < minMark) {
                minMark = first;
                hasMark = true;
            }
        }

        if (!hasMark) {
            transaction.deltas.clear();
            return;
        }

        Delta head = transaction.deltas.peekFirst();
        while (head != null && head.seq <= minMark) {
            transaction.deltas.pollFirst();
            head = transaction.deltas.peekFirst();
        }
    }

    private static void removeSimilarItems(ItemStack[] items, ItemStack item) {
        int remaining = item.getAmount();
        for (int i = 0; i < items.length; i++) {
            if (remaining <= 0) {
                return;
            }
            ItemStack check = items[i];
            if (check == null || check.getAmount() <= 0 || !check.isSimilar(item)) {
                continue;
            }

            int removed = Math.min(remaining, check.getAmount());
            ItemStack updated = check.clone();
            updated.setAmount(check.getAmount() - removed);
            items[i] = updated;
            remaining -= removed;
        }
    }

    private static final class PendingTransaction {
        private long nextSeq;
        private long lastMarkSeq;
        private final Deque<Delta> deltas = new ArrayDeque<>();
        private final Map<String, Deque<Long>> ownerMarks = new HashMap<>();
        private final Map<String, ItemStack> lastBatchItems = new HashMap<>();
    }

    private static final class Delta {
        private final ItemStack item;
        private final boolean addBack;
        private final long seq;
        private int amount;

        private Delta(ItemStack item, boolean addBack, int amount, long seq) {
            this.item = item;
            this.addBack = addBack;
            this.amount = amount;
            this.seq = seq;
        }
    }
}
