package net.coreprotect.bukkit;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * @author Pavithra Gunasekaran
 */
public class BukkitAdapterLegacy implements  BukkitAdapterLegacyInterface{
    public static BukkitAdapterLegacyInterface ADAPTER;
    @Override
    public String parseLegacyName(String name) {
        return name;
    }

    @Override
    public int getLegacyBlockId(Material material) {
        return -1;
    }

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        return false;
    }

    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        return false;
    }

    @Override
    public boolean getItemMeta(ItemMeta itemMeta, List<Map<String, Object>> list, List<List<Map<String, Object>>> metadata, int slot) {
        return false;
    }

    @Override
    public boolean setItemMeta(Material rowType, ItemStack itemstack, List<Map<String, Object>> map) {
        return false;
    }

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        return false;
    }


}
