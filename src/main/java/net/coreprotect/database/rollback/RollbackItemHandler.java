package net.coreprotect.database.rollback;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;

import net.coreprotect.config.ConfigHandler;

public class RollbackItemHandler {

    /**
     * Populates an ItemStack with metadata from the database
     * 
     * @param itemstack
     *            The ItemStack to populate
     * @param metadata
     *            The metadata as a byte array
     * @return Object array containing [slot, facing, itemstack]
     */
    public static Object[] populateItemStack(ItemStack itemstack, byte[] metadata) {
        if (metadata != null) {
            try {
                ByteArrayInputStream metaByteStream = new ByteArrayInputStream(metadata);
                BukkitObjectInputStream metaObjectStream = new BukkitObjectInputStream(metaByteStream);
                Object metaList = metaObjectStream.readObject();
                metaObjectStream.close();
                metaByteStream.close();

                return RollbackUtil.populateItemStack(itemstack, metaList);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return new Object[] { 0, "", itemstack };
    }

    /**
     * Sorts inventory items for better display
     * 
     * @param inventory
     *            The inventory to sort
     * @param slots
     *            The slots to sort
     */
    public static void sortContainerItems(PlayerInventory inventory, List<Integer> slots) {
        if (slots.contains(0)) {
            ItemStack boots = inventory.getBoots();
            if (boots != null && !boots.getType().equals(Material.AIR)) {
                if (!boots.getType().name().contains("BOOTS")) {
                    inventory.setBoots(new ItemStack(Material.AIR));
                    inventory.addItem(boots);
                }
            }
        }

        if (slots.contains(1)) {
            ItemStack leggings = inventory.getLeggings();
            if (leggings != null && !leggings.getType().equals(Material.AIR)) {
                if (!leggings.getType().name().contains("LEGGINGS")) {
                    inventory.setLeggings(new ItemStack(Material.AIR));
                    inventory.addItem(leggings);
                }
            }
        }

        if (slots.contains(2)) {
            ItemStack chestplate = inventory.getChestplate();
            if (chestplate != null && !chestplate.getType().equals(Material.AIR)) {
                if (!chestplate.getType().name().contains("CHESTPLATE")) {
                    inventory.setChestplate(new ItemStack(Material.AIR));
                    inventory.addItem(chestplate);
                }
            }
        }

        if (slots.contains(3)) {
            ItemStack helmet = inventory.getHelmet();
            if (helmet != null && !helmet.getType().equals(Material.AIR)) {
                String materialName = helmet.getType().name();
                if (!materialName.contains("HELMET") && !materialName.contains("SKULL") && !materialName.endsWith("_HEAD")) {
                    inventory.setHelmet(new ItemStack(Material.AIR));
                    inventory.addItem(helmet);
                }
            }
        }
    }

    /**
     * Update the item count in the rollback hash
     * 
     * @param userString
     *            The username for this rollback
     * @param increment
     *            The amount to increment the item count by
     */
    public static void updateItemCount(String userString, int increment) {
        int[] rollbackHashData = ConfigHandler.rollbackHash.get(userString);
        int itemCount = rollbackHashData[0];
        int blockCount = rollbackHashData[1];
        int entityCount = rollbackHashData[2];
        int scannedWorlds = rollbackHashData[4];

        itemCount += increment;
        ConfigHandler.rollbackHash.put(userString, new int[] { itemCount, blockCount, entityCount, 0, scannedWorlds });
    }

}
