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
public interface BukkitAdapterLegacyInterface {
    public String parseLegacyName(String name);

    int getLegacyBlockId(Material material);

    public boolean getEntityMeta(LivingEntity entity, List<Object> info);

    public boolean setEntityMeta(Entity entity, Object value, int count);

    public boolean getItemMeta(ItemMeta itemMeta, List<Map<String, Object>> list, List<List<Map<String, Object>>> metadata, int slot);

    public boolean setItemMeta(Material rowType, ItemStack itemstack, List<Map<String, Object>> map);

    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin);

}
