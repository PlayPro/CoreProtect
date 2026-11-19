package net.coreprotect.listener.player.inspector;

import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.lookup.BlockLookup;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;

public class BlockInspector extends BaseInspector {

    public void performBlockLookup(Player player, BlockState blockState) {
        Location signLocation = blockState instanceof Sign && player.getGameMode() != GameMode.CREATIVE ? blockState.getLocation() : null;
        inspectHistory(player, blockState, signLocation, null);
    }

    public void performAirBlockLookup(Player player, BlockState blockState) {
        inspectHistory(player, blockState, null, null);
    }

    public void performEntityLookup(Player player, BlockState blockState, UUID entityUuid) {
        inspectHistory(player, blockState, null, entityUuid);
    }

    private void inspectHistory(Player player, BlockState blockState, Location signLocation, UUID entityUuid) {
        startInspection(player, () -> {
            withStatement(player, (connection, statement) -> {
                String result = entityUuid == null ? BlockLookup.performLookup(null, statement, blockState, player, 0, 1, 7)
                        : BlockLookup.performEntityLookup(null, statement, blockState, player, 1, 7, entityUuid);
                Chat.sendLookupComponents(player, result);
            });
            if (signLocation != null) {
                scheduleSignRefresh(player, signLocation);
            }
        });
    }

    private void scheduleSignRefresh(Player player, Location location) {
        CoreProtect plugin = CoreProtect.getInstance();
        Scheduler.scheduleSyncDelayedTask(plugin, () -> {
            if (!location.isWorldLoaded()) {
                return;
            }

            World world = location.getWorld();
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                return;
            }

            BlockState currentState = location.getBlock().getState();
            if (currentState instanceof Sign) {
                Runnable send = () -> {
                    if (player.isOnline() && world.equals(player.getWorld())) {
                        BukkitAdapter.ADAPTER.sendSignUpdate(player, location, (Sign) currentState);
                    }
                };
                if (ConfigHandler.isFolia) {
                    Scheduler.runTask(plugin, send, player);
                }
                else {
                    send.run();
                }
            }
        }, location, 30);
    }
}
