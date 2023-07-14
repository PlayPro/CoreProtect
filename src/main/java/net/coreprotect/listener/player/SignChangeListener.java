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
import net.coreprotect.paper.PaperAdapter;

public final class SignChangeListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        if (event.isCancelled() || !Config.getConfig(block.getWorld()).SIGN_TEXT) {
            return;
        }

        String player = event.getPlayer().getName();
        Location location = block.getLocation();
        BlockState blockState = block.getState();

        String line1 = "";
        String line2 = "";
        String line3 = "";
        String line4 = "";
        String line5 = "";
        String line6 = "";
        String line7 = "";
        String line8 = "";
        int color = 0;
        int colorSecondary = 0;
        boolean frontGlowing = false;
        boolean backGlowing = false;
        boolean isWaxed = false;
        boolean isFront = BukkitAdapter.ADAPTER.isSignFront(event);
        boolean existingText = false;

        if (blockState instanceof Sign) {
            Sign sign = (Sign) blockState;
            line1 = PaperAdapter.ADAPTER.getLine(sign, 0);
            line2 = PaperAdapter.ADAPTER.getLine(sign, 1);
            line3 = PaperAdapter.ADAPTER.getLine(sign, 2);
            line4 = PaperAdapter.ADAPTER.getLine(sign, 3);
            line5 = PaperAdapter.ADAPTER.getLine(sign, 4);
            line6 = PaperAdapter.ADAPTER.getLine(sign, 5);
            line7 = PaperAdapter.ADAPTER.getLine(sign, 6);
            line8 = PaperAdapter.ADAPTER.getLine(sign, 7);
            color = BukkitAdapter.ADAPTER.getColor(sign, true);
            colorSecondary = BukkitAdapter.ADAPTER.getColor(sign, false);
            frontGlowing = BukkitAdapter.ADAPTER.isGlowing(sign, true);
            backGlowing = BukkitAdapter.ADAPTER.isGlowing(sign, false);
            isWaxed = BukkitAdapter.ADAPTER.isWaxed(sign);

            if (line1.length() > 0 || line2.length() > 0 || line3.length() > 0 || line4.length() > 0 || line5.length() > 0 || line6.length() > 0 || line7.length() > 0 || line8.length() > 0) {
                existingText = true;
                Queue.queueSignText(player, location, 0, color, colorSecondary, frontGlowing, backGlowing, isWaxed, isFront, line1, line2, line3, line4, line5, line6, line7, line8, 1);
                Queue.queueBlockPlace(player, blockState, block.getType(), blockState, block.getType(), -1, 0, blockState.getBlockData().getAsString());
            }
        }

        if (isFront) {
            line1 = event.getLine(0);
            line2 = event.getLine(1);
            line3 = event.getLine(2);
            line4 = event.getLine(3);
        }
        else {
            line5 = event.getLine(0);
            line6 = event.getLine(1);
            line7 = event.getLine(2);
            line8 = event.getLine(3);
        }

        if (existingText || line1.length() > 0 || line2.length() > 0 || line3.length() > 0 || line4.length() > 0 || line5.length() > 0 || line6.length() > 0 || line7.length() > 0 || line8.length() > 0) {
            Queue.queueSignText(player, location, 1, color, colorSecondary, frontGlowing, backGlowing, isWaxed, isFront, line1, line2, line3, line4, line5, line6, line7, line8, 0);
        }
    }
}
