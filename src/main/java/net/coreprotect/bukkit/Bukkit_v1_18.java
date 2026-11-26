package net.coreprotect.bukkit;

import java.net.URL;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.block.Skull;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import net.coreprotect.utility.SkullProfileUtils;

/**
 * Bukkit adapter implementation for Minecraft 1.18.
 * Provides version-specific implementations for the BukkitInterface
 * to handle features introduced in the 1.18 update.
 */
public class Bukkit_v1_18 extends Bukkit_v1_17 {

    @Override
    public boolean isUltraWarm(World world) {
        return world.isUltraWarm();
    }

    private static final boolean HAS_RECIPE_ADJUST = hasPublicMethod(MerchantRecipe.class, "adjust", ItemStack.class);
    private static final boolean HAS_SKULL_PROFILES = hasPublicMethod(Skull.class, "getOwnerProfile");

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

    @Override
    public String getSkullProfileOwner(Skull skull) {
        return HAS_SKULL_PROFILES ? SkullProfiles.getOwner(skull) : null;
    }

    @Override
    public void setSkullProfileOwner(Skull skull, String name) {
        if (HAS_SKULL_PROFILES && SkullProfileUtils.isOwnerName(name)) {
            SkullProfiles.setOwner(skull, name);
        }
    }

    @Override
    public String getSkullSkin(Skull skull) {
        return HAS_SKULL_PROFILES ? SkullProfiles.getSkin(skull) : null;
    }

    @Override
    public void setSkullSkin(Skull skull, String skin) {
        if (HAS_SKULL_PROFILES) {
            SkullProfiles.setSkin(skull, skin);
        }
    }

    private static final class SkullProfiles {
        private static PlayerProfile getProfile(Skull skull) {
            try {
                return skull.getOwnerProfile();
            }
            catch (IllegalArgumentException e) {
                return null;
            }
        }

        private static String getOwner(Skull skull) {
            PlayerProfile profile = getProfile(skull);
            if (profile == null) {
                return null;
            }

            UUID uuid = profile.getUniqueId();
            return uuid == null ? profile.getName() : uuid.toString();
        }

        private static void setOwner(Skull skull, String name) {
            try {
                skull.setOwnerProfile(Bukkit.createPlayerProfile(name));
            }
            catch (IllegalArgumentException ignored) {
            }
        }

        private static String getSkin(Skull skull) {
            PlayerProfile profile = getProfile(skull);
            URL skin = profile == null ? null : profile.getTextures().getSkin();
            return skin == null ? null : skin.toString();
        }

        private static void setSkin(Skull skull, String skin) {
            URL url = SkullProfileUtils.getSkinUrl(skin);
            if (url == null) {
                return;
            }

            try {
                PlayerProfile profile = getProfile(skull);
                if (profile == null || (profile.getUniqueId() == null && !SkullProfileUtils.isOwnerName(profile.getName()))) {
                    profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                }

                PlayerTextures textures = profile.getTextures();
                textures.setSkin(url);
                profile.setTextures(textures);
                skull.setOwnerProfile(profile);
            }
            catch (IllegalArgumentException ignored) {
            }
        }
    }
}
