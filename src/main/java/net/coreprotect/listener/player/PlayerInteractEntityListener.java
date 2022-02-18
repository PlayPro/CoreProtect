package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
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
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.Util;

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
            Material handType = Material.AIR;
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (event.getHand().equals(EquipmentSlot.HAND) && mainHand.getType() != Material.AIR) {
                handType = mainHand.getType();
            }
            else if (event.getHand().equals(EquipmentSlot.OFF_HAND) && offHand.getType() != Material.AIR) {
                handType = offHand.getType();
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

            if (frame.getItem().getType().equals(Material.AIR) && !handType.equals(Material.AIR)) {
                queueFrameTransaction(player.getName(), frame, false);
            }
        }
    }

    public static void queueFrameTransaction(String user, ItemFrame frame, boolean logDrop) {
        ItemStack[] contents = Util.getItemFrameItem(frame);
        Material type = Material.ITEM_FRAME;
        Location frameLocation = frame.getLocation();
        int x = frameLocation.getBlockX();
        int y = frameLocation.getBlockY();
        int z = frameLocation.getBlockZ();

        String transactingChestId = frameLocation.getWorld().getUID().toString() + "." + x + "." + y + "." + z;
        String loggingChestId = user.toLowerCase(Locale.ROOT) + "." + x + "." + y + "." + z;
        int chestId = Queue.getChestId(loggingChestId);
        if (chestId > 0) {
            if (ConfigHandler.forceContainer.get(loggingChestId) != null) {
                int forceSize = ConfigHandler.forceContainer.get(loggingChestId).size();
                List<ItemStack[]> list = ConfigHandler.oldContainer.get(loggingChestId);

                if (list.size() <= forceSize) {
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
        Queue.queueContainerTransaction(user, frameLocation, type, frame, chestId);

        if (logDrop) {
            ItemStack dropItem = frame.getItem();
            if (dropItem.getType() == Material.AIR) {
                return;
            }

            PlayerDropItemListener.playerDropItem(frame.getLocation(), user, dropItem);
        }
    }
}
