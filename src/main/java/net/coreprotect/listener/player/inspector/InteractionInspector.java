package net.coreprotect.listener.player.inspector;

import java.sql.Connection;
import java.sql.Statement;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import net.coreprotect.database.lookup.InteractionLookup;
import net.coreprotect.utility.Chat;

public class InteractionInspector extends BaseInspector {

    public void performInteractionLookup(final Player player, final Block finalInteractBlock) {
        class BasicThread implements Runnable {
            @Override
            public void run() {
                try {
                    checkPreconditions(player);

                    try (Connection connection = getDatabaseConnection(player)) {
                        Statement statement = connection.createStatement();
                        String blockData = InteractionLookup.performLookup(null, statement, finalInteractBlock, player, 0, 1, 7);

                        if (blockData.contains("\n")) {
                            for (String splitData : blockData.split("\n")) {
                                Chat.sendComponent(player, splitData);
                            }
                        }
                        else {
                            Chat.sendComponent(player, blockData);
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
