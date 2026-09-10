package net.coreprotect.database.logger;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.ItemStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.item.ItemTransactionActions;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.serialize.ItemMetaHandler;

public class ItemLogger {

    public static final int ITEM_REMOVE = ItemTransactionActions.REMOVE;
    public static final int ITEM_ADD = ItemTransactionActions.ADD;
    public static final int ITEM_DROP = ItemTransactionActions.DROP;
    public static final int ITEM_PICKUP = ItemTransactionActions.PICKUP;
    public static final int ITEM_REMOVE_ENDER = ItemTransactionActions.REMOVE_ENDER;
    public static final int ITEM_ADD_ENDER = ItemTransactionActions.ADD_ENDER;
    public static final int ITEM_THROW = ItemTransactionActions.THROW;
    public static final int ITEM_SHOOT = ItemTransactionActions.SHOOT;
    public static final int ITEM_BREAK = ItemTransactionActions.BREAK;
    public static final int ITEM_DESTROY = ItemTransactionActions.DESTROY;
    public static final int ITEM_CREATE = ItemTransactionActions.CREATE;
    public static final int ITEM_SELL = ItemTransactionActions.SELL;
    public static final int ITEM_BUY = ItemTransactionActions.BUY;

    private ItemLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(ConsumerWriteBatch preparedStmt, int batchCount, Location location, int offset, String user) {
        try {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }

            prepare(location, offset, user).log(preparedStmt, batchCount, user);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

    public static PreparedTransaction prepare(Location location, int offset, String user) {
        String key = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        ItemStack[][] items = {
                snapshot(ConfigHandler.itemsPickup, key), snapshot(ConfigHandler.itemsDrop, key),
                snapshot(ConfigHandler.itemsThrown, key), snapshot(ConfigHandler.itemsShot, key),
                snapshot(ConfigHandler.itemsBreak, key), snapshot(ConfigHandler.itemsDestroy, key),
                snapshot(ConfigHandler.itemsCreate, key), snapshot(ConfigHandler.itemsSell, key), snapshot(ConfigHandler.itemsBuy, key)
        };
        for (ItemStack[] group : items) {
            ItemUtils.mergeItems(null, group);
        }
        return new PreparedTransaction(location, (int) (System.currentTimeMillis() / 1000L) - offset, items);
    }

    private static ItemStack[] snapshot(Map<String, List<ItemStack>> source, String key) {
        List<ItemStack> values = source.get(key);
        return values == null ? new ItemStack[0] : ItemUtils.getContainerState(values.toArray(new ItemStack[0]));
    }

    public static final class PreparedTransaction {
        private static final int[] ACTIONS = { ITEM_PICKUP, ITEM_DROP, ITEM_THROW, ITEM_SHOOT, ITEM_BREAK, ITEM_DESTROY, ITEM_CREATE, ITEM_SELL, ITEM_BUY };
        private final Location location;
        private final int time;
        private final ItemStack[][] items;

        private PreparedTransaction(Location location, int time, ItemStack[][] items) {
            this.location = location.clone();
            this.time = time;
            this.items = items;
        }

        public void log(ConsumerWriteBatch batch, int batchCount, String user) {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }
            for (int index = 0; index < items.length; index++) {
                logTransaction(batch, batchCount, 0, user, location.clone(), ItemUtils.getContainerState(items[index]), ACTIONS[index], time);
            }
        }
    }

    protected static void logTransaction(ConsumerWriteBatch preparedStmt, int batchCount, int offset, String user, Location location, ItemStack[] items, int action) {
        logTransaction(preparedStmt, batchCount, offset, user, location, items, action, null);
    }

    protected static void logTransaction(ConsumerWriteBatch preparedStmt, int batchCount, int offset, String user, Location location, ItemStack[] items, int action, Integer preparedTime) {
        try {
            for (ItemStack item : items) {
                if (item != null && item.getAmount() > 0 && !BlockUtils.isAir(item.getType())) {
                    // Object[] metadata = new Object[] { slot, item.getItemMeta() };
                    if (ConfigHandler.isFilterBlacklisted(user, item.getType().getKey().toString())){
                        continue;
                    }

                    List<List<Map<String, Object>>> data = ItemMetaHandler.serialize(item, null, null, 0);
                    if (data.size() == 0) {
                        data = null;
                    }

                    CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user, location, CoreProtectPreLogEvent.Action.ITEM_TRANSACTION, action, item.getType(), null, null);
                    if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
                        CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
                    }

                    if (event.isCancelled()) {
                        return;
                    }
                    
                    int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
                    Location eventLocation = event.getLocation();
                    int wid = WorldUtils.getWorldId(eventLocation.getWorld().getName());
                    int time = preparedTime == null ? (int) (System.currentTimeMillis() / 1000L) - offset : preparedTime;
                    int x = eventLocation.getBlockX();
                    int y = eventLocation.getBlockY();
                    int z = eventLocation.getBlockZ();
                    int typeId = MaterialUtils.getBlockId(item.getType().name(), true);
                    int amount = item.getAmount();
                    ItemStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, typeId, data, amount, action);
                }
            }
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

}
