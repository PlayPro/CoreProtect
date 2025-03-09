package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.ItemUtils;

public final class PlayerInteractEntityListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerArmorStandManipulateEvent) {
            return;
        }

        Player player = event.getPlayer();
        final Entity entity = event.getRightClicked(); // change item in ItemFrame, etc
        if (entity instanceof ItemFrame) {
            ItemFrame frame = (ItemFrame) entity;
            ItemStack handItem = new ItemStack(Material.AIR);
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (event.getHand().equals(EquipmentSlot.HAND) && mainHand.getType() != Material.AIR) {
                handItem = mainHand;
            }
            else if (event.getHand().equals(EquipmentSlot.OFF_HAND) && offHand.getType() != Material.AIR) {
                handItem = offHand;
            }
            else if (event.getHand().equals(EquipmentSlot.OFF_HAND)) {
                return;
            }

            if (ConfigHandler.inspecting.get(player.getName()) != null && ConfigHandler.inspecting.get(player.getName())) {
                if (BlockGroup.CONTAINERS.contains(Material.ARMOR_STAND)) {
                    // logged armor stand items
                    ArmorStandManipulateListener.inspectHangingTransactions(frame.getLocation(), player);
                }

                event.setCancelled(true);
            }

            if (event.isCancelled()) {
                return;
            }

            if (frame.getItem().getType() != Material.AIR && event.getHand().equals(EquipmentSlot.HAND) && Config.getConfig(player.getWorld()).PLAYER_INTERACTIONS) {
                Queue.queuePlayerInteraction(player.getName(), entity.getLocation().getBlock().getState(), Material.ITEM_FRAME);
            }

            if (!Config.getConfig(player.getWorld()).ITEM_TRANSACTIONS) {
                return;
            }

            if (frame.getItem().getType().equals(Material.AIR) && !handItem.getType().equals(Material.AIR)) { // add item to item frame
                ItemStack[] oldState = new ItemStack[] { new ItemStack(Material.AIR) };
                ItemStack[] newState = new ItemStack[] { handItem.clone() };
                if (newState[0].getAmount() > 1) {
                    newState[0].setAmount(1); // never add more than 1 item to an item frame at once
                }
                queueContainerSpecifiedItems(player.getName(), Material.ITEM_FRAME, new Object[] { oldState, newState, frame.getFacing() }, frame.getLocation(), false);
            }
        }
        else if (!event.isCancelled() && entity instanceof Creature && entity.getType().name().equals("ALLAY")) {
            ItemStack handItem = new ItemStack(Material.AIR);
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (event.getHand().equals(EquipmentSlot.HAND) && mainHand.getType() != Material.AIR) {
                handItem = mainHand;
            }
            else if (event.getHand().equals(EquipmentSlot.OFF_HAND) && offHand.getType() != Material.AIR) {
                handItem = offHand;
            }
            else if (event.getHand().equals(EquipmentSlot.OFF_HAND)) {
                return;
            }

            ItemStack allayItem = ((Creature) entity).getEquipment().getItemInMainHand();
            if (handItem.getType().equals(allayItem.getType())) {
                return;
            }

            if (allayItem.getType().equals(Material.AIR)) {
                ItemStack removedItem = handItem.clone();
                removedItem.setAmount(1);
                CraftItemListener.logCraftedItem(player.getLocation(), player.getName(), removedItem, ItemLogger.ITEM_SELL);
            }
            else if (handItem.getType().equals(Material.AIR)) {
                ItemStack addItem = allayItem.clone();
                addItem.setAmount(1);
                CraftItemListener.logCraftedItem(player.getLocation(), player.getName(), addItem, ItemLogger.ITEM_BUY);
            }
        }
    }

    public static void queueContainerSpecifiedItems(String user, Material type, Object container, Location location, boolean logDrop) {
        ItemStack[] contents = (ItemStack[]) ((Object[]) container)[0];
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        String transactingChestId = location.getWorld().getUID().toString() + "." + x + "." + y + "." + z;
        String loggingChestId = user.toLowerCase(Locale.ROOT) + "." + x + "." + y + "." + z;
        int chestId = Queue.getChestId(loggingChestId);
        if (chestId > 0) {
            if (ConfigHandler.forceContainer.get(loggingChestId) != null) {
                int forceSize = ConfigHandler.forceContainer.get(loggingChestId).size();
                List<ItemStack[]> list = ConfigHandler.oldContainer.get(loggingChestId);

                if (list.size() <= forceSize) {
                    list.add(ItemUtils.getContainerState(contents));
                    ConfigHandler.oldContainer.put(loggingChestId, list);
                }
            }
        }
        else {
            List<ItemStack[]> list = new ArrayList<>();
            list.add(ItemUtils.getContainerState(contents));
            ConfigHandler.oldContainer.put(loggingChestId, list);
        }

        ConfigHandler.transactingChest.computeIfAbsent(transactingChestId, k -> Collections.synchronizedList(new ArrayList<>()));
        Queue.queueContainerTransaction(user, location, type, container, chestId);

        if (logDrop) {
            ItemStack dropItem = contents[0];
            if (dropItem.getType() == Material.AIR) {
                return;
            }

            PlayerDropItemListener.playerDropItem(location, user, dropItem);
        }
    }
}
