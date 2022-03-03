package net.coreprotect.bukkit;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

public class Bukkit_v1_18 extends Bukkit_v1_17 implements BukkitInterface {

    private Boolean hasAdjust = null;

    @Override
    public ItemStack adjustIngredient(MerchantRecipe recipe, ItemStack itemStack) {
        try {
            if (hasAdjust == null) {
                hasAdjust = true;
                MerchantRecipe.class.getMethod("adjust", ItemStack.class); // Bukkit 1.18.1+
            }
            else if (Boolean.FALSE.equals(hasAdjust)) {
                return null;
            }

            ItemStack adjustedStack = itemStack.clone();
            recipe.adjust(adjustedStack);
            return adjustedStack;
        }
        catch (Exception e) {
            hasAdjust = false;
            return null;
        }
    }

}
