package net.coreprotect.listener.entity;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.listener.player.PlayerDropItemListener;
import net.coreprotect.utility.EntityUtils;

public final class SulfurCubeShearLogger {

    private SulfurCubeShearLogger() {
        throw new IllegalStateException("Utility class");
    }

    public static void logDrops(Entity entity, String user, List<ItemStack> drops) {
        if (entity == null || !EntityUtils.isSulfurCube(entity.getType()) || drops == null || drops.isEmpty()) {
            return;
        }

        Location location = entity.getLocation();
        for (ItemStack drop : drops) {
            PlayerDropItemListener.playerDropItem(location, user, drop);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<ItemStack> getDrops(Object event) {
        if (event == null) {
            return null;
        }

        try {
            Object drops = event.getClass().getMethod("getDrops").invoke(event);
            return (drops instanceof List<?>) ? (List<ItemStack>) drops : null;
        }
        catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
            return null;
        }
    }
}
