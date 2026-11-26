package net.coreprotect.listener.block;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.block.TNTPrimeEvent.PrimeCause;

public final class TNTPrimeListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onTNTPrime(TNTPrimeEvent event) {
        PrimeCause cause = event.getCause();
        if (!event.isCancelled() && cause != PrimeCause.EXPLOSION && cause != PrimeCause.BLOCK_BREAK) {
            TNTPrimeUtil.logPrime(event.getBlock(), event.getPrimingEntity(), cause == PrimeCause.FIRE);
        }
    }
}
