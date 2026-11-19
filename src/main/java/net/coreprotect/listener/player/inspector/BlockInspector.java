package net.coreprotect.listener.player.inspector;

import java.sql.Connection;
import java.sql.Statement;

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
import net.coreprotect.utility.ErrorReporter;

public class BlockInspector extends BaseInspector {

    public void performBlockLookup(final Player player, final BlockState blockState) {
        Location signLocation = blockState instanceof Sign && player.getGameMode() != GameMode.CREATIVE ? blockState.getLocation() : null;
        startInspection(player, () -> {
            try {
                try (Connection connection = getDatabaseConnection(player); Statement statement = connection.createStatement()) {
                    String resultData = BlockLookup.performLookup(null, statement, blockState, player, 0, 1, 7);
                    if (resultData.contains("\n")) {
                        for (String line : resultData.split("\n")) {
                            Chat.sendComponent(player, line);
                        }
                    }
                    else if (resultData.length() > 0) {
                        Chat.sendComponent(player, resultData);
                    }
                }

                if (signLocation != null) {
                    scheduleSignRefresh(player, signLocation);
                }
            }
            catch (InspectionException e) {
                Chat.sendMessage(player, e.getMessage());
            }
            catch (Exception e) {
                ErrorReporter.report(e);
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

    public void performAirBlockLookup(final Player player, final BlockState finalBlock) {
        class BasicThread implements Runnable {
            @Override
            public void run() {
                try {
                    try (Connection connection = getDatabaseConnection(player)) {
                        Statement statement = connection.createStatement();
                        if (finalBlock.getType().name().endsWith("AIR")) {
                            String blockData = BlockLookup.performLookup(null, statement, finalBlock, player, 0, 1, 7);

                            if (blockData.contains("\n")) {
                                for (String b : blockData.split("\n")) {
                                    Chat.sendComponent(player, b);
                                }
                            }
                            else if (blockData.length() > 0) {
                                Chat.sendComponent(player, blockData);
                            }
                        }
                        else {
                            String blockData = BlockLookup.performLookup(null, statement, finalBlock, player, 0, 1, 7);
                            if (blockData.contains("\n")) {
                                for (String splitData : blockData.split("\n")) {
                                    Chat.sendComponent(player, splitData);
                                }
                            }
                            else if (blockData.length() > 0) {
                                Chat.sendComponent(player, blockData);
                            }
                        }

                        statement.close();
                    }
                }
                catch (InspectionException e) {
                    Chat.sendMessage(player, e.getMessage());
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }

        startInspection(player, new BasicThread());
    }
}
