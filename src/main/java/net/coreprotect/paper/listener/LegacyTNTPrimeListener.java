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
        boolean explosions = Config.getConfig(block.getWorld()).EXPLOSIONS;
        boolean alreadyLogged = event.getReason() == TNTPrimeEvent.PrimeReason.EXPLOSION;
        String user = TNTPrimeUtil.getUser(event.getPrimerEntity());
        if (event.isCancelled() || block.getType() != Material.TNT || !explosions || alreadyLogged) {
            return;
        }
        else if (event.getReason() == TNTPrimeEvent.PrimeReason.FIRE) {
            user = TNTPrimeUtil.getFireUser(block, user);
        }

        Queue.queueBlockBreak(user, block.getState(), Material.TNT, block.getBlockData().getAsString(), 0);
    }

}
