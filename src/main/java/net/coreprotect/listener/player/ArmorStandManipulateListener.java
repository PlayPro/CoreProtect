package net.coreprotect.listener.player;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

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
import net.coreprotect.utility.ItemUtils;

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
                        List<String> blockData = ChestTransactionLookup.performLookup(null, statement, location, finalPlayer, 1, 7, true);
                        for (String data : blockData) {
                            Chat.sendComponent(finalPlayer, data);
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
        ItemStack[] oldContents = ItemUtils.getArmorStandContents(equipment);
        ItemStack[] newContents = oldContents.clone();
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

        int slot = 0;
        switch (event.getSlot()) {
            case LEGS:
                slot = 1;
                break;
            case CHEST:
                slot = 2;
                break;
            case HEAD:
                slot = 3;
                break;
            case HAND:
                slot = 4;
                break;
            case OFF_HAND:
                slot = 5;
                break;
            default:
                slot = 0;
        }
        // 0: BOOTS, 1: LEGGINGS, 2: CHESTPLATE, 3: HELMET, 4: MAINHAND, 5: OFFHAND

        if (item.getType() == playerItem.getType()) {
            return;
        }
        else if (item.getType() != Material.AIR && playerItem.getType() == Material.AIR) {
            oldContents[slot] = item.clone();
            newContents[slot] = new ItemStack(Material.AIR);
            PlayerInteractEntityListener.queueContainerSpecifiedItems(player.getName(), Material.ARMOR_STAND, new Object[] { oldContents, newContents }, armorStand.getLocation(), false);
        }
        else if (item.getType() == Material.AIR && playerItem.getType() != Material.AIR) {
            oldContents[slot] = new ItemStack(Material.AIR);
            newContents[slot] = playerItem.clone();
            PlayerInteractEntityListener.queueContainerSpecifiedItems(player.getName(), Material.ARMOR_STAND, new Object[] { oldContents, newContents }, armorStand.getLocation(), false);
        }
        else if (item.getType() != Material.AIR && playerItem.getType() != Material.AIR) {
            oldContents[slot] = item.clone();
            newContents[slot] = playerItem.clone();
            PlayerInteractEntityListener.queueContainerSpecifiedItems(player.getName(), Material.ARMOR_STAND, new Object[] { oldContents, newContents }, armorStand.getLocation(), false);
        }
    }
}
