package net.coreprotect.listener.player;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

public final class SignChangeListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onSignChange(SignChangeEvent event) {
        String player = event.getPlayer().getName();
        Block block = event.getBlock();
        Location location = block.getLocation();
        BlockState blockState = block.getState();
        String line1 = event.getLine(0);
        String line2 = event.getLine(1);
        String line3 = event.getLine(2);
        String line4 = event.getLine(3);
        int color = (blockState instanceof Sign) ? ((Sign) blockState).getColor().getColor().asRGB() : 0;
        boolean isGlowing = blockState instanceof Sign && BukkitAdapter.ADAPTER.isGlowing((Sign) blockState);

        if (!event.isCancelled() && Config.getConfig(block.getWorld()).SIGN_TEXT) {
            Queue.queueSignText(player, location, 1, color, isGlowing, line1, line2, line3, line4, 0);
        }
    }
}
