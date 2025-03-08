package net.coreprotect.listener.player.inspector;

import java.sql.Connection;
import java.sql.Statement;

import org.bukkit.GameMode;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import net.coreprotect.database.lookup.BlockLookup;
import net.coreprotect.utility.Chat;

public class BlockInspector extends BaseInspector {

    public void performBlockLookup(final Player player, final BlockState blockState) {
        class BasicThread implements Runnable {
            @Override
            public void run() {
                try {
                    checkPreconditions(player);

                    try (Connection connection = getDatabaseConnection(player)) {
                        Statement statement = connection.createStatement();

                        String resultData = BlockLookup.performLookup(null, statement, blockState, player, 0, 1, 7);
                        if (resultData.contains("\n")) {
                            for (String b : resultData.split("\n")) {
                                Chat.sendComponent(player, b);
                            }
                        }
                        else if (resultData.length() > 0) {
                            Chat.sendComponent(player, resultData);
                        }

                        statement.close();

                        if (blockState instanceof Sign && player.getGameMode() != GameMode.CREATIVE) {
                            Thread.sleep(1500);
                            Sign sign = (Sign) blockState;
                            player.sendSignChange(sign.getLocation(), sign.getLines(), sign.getColor());
                        }
                    }
                }
                catch (InspectionException e) {
                    Chat.sendMessage(player, e.getMessage());
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    finishInspection(player);
                }
            }
        }

        Runnable runnable = new BasicThread();
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public void performAirBlockLookup(final Player player, final BlockState finalBlock) {
        class BasicThread implements Runnable {
            @Override
            public void run() {
                try {
                    checkPreconditions(player);

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
                    e.printStackTrace();
                }
                finally {
                    finishInspection(player);
                }
            }
        }

        Runnable runnable = new BasicThread();
        Thread thread = new Thread(runnable);
        thread.start();
    }
}
