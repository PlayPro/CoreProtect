package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.ItemStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.utility.Util;
import net.coreprotect.utility.serialize.ItemMetaHandler;

public class ItemLogger {

    public static final int ITEM_REMOVE = 0;
    public static final int ITEM_ADD = 1;
    public static final int ITEM_DROP = 2;
    public static final int ITEM_PICKUP = 3;
    public static final int ITEM_REMOVE_ENDER = 4;
    public static final int ITEM_ADD_ENDER = 5;

    private ItemLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, Location location, String user) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }

            String loggingItemId = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();

            List<ItemStack> dropList = ConfigHandler.itemsDrop.getOrDefault(loggingItemId, new ArrayList<>());
            ItemStack[] itemDrops = new ItemStack[dropList.size()];
            itemDrops = dropList.toArray(itemDrops);
            dropList.clear();

            List<ItemStack> pickupList = ConfigHandler.itemsPickup.getOrDefault(loggingItemId, new ArrayList<>());
            ItemStack[] itemPickups = new ItemStack[pickupList.size()];
            itemPickups = pickupList.toArray(itemPickups);
            pickupList.clear();

            Util.mergeItems(null, itemDrops);
            Util.mergeItems(null, itemPickups);
            logTransaction(preparedStmt, batchCount, user, location, itemDrops, ITEM_DROP);
            logTransaction(preparedStmt, batchCount, user, location, itemPickups, ITEM_PICKUP);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected static void logTransaction(PreparedStatement preparedStmt, int batchCount, String user, Location location, ItemStack[] items, int action) {
        try {
            for (ItemStack item : items) {
                if (item != null && item.getAmount() > 0 && !Util.isAir(item.getType())) {
                    // Object[] metadata = new Object[] { slot, item.getItemMeta() };
                    List<List<Map<String, Object>>> data = ItemMetaHandler.seralize(item, null, null, 0);
                    if (data.size() == 0) {
                        data = null;
                    }

                    CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user);
                    CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);

                    int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
                    int wid = Util.getWorldId(location.getWorld().getName());
                    int time = (int) (System.currentTimeMillis() / 1000L);
                    int x = location.getBlockX();
                    int y = location.getBlockY();
                    int z = location.getBlockZ();
                    int typeId = Util.getBlockId(item.getType().name(), true);
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
