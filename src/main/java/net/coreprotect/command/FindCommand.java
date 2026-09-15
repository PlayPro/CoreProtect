package net.coreprotect.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.coreprotect.CoreProtect;
import net.coreprotect.api.InventoryAPI;
import net.coreprotect.api.LookupOptions;
import net.coreprotect.api.result.InventoryResult;
import net.coreprotect.command.parser.ItemSearchParser;
import net.coreprotect.config.Config;
import net.coreprotect.database.rollback.Rollback;
import net.coreprotect.model.item.ItemSearchFilter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ErrorReporter;

public final class FindCommand {
    private static final java.util.Set<String> ACTIVE = ConcurrentHashMap.newKeySet();
    private FindCommand() {}

    public static void runCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("coreprotect.find")) {
            message(sender, "You do not have permission to search items.");
            return;
        }
        try {
            Map<String, String> values = ItemSearchParser.parse(args);
            ItemSearchFilter filter = new ItemSearchFilter(values);
            boolean online = values.getOrDefault("source", "history").equals("online");
            if (!sender.hasPermission(online ? "coreprotect.find.online" : "coreprotect.find.history")) {
                message(sender, "You do not have permission for this search source.");
                return;
            }
            if (!online && (!sender.hasPermission("coreprotect.lookup.inventory") || !sender.hasPermission("coreprotect.lookup.container")
                    || !sender.hasPermission("coreprotect.lookup.item") || !Config.getGlobal().API_ENABLED)) {
                message(sender, "Historical search requires inventory, container, and item lookup permissions and api-enabled.");
                return;
            }
            if (!ACTIVE.add(sender.getName())) {
                message(sender, "Your previous item search is still running.");
                return;
            }
            boolean contents = Boolean.parseBoolean(values.getOrDefault("contents", "true"));
            if (online) {
                if (values.containsKey("t")) throw new IllegalArgumentException("Live searches do not accept a time window.");
                searchOnline(sender, values, filter, contents);
            }
            else {
                long[] time = CommandParser.parseTime(new String[] {"lookup", "t:" + values.getOrDefault("t", "1d")});
                if (time[0] <= 0 || time[0] > 2678400 || time[1] != 0) throw new IllegalArgumentException("Use a single time window from 1 second to 31 days.");
                Scheduler.runTaskAsynchronously(CoreProtect.getInstance(), () -> {
                    try {
                        LookupOptions.Builder options = LookupOptions.builder().time((int) time[0]).limit(0, 10001);
                        if (values.containsKey("u")) options.users(List.of(values.get("u")));
                        List<InventoryResult> rows = InventoryAPI.performLookupChecked(options.build());
                        List<String> matches = new ArrayList<>();
                        boolean limited = rows.size() > 10000;
                        for (InventoryResult row : rows.subList(0, Math.min(rows.size(), 10000))) {
                            if (row.getType() == null || row.getType().isAir()) continue;
                            ItemStack item = new ItemStack(row.getType(), Math.max(1, row.getAmount()));
                            byte[] metadata = row.getMetadata();
                            if (metadata != null && metadata.length > 0) item = (ItemStack) Rollback.populateItemStack(item, metadata)[2];
                            String label = java.time.Instant.ofEpochMilli(row.getTimestamp()) + " " + row.getPlayer() + " " + row.getSource()
                                    + " " + row.getTransactionActionString() + (row.isRolledBack() ? " [rolled back]" : "")
                                    + " " + row.worldName() + " " + row.getX() + "," + row.getY() + "," + row.getZ();
                            matches.addAll(filter.find(item, label, contents));
                            if (matches.size() > 10000) { limited = true; break; }
                        }
                        finish(sender, values, matches, limited ? "Partial results: search limit reached. Narrow t: or u:." : "Historical transactions; these do not establish current ownership.");
                    }
                    catch (Exception e) {
                        ErrorReporter.report(e);
                        message(sender, "Item search failed. No complete result is available; check the server log.");
                    }
                    finally { ACTIVE.remove(sender.getName()); }
                });
            }
        }
        catch (Exception e) {
            ACTIVE.remove(sender.getName());
            message(sender, "Search could not start: " + e.getMessage());
        }
    }

    private static void searchOnline(CommandSender sender, Map<String, String> values, ItemSearchFilter filter, boolean contents) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.removeIf(player -> values.containsKey("u") && !player.getName().equalsIgnoreCase(values.get("u")));
        List<String> matches = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger remaining = new AtomicInteger(players.size());
        AtomicInteger unavailable = new AtomicInteger();
        Runnable complete = () -> {
            if (remaining.decrementAndGet() == 0) {
                finish(sender, values, matches, "Live inventory and Ender Chest search. Players unavailable: " + unavailable.get() + ". World chests and offline players are not scanned.");
                ACTIVE.remove(sender.getName());
            }
        };
        if (players.isEmpty()) {
            finish(sender, values, matches, "No matching online players.");
            ACTIVE.remove(sender.getName());
            return;
        }
        for (Player player : players) {
            java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
            Runnable once = () -> { if (finished.compareAndSet(false, true)) complete.run(); };
            try {
                Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
                    try {
                        if (!player.isOnline()) { unavailable.incrementAndGet(); return; }
                        scan(player.getInventory().getContents(), player.getName() + "/inventory", filter, contents, matches);
                        scan(player.getEnderChest().getContents(), player.getName() + "/enderchest", filter, contents, matches);
                    }
                    catch (Exception e) { unavailable.incrementAndGet(); ErrorReporter.report(e); }
                    finally { once.run(); }
                }, () -> { unavailable.incrementAndGet(); once.run(); }, player, 0);
            }
            catch (Exception e) { unavailable.incrementAndGet(); once.run(); }
        }
    }

    private static void scan(ItemStack[] items, String source, ItemSearchFilter filter, boolean contents, List<String> matches) {
        for (int slot = 0; slot < items.length; slot++) matches.addAll(filter.find(items[slot], source + "[" + slot + "]", contents));
    }

    private static void finish(CommandSender sender, Map<String, String> values, List<String> matches, String note) {
        List<String> sorted = new ArrayList<>(matches);
        if (values.getOrDefault("source", "history").equals("online")) sorted.sort(String::compareTo);
        int page = Integer.parseInt(values.getOrDefault("page", "1"));
        int first = (page - 1) * 10;
        message(sender, sorted.size() + " matches, page " + page + ". " + note);
        for (int index = first; index < Math.min(first + 10, sorted.size()); index++) message(sender, sorted.get(index));
        if (first >= sorted.size() && !sorted.isEmpty()) message(sender, "Page is beyond the available results.");
    }

    private static void message(CommandSender sender, String text) {
        Scheduler.runTask(CoreProtect.getInstance(), () -> Chat.sendMessage(sender, "CoreProtect - " + text), sender instanceof Player ? sender : null);
    }
}
