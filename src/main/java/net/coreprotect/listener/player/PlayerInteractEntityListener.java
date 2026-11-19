package net.coreprotect.listener.player;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.listener.player.inspector.ContainerInspector;
import net.coreprotect.listener.player.inspector.EntityInteractionInspector;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.ItemUtils;

public final class PlayerInteractEntityListener implements Listener {

    private final ContainerInspector containerInspector = new ContainerInspector();
    private final EntityInteractionInspector entityInteractionInspector = new EntityInteractionInspector();

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerArmorStandManipulateEvent) {
            return;
        }

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        Config config = Config.getConfig(player.getWorld());
        if (Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()))) {
            event.setCancelled(true);
            if (!PlayerInteractListener.beginEntityInspection(player, event.getHand())) {
                return;
            }
            if (entity instanceof ItemFrame) {
                if (BlockGroup.CONTAINERS.contains(Material.ARMOR_STAND)) {
                    containerInspector.performHangingLookup(player, entity.getLocation());
                }
            }
            else if (entity instanceof InventoryHolder && config.ITEM_TRANSACTIONS && EntitySpawnTracking.isPlacedEntity(entity)) {
                containerInspector.performEntityContainerLookup(player, entity.getUniqueId(), entity.getLocation());
            }
            else {
                entityInteractionInspector.performLookup(player, entity.getUniqueId(), entity.getLocation());
            }
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (event.isCancelled() || (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND)) {
            return;
        }

        ItemStack handItem = PlayerInteractListener.getHandItem(player, hand);
        boolean holdingItem = PlayerInteractListener.containsItem(handItem);
        if (!holdingItem && hand == EquipmentSlot.OFF_HAND) {
            return;
        }

        if (entity instanceof ItemFrame) {
            ItemFrame frame = (ItemFrame) entity;
            if (frame.getItem().getType() != Material.AIR && hand == EquipmentSlot.HAND && config.PLAYER_INTERACTIONS) {
                Material frameType = BukkitAdapter.ADAPTER.getFrameType(entity);
                if (frameType != null) {
                    Block block = entity.getLocation().getBlock();
                    Queue.queuePlayerInteraction(player.getName(), block.getLocation(), frameType, block.getBlockData().getAsString());
                }
            }

            if (!config.ITEM_TRANSACTIONS) {
                return;
            }

            if (frame.getItem().getType() == Material.AIR && holdingItem) {
                ItemStack[] oldState = new ItemStack[] { new ItemStack(Material.AIR) };
                ItemStack[] newState = new ItemStack[] { handItem.clone() };
                if (newState[0].getAmount() > 1) {
                    newState[0].setAmount(1); // never add more than 1 item to an item frame at once
                }
                queueContainerSpecifiedItems(player.getName(), Material.ITEM_FRAME, new Object[] { oldState, newState, frame.getFacing() }, frame.getLocation(), false);
            }
        }
        else if (entity instanceof Creature && entity.getType().name().equals("ALLAY")) {
            EntityEquipment equipment = ((Creature) entity).getEquipment();
            if (equipment == null) {
                return;
            }
            ItemStack allayItem = equipment.getItemInMainHand();
            boolean allayHoldingItem = PlayerInteractListener.containsItem(allayItem);
            if (holdingItem && !allayHoldingItem) {
                ItemStack removedItem = handItem.clone();
                removedItem.setAmount(1);
                CraftItemListener.logCraftedItem(player.getLocation(), player.getName(), removedItem, ItemLogger.ITEM_SELL);
            }
            else if (!holdingItem && allayHoldingItem) {
                ItemStack addItem = allayItem.clone();
                addItem.setAmount(1);
                CraftItemListener.logCraftedItem(player.getLocation(), player.getName(), addItem, ItemLogger.ITEM_BUY);
            }
        }
    }

    public static void queueContainerSpecifiedItems(String user, Material type, Object container, Location location, boolean logDrop) {
        Object[] event = (Object[]) container;
        ItemStack[] before = (ItemStack[]) event[0];
        ItemStack[] after = (ItemStack[]) event[1];
        BlockFace face = event.length > 2 ? (BlockFace) event[2] : null;
        Queue.queueSpecifiedContainerTransaction(user, location, type, face, before, after);

        if (logDrop && before.length > 0 && before[0] != null && before[0].getType() != Material.AIR) {
            PlayerDropItemListener.playerDropItem(location, user, before[0]);
        }
    }
}
