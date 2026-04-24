package net.coreprotect.paper.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.destroystokyo.paper.event.block.TNTPrimeEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.block.TNTPrimeUtil;

public final class LegacyTNTPrimeListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onTNTPrime(TNTPrimeEvent event) {
        Block block = event.getBlock();
        if (event.isCancelled() || block.getType() != Material.TNT || !Config.getConfig(block.getWorld()).EXPLOSIONS || event.getReason() == TNTPrimeEvent.PrimeReason.EXPLOSION) {
            return;
        }

        Queue.queueBlockBreak(TNTPrimeUtil.getUser(event.getPrimerEntity()), block.getState(), Material.TNT, block.getBlockData().getAsString(), 0);
    }

}
