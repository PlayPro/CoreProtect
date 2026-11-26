package net.coreprotect.paper.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.destroystokyo.paper.event.block.TNTPrimeEvent;
import com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason;

import net.coreprotect.listener.block.TNTPrimeUtil;

public final class LegacyTNTPrimeListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onTNTPrime(TNTPrimeEvent event) {
        PrimeReason reason = event.getReason();
        if (!event.isCancelled() && reason != PrimeReason.EXPLOSION) {
            TNTPrimeUtil.logPrime(event.getBlock(), event.getPrimerEntity(), reason == PrimeReason.FIRE);
        }
    }
}
