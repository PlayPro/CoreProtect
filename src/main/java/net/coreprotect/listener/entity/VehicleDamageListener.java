package net.coreprotect.listener.entity;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDamageEvent;

import net.coreprotect.config.ConfigHandler;

public final class VehicleDamageListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getAttacker() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getAttacker();
        if (!Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()))) {
            return;
        }

        event.setCancelled(true);
        HangingBreakByEntityListener.inspectItemFrame(event.getVehicle().getLocation().getBlock().getState(), player);
    }
}
