package net.coreprotect.listener.player.inspector;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.coreprotect.database.lookup.SignMessageLookup;
import net.coreprotect.utility.Chat;

public class SignInspector extends BaseInspector {

    public void performSignLookup(final Player player, final Location location) {
        class BasicThread implements Runnable {
            @Override
            public void run() {
                try {
                    checkPreconditions(player);

                    try (Connection connection = getDatabaseConnection(player)) {
                        Statement statement = connection.createStatement();
                        List<String> signData = SignMessageLookup.performLookup(null, statement, location, player, 1, 7);
                        for (String signMessage : signData) {
                            String bypass = null;

                            if (signMessage.contains("\n")) {
                                String[] split = signMessage.split("\n");
                                signMessage = split[0];
                                bypass = split[1];
                            }

                            if (signMessage.length() > 0) {
                                Chat.sendComponent(player, signMessage, bypass);
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
