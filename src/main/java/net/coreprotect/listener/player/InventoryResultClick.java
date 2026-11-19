package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;

final class InventoryResultClick {

    static InventoryResultClick capture(InventoryClickEvent event, Player player, Inventory inventory, Supplier<ItemStack[]> inputs,
            boolean crafting, boolean trading) {
        if (event.isCancelled() || event.getSlotType() != SlotType.RESULT || event.getAction() == InventoryAction.NOTHING) {
            return null;
        }

        ItemStack result = event.getCurrentItem();
        if (isEmpty(result) || !canTakeResult(event, player.getInventory(), result)) {
            return null;
        }

        Location location = trading ? player.getLocation() : inventory.getLocation();
        if (location == null) {
            location = player.getLocation();
        }
        if (location == null || location.getWorld() == null || !Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return null;
        }

        ItemStack[] before = ItemUtils.getContainerState(inputs.get());
        if (before == null) {
            return null;
        }
        return new InventoryResultClick(player, location.clone(), inputs, before, result.clone(), crafting, trading);
    }

    private static boolean canTakeResult(InventoryClickEvent event, PlayerInventory inventory, ItemStack result) {
        switch (event.getClick()) {
            case LEFT:
            case RIGHT:
                ItemStack cursor = event.getCursor();
                return isEmpty(cursor) || cursor.isSimilar(result) && (long) cursor.getAmount() + result.getAmount() <= cursor.getMaxStackSize();
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                return event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;
            case NUMBER_KEY:
                int slot = event.getHotbarButton();
                return slot >= 0 && slot < 9 && isEmpty(inventory.getItem(slot));
            case SWAP_OFFHAND:
                return isEmpty(inventory.getItemInOffHand());
            case DROP:
            case CONTROL_DROP:
                return isEmpty(event.getCursor());
            default:
                return false;
        }
    }

    static boolean isEmpty(ItemStack stack) {
        return stack == null || BlockUtils.isAir(stack.getType()) || stack.getAmount() <= 0;
    }

    private static int matchingAmount(ItemStack[] inventory, ItemStack stack) {
        int amount = 0;
        for (ItemStack item : inventory) {
            if (!isEmpty(item) && item.isSimilar(stack)) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    private static ItemStack[] playerContents(Player player) {
        ItemStack[] inventory = player.getInventory().getContents();
        ItemStack[] contents = new ItemStack[inventory.length + 1];
        System.arraycopy(inventory, 0, contents, 0, inventory.length);
        contents[inventory.length] = player.getItemOnCursor();
        return contents;
    }

    private static ItemStack quantity(ItemStack stack, int amount) {
        ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
    }

    static int queueTime() {
        return (int) (System.currentTimeMillis() / 1000L) + 1;
    }

    private final Player player;
    private final String user;
    private final Location location;
    private final Supplier<ItemStack[]> readInputs;
    private final ItemStack[] beforeInputs;
    private final ItemStack[] beforePlayer;
    private final ItemStack result;
    private final boolean crafting;
    private final boolean trading;
    private int droppedResult;

    private InventoryResultClick(Player player, Location location, Supplier<ItemStack[]> readInputs, ItemStack[] beforeInputs, ItemStack result,
            boolean crafting, boolean trading) {
        this.player = player;
        this.user = player.getName();
        this.location = location;
        this.readInputs = readInputs;
        this.beforeInputs = beforeInputs;
        this.beforePlayer = ItemUtils.getContainerState(playerContents(player));
        this.result = result;
        this.crafting = crafting;
        this.trading = trading;
    }

    void observeDrop(ItemStack dropped) {
        if (!isEmpty(dropped) && dropped.isSimilar(result)) {
            droppedResult += dropped.getAmount();
        }
    }

    void settle() {
        ItemStack[] afterInputs = readInputs.get();
        if (afterInputs == null || afterInputs.length != beforeInputs.length) {
            return;
        }

        List<ItemStack> consumed = new ArrayList<>();
        List<ItemStack> produced = new ArrayList<>();
        for (int slot = 0; slot < beforeInputs.length; slot++) {
            ItemStack before = beforeInputs[slot];
            ItemStack after = afterInputs[slot];
            int beforeAmount = isEmpty(before) ? 0 : before.getAmount();
            int afterAmount = isEmpty(after) ? 0 : after.getAmount();
            int unchanged = beforeAmount > 0 && afterAmount > 0 && before.isSimilar(after) ? Math.min(beforeAmount, afterAmount) : 0;
            if (beforeAmount > unchanged) {
                consumed.add(quantity(before, beforeAmount - unchanged));
            }
            if (crafting && afterAmount > unchanged) {
                produced.add(quantity(after, afterAmount - unchanged));
            }
        }
        if (consumed.isEmpty()) {
            return;
        }

        ItemStack[] afterPlayer = playerContents(player);
        int received = matchingAmount(afterPlayer, result) - matchingAmount(beforePlayer, result) + droppedResult;
        if (received <= 0) {
            return;
        }
        if (crafting) {
            addStoredRemainders(consumed, produced, afterPlayer);
        }
        produced.add(quantity(result, received));
        Queue.queuePendingItems(user, location, queueTime(), 0, Map.of(
                trading ? ItemLogger.ITEM_SELL : ItemLogger.ITEM_DESTROY, consumed,
                trading ? ItemLogger.ITEM_BUY : ItemLogger.ITEM_CREATE, produced));
    }

    private void addStoredRemainders(List<ItemStack> consumed, List<ItemStack> produced, ItemStack[] afterPlayer) {
        List<ItemStack> gained = new ArrayList<>();
        List<ItemStack> compared = new ArrayList<>();
        for (ItemStack item : afterPlayer) {
            if (isEmpty(item) || result.isSimilar(item) || compared.stream().anyMatch(item::isSimilar)) {
                continue;
            }
            compared.add(item);
            int amount = matchingAmount(afterPlayer, item) - matchingAmount(beforePlayer, item);
            if (amount > 0) {
                gained.add(quantity(item, amount));
            }
        }
        if (gained.isEmpty()) {
            return;
        }

        Map<Material, Integer> expected = new EnumMap<>(Material.class);
        for (ItemStack item : consumed) {
            Material remainder = item.getType().getCraftingRemainingItem();
            if (remainder != null && !BlockUtils.isAir(remainder)) {
                expected.merge(remainder, item.getAmount(), Integer::sum);
            }
        }
        for (ItemStack item : produced) {
            expected.computeIfPresent(item.getType(), (type, count) -> count - item.getAmount());
        }

        for (ItemStack item : gained) {
            int amount = Math.min(item.getAmount(), expected.getOrDefault(item.getType(), 0));
            if (amount > 0) {
                produced.add(quantity(item, amount));
                expected.merge(item.getType(), -amount, Integer::sum);
            }
        }
    }
}
