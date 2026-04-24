package net.coreprotect.listener.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.TNTPrimeEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

public final class TNTPrimeListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onTNTPrime(TNTPrimeEvent event) {
        Block block = event.getBlock();
        if (event.isCancelled() || block.getType() != Material.TNT || !Config.getConfig(block.getWorld()).EXPLOSIONS || isAlreadyLogged(event.getCause())) {
            return;
        }

        Queue.queueBlockBreak(TNTPrimeUtil.getUser(event.getPrimingEntity()), block.getState(), Material.TNT, block.getBlockData().getAsString(), 0);
    }

    private boolean isAlreadyLogged(TNTPrimeEvent.PrimeCause cause) {
        return cause == TNTPrimeEvent.PrimeCause.EXPLOSION || cause == TNTPrimeEvent.PrimeCause.BLOCK_BREAK;
    }

}
