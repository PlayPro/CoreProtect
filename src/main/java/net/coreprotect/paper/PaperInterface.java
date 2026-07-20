package net.coreprotect.paper;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.Plugin;

public interface PaperInterface {

    public InventoryHolder getHolder(Inventory holder, boolean useSnapshot);

    public boolean isStopping(Server server);

    public double getAverageTickTime(Server server);

    public String getLine(Sign sign, int line);

    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin);

    public void teleportAsync(Entity entity, Location location);

    public void prefetchChunk(World world, int chunkX, int chunkZ);

    public boolean isOwnedByCurrentRegion(Entity entity);

    public boolean isOwnedByCurrentRegion(World world, int chunkX, int chunkZ);

    public boolean executeEntityTask(Plugin plugin, Entity entity, Runnable task, Runnable retiredTask);

    public boolean executeEntityTask(Plugin plugin, Entity entity, Runnable task, Runnable retiredTask, long delayTicks);

    public String getSkullOwner(Skull skull);

    public String getSkullSkin(Skull skull);

    public void setSkullOwner(Skull skull, String owner);

    public void setSkullSkin(Skull skull, String skin);

    public List<Object> getVillagerReputations(Villager villager);

    public boolean setVillagerReputations(Villager villager, List<?> reputations);

    public Object getVillagerRestocksToday(Villager villager);

    public void setVillagerRestocksToday(Villager villager, Object value);

    public void addMerchantRecipeMeta(MerchantRecipe recipe, List<Object> recipeData);

    public void setMerchantRecipeMeta(MerchantRecipe recipe, List<?> recipeData);

}
