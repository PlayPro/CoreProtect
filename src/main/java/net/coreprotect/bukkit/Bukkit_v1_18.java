package net.coreprotect.bukkit;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

/**
 * Bukkit adapter implementation for Minecraft 1.18.
 * Provides version-specific implementations for the BukkitInterface
 * to handle features introduced in the 1.18 update.
 */
public class Bukkit_v1_18 extends Bukkit_v1_17 {

    private static final boolean HAS_RECIPE_ADJUST = hasPublicMethod(MerchantRecipe.class, "adjust", ItemStack.class);

    protected static boolean hasPublicMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            type.getMethod(name, parameters);
            return true;
        }
        catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Adjusts an ingredient in a merchant recipe for version 1.18+.
     * This handles changes to the MerchantRecipe API introduced in Bukkit 1.18.1.
     *
     * @param recipe
     *            The merchant recipe
     * @param itemStack
     *            The item stack to adjust
     * @return The adjusted item stack, or null if adjustment not supported or fails
     */
    @Override
    public ItemStack adjustIngredient(MerchantRecipe recipe, ItemStack itemStack) {
        if (itemStack == null || !HAS_RECIPE_ADJUST) {
            return super.adjustIngredient(recipe, itemStack);
        }

        try {
            ItemStack adjustedStack = itemStack.clone();
            recipe.adjust(adjustedStack);
            return adjustedStack;
        }
        catch (RuntimeException e) {
            return super.adjustIngredient(recipe, itemStack);
        }
    }
}
