package net.coreprotect.listener.player;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.ChestTransactionLookup;
import net.coreprotect.language.Phrase;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public final class ArmorStandManipulateListener extends Queue implements Listener {

    public static void inspectHangingTransactions(final Location location, final Player finalPlayer) {
        class BasicThread implements Runnable {
            @Override
            public void run() {
                if (!finalPlayer.hasPermission("coreprotect.inspect")) {
                    Chat.sendMessage(finalPlayer, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                    ConfigHandler.inspecting.put(finalPlayer.getName(), false);
                    return;
                }
                if (ConfigHandler.converterRunning) {
                    Chat.sendMessage(finalPlayer, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
                    return;
                }
                if (ConfigHandler.purgeRunning) {
                    Chat.sendMessage(finalPlayer, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
                    return;
                }
                if (ConfigHandler.lookupThrottle.get(finalPlayer.getName()) != null) {
                    Object[] lookupThrottle = ConfigHandler.lookupThrottle.get(finalPlayer.getName());
                    if ((boolean) lookupThrottle[0] || ((System.currentTimeMillis() - (long) lookupThrottle[1])) < 100) {
                        Chat.sendMessage(finalPlayer, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                        return;
                    }
                }
                ConfigHandler.lookupThrottle.put(finalPlayer.getName(), new Object[] { true, System.currentTimeMillis() });

                try (Connection connection = Database.getConnection(true)) {
                    if (connection != null) {
                        Statement statement = connection.createStatement();
                        String blockData = ChestTransactionLookup.performLookup(null, statement, location, finalPlayer, 1, 7, true);

                        if (blockData.contains("\n")) {
                            for (String b : blockData.split("\n")) {
                                Chat.sendComponent(finalPlayer, b);
                            }
                        }
                        else {
                            Chat.sendComponent(finalPlayer, blockData);
                        }
                        statement.close();
                    }
                    else {
                        Chat.sendMessage(finalPlayer, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

                ConfigHandler.lookupThrottle.put(finalPlayer.getName(), new Object[] { false, System.currentTimeMillis() });
            }
        }
        Runnable runnable = new BasicThread();
        Thread thread = new Thread(runnable);
        thread.start();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPlayerArmorStandManipulateEvent(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        final ArmorStand armorStand = event.getRightClicked();
        EntityEquipment equipment = armorStand.getEquipment();
        ItemStack[] contents = Util.getArmorStandContents(equipment);
        ItemStack item = event.getArmorStandItem();
        ItemStack playerItem = event.getPlayerItem();

        if (ConfigHandler.inspecting.get(player.getName()) != null) {
            if (ConfigHandler.inspecting.get(player.getName())) {
                if (BlockGroup.CONTAINERS.contains(Material.ARMOR_STAND)) {
                    // logged armor stand items
                    inspectHangingTransactions(armorStand.getLocation(), player);
                }

                event.setCancelled(true);
            }
        }

        if (event.isCancelled()) {
            return;
        }

        if (!Config.getConfig(player.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        if (item == null && playerItem == null) {
            return;
        }

        /*
            if (item!=null && playerItem==null){
                //player gets item
            }
            if (item==null && playerItem!=null){
                //players item placed on armor stands
            }
            if (item!=null && playerItem!=null){
                //items are swapped
            }
         */

        Material type = Material.ARMOR_STAND;
        Location standLocation = armorStand.getLocation();
        int x = standLocation.getBlockX();
        int y = standLocation.getBlockY();
        int z = standLocation.getBlockZ();

        String transactingChestId = standLocation.getWorld().getUID().toString() + "." + x + "." + y + "." + z;
        String loggingChestId = player.getName().toLowerCase(Locale.ROOT) + "." + x + "." + y + "." + z;
        int chestId = Queue.getChestId(loggingChestId);
        if (chestId > 0) {
            if (ConfigHandler.forceContainer.get(loggingChestId) != null) {
                int force_size = ConfigHandler.forceContainer.get(loggingChestId).size();
                List<ItemStack[]> list = ConfigHandler.oldContainer.get(loggingChestId);

                if (list.size() <= force_size) {
                    list.add(Util.getContainerState(contents));
                    ConfigHandler.oldContainer.put(loggingChestId, list);
                }
            }
        }
        else {
            List<ItemStack[]> list = new ArrayList<>();
            list.add(Util.getContainerState(contents));
            ConfigHandler.oldContainer.put(loggingChestId, list);
        }

        ConfigHandler.transactingChest.computeIfAbsent(transactingChestId, k -> Collections.synchronizedList(new ArrayList<>()));
        Queue.queueContainerTransaction(player.getName(), standLocation, type, equipment, chestId);
    }
}
