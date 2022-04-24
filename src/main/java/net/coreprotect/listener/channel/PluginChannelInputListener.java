package net.coreprotect.listener.channel;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.utility.Chat;

public class PluginChannelInputListener implements PluginMessageListener, Listener {

    public static final String pluginChannel = "coreprotect:input";
    private static PluginChannelInputListener instance;

    public PluginChannelInputListener() {
        instance = this;
    }

    public static PluginChannelInputListener getInstance() {
        return instance;
    }

    @Override
    public void onPluginMessageReceived(String s, Player player, byte[] bytes) {
        handleInput(s, player, bytes);
    }

    private void handleInput(String channel, Player player, byte[] bytes) {
        if (!PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(player)) {
            return;
        }

        if (!channel.equals(pluginChannel)) {
            return;
        }

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(in);

        try {
            String search = dis.readUTF();
            int pages = dis.readInt();
            int amountRows = dis.readInt();

            if (Config.getGlobal().NETWORK_DEBUG) {
                Chat.console(new String(bytes));
                Chat.console(search);
                Chat.console(String.valueOf(pages));
                Chat.console(String.valueOf(amountRows));
            }

            String command;
            if (player.hasPermission("coreprotect.co")) {
                command = "co";
            }
            else if (player.hasPermission("coreprotect.core")) {
                command = "core";
            }
            else if (player.hasPermission("coreprotect.coreprotect")) {
                command = "coreprotect";
            }
            else {
                command = "co";
            }

            Bukkit.dispatchCommand(player, command + " " + search);

            if (pages == 1 || !search.contains("lookup")) {
                return;
            }

            class sendLookupPages implements Runnable {
                @Override
                public void run() {
                    try {
                        for (int page = 2; page <= pages; page++)
                        {
                            Thread.sleep(100);
                            Bukkit.dispatchCommand(player, command + " lookup " + page + ":" + amountRows);
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            Bukkit.getScheduler().runTask(CoreProtect.getInstance(), new sendLookupPages());
        }
        catch (Exception exception) {
            Chat.console(exception.toString());
            exception.printStackTrace();
        }
    }
}
