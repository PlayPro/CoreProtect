package net.coreprotect.paper;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.MerchantRecipe;

public interface PaperInterface {

    public InventoryHolder getHolder(Inventory holder, boolean useSnapshot);

    public boolean isStopping(Server server);

    public String getLine(Sign sign, int line);

    public void teleportAsync(Entity entity, Location location);

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
