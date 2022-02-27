package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;

public final class CraftItemListener extends Queue implements Listener {

    protected static void playerCraftItem(Location location, String user, ItemStack itemStack, int action) {
        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS || itemStack == null) {
            return;
        }

        String loggingItemId = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        int itemId = getItemId(loggingItemId);

        if (action == ItemLogger.ITEM_CREATE) {
            List<ItemStack> list = ConfigHandler.itemsCreate.getOrDefault(loggingItemId, new ArrayList<>());
            list.add(itemStack);
            ConfigHandler.itemsCreate.put(loggingItemId, list);
        }
        else {
            List<ItemStack> list = ConfigHandler.itemsDestroy.getOrDefault(loggingItemId, new ArrayList<>());
            list.add(itemStack);
            ConfigHandler.itemsDestroy.put(loggingItemId, list);
        }

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queueItemTransaction(user, location.clone(), time, 0, itemId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onCraftItem(CraftItemEvent event) {
        if (event.getResult() == Result.DENY) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (event.getClick() == ClickType.NUMBER_KEY && player.getInventory().getItem(event.getHotbarButton()) != null) {
            return;
        }

        if ((event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) && event.getCursor().getType() != Material.AIR) {
            return;
        }

        CraftingInventory craftingInventory = event.getInventory();
        if (craftingInventory.getResult() == null) {
            return;
        }

        Inventory bottomInventory = event.getView().getBottomInventory();
        if (bottomInventory.getType() != InventoryType.PLAYER) {
            return;
        }

        ItemStack addItem = event.getRecipe().getResult().clone();
        int amount = addItem.getAmount();
        if (amount == 0) {
            return;
        }

        int amountMultiplier = 1;
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            int newMultiplier = Integer.MIN_VALUE;
            for (ItemStack item : craftingInventory.getMatrix()) {
                if (item != null && (newMultiplier == Integer.MIN_VALUE || item.getAmount() < newMultiplier)) {
                    newMultiplier = item.getAmount();
                }
            }
            amountMultiplier = (newMultiplier == Integer.MIN_VALUE ? 1 : newMultiplier);

            int addAmount = amount * amountMultiplier;
            Inventory virtualInventory = Bukkit.createInventory(null, 36);
            virtualInventory.setStorageContents(bottomInventory.getStorageContents());
            addItem.setAmount(addAmount);

            HashMap<Integer, ItemStack> result = virtualInventory.addItem(addItem);
            for (ItemStack itemFailed : result.values()) {
                if (itemFailed.isSimilar(addItem)) {
                    addAmount = (int) (Math.ceil((addAmount - itemFailed.getAmount()) / (double) amount) * amount);
                    amountMultiplier = addAmount / amount;
                }
            }
            virtualInventory.clear();
            addItem.setAmount(addAmount);
        }

        List<ItemStack> oldItems = new ArrayList<>();
        if (event.getRecipe() instanceof ShapelessRecipe) {
            oldItems.addAll(((ShapelessRecipe) event.getRecipe()).getIngredientList());
        }
        if (event.getRecipe() instanceof ShapedRecipe) {
            oldItems.addAll(((ShapedRecipe) event.getRecipe()).getIngredientMap().values());
        }

        if (addItem.getAmount() > 0) {
            for (ItemStack oldItem : oldItems) {
                if (oldItem == null) {
                    continue;
                }

                ItemStack removedItem = oldItem.clone();
                removedItem.setAmount(oldItem.getAmount() * amountMultiplier);
                playerCraftItem(player.getLocation(), player.getName(), removedItem, ItemLogger.ITEM_DESTROY);
            }

            playerCraftItem(player.getLocation(), player.getName(), addItem, ItemLogger.ITEM_CREATE);
        }
    }

}
