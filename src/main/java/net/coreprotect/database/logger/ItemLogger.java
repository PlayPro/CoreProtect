package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.ItemStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.serialize.ItemMetaHandler;

public class ItemLogger {

    public static final int ITEM_REMOVE = 0;
    public static final int ITEM_ADD = 1;
    public static final int ITEM_DROP = 2;
    public static final int ITEM_PICKUP = 3;
    public static final int ITEM_REMOVE_ENDER = 4;
    public static final int ITEM_ADD_ENDER = 5;
    public static final int ITEM_THROW = 6;
    public static final int ITEM_SHOOT = 7;
    public static final int ITEM_BREAK = 8;
    public static final int ITEM_DESTROY = 9;
    public static final int ITEM_CREATE = 10;
    public static final int ITEM_SELL = 11;
    public static final int ITEM_BUY = 12;

    private ItemLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, Location location, int offset, String user) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }

            String loggingItemId = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();

            List<ItemStack> pickupList = ConfigHandler.itemsPickup.getOrDefault(loggingItemId, new ArrayList<>());
            ItemStack[] itemPickups = new ItemStack[pickupList.size()];
            itemPickups = pickupList.toArray(itemPickups);
            pickupList.clear();

            List<ItemStack> dropList = ConfigHandler.itemsDrop.getOrDefault(loggingItemId, new ArrayList<>());
            ItemStack[] itemDrops = new ItemStack[dropList.size()];
            itemDrops = dropList.toArray(itemDrops);
            dropList.clear();

            List<ItemStack> thrownList = ConfigHandler.itemsThrown.getOrDefault(loggingItemId, new ArrayList<>());
            ItemStack[] itemThrows = new ItemStack[thrownList.size()];
            itemThrows = thrownList.toArray(itemThrows);
            thrownList.clear();

            List<ItemStack> shotList = ConfigHandler.itemsShot.getOrDefault(loggingItemId, new ArrayList<>());
            ItemStack[] itemShots = new ItemStack[shotList.size()];
            itemShots = shotList.toArray(itemShots);
            shotList.clear();

            List<ItemStack> breakList = ConfigHandler.itemsBreak.getOrDefault(loggingItemId, new ArrayList<>());
            ItemStack[] itemBreaks = new ItemStack[breakList.size()];
            itemBreaks = breakList.toArray(itemBreaks);
            breakList.clear();

            List<ItemStack> destroyList = ConfigHandler.itemsDestroy.getOrDefault(loggingItemId, new ArrayList<>());
            ItemStack[] itemDestroys = new ItemStack[destroyList.size()];
            itemDestroys = destroyList.toArray(itemDestroys);
            destroyList.clear();

            List<ItemStack> createList = ConfigHandler.itemsCreate.getOrDefault(loggingItemId, new ArrayList<>());
            ItemStack[] itemCreates = new ItemStack[createList.size()];
            itemCreates = createList.toArray(itemCreates);
            createList.clear();

            List<ItemStack> sellList = ConfigHandler.itemsSell.getOrDefault(loggingItemId, new ArrayList<>());
            ItemStack[] itemSells = new ItemStack[sellList.size()];
            itemSells = sellList.toArray(itemSells);
            sellList.clear();

            List<ItemStack> buyList = ConfigHandler.itemsBuy.getOrDefault(loggingItemId, new ArrayList<>());
            ItemStack[] itemBuys = new ItemStack[buyList.size()];
            itemBuys = buyList.toArray(itemBuys);
            buyList.clear();

            ItemUtils.mergeItems(null, itemPickups);
            ItemUtils.mergeItems(null, itemDrops);
            ItemUtils.mergeItems(null, itemThrows);
            ItemUtils.mergeItems(null, itemShots);
            ItemUtils.mergeItems(null, itemBreaks);
            ItemUtils.mergeItems(null, itemDestroys);
            ItemUtils.mergeItems(null, itemCreates);
            ItemUtils.mergeItems(null, itemSells);
            ItemUtils.mergeItems(null, itemBuys);
            logTransaction(preparedStmt, batchCount, offset, user, location, itemPickups, ITEM_PICKUP);
            logTransaction(preparedStmt, batchCount, offset, user, location, itemDrops, ITEM_DROP);
            logTransaction(preparedStmt, batchCount, offset, user, location, itemThrows, ITEM_THROW);
            logTransaction(preparedStmt, batchCount, offset, user, location, itemShots, ITEM_SHOOT);
            logTransaction(preparedStmt, batchCount, offset, user, location, itemBreaks, ITEM_BREAK);
            logTransaction(preparedStmt, batchCount, offset, user, location, itemDestroys, ITEM_DESTROY);
            logTransaction(preparedStmt, batchCount, offset, user, location, itemCreates, ITEM_CREATE);
            logTransaction(preparedStmt, batchCount, offset, user, location, itemSells, ITEM_SELL);
            logTransaction(preparedStmt, batchCount, offset, user, location, itemBuys, ITEM_BUY);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected static void logTransaction(PreparedStatement preparedStmt, int batchCount, int offset, String user, Location location, ItemStack[] items, int action) {
        try {
            for (ItemStack item : items) {
                if (item != null && item.getAmount() > 0 && !BlockUtils.isAir(item.getType())) {
                    // Object[] metadata = new Object[] { slot, item.getItemMeta() };
                    List<List<Map<String, Object>>> data = ItemMetaHandler.serialize(item, null, null, 0);
                    if (data.size() == 0) {
                        data = null;
                    }

                    CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user);
                    if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
                        CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
                    }

                    if (event.isCancelled()) {
                        return;
                    }

                    int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
                    int wid = WorldUtils.getWorldId(location.getWorld().getName());
                    int time = (int) (System.currentTimeMillis() / 1000L) - offset;
                    int x = location.getBlockX();
                    int y = location.getBlockY();
                    int z = location.getBlockZ();
                    int typeId = MaterialUtils.getBlockId(item.getType().name(), true);
                    int amount = item.getAmount();
                    ItemStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, typeId, data, amount, action);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
