package net.coreprotect.listener.channel;

import net.coreprotect.config.Config;
import net.coreprotect.utility.Chat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

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

            if (Config.getGlobal().NETWORK_DEBUG) {
                Chat.console(new String(bytes));
                Chat.console(search);
                Chat.console(String.valueOf(pages));
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

            Bukkit.dispatchCommand(player, command + " lookup " + search);
        }
        catch (Exception exception) {
            Chat.console(exception.toString());
            exception.printStackTrace();
        }
    }

    private List<String> readStringList(DataInputStream dis) throws IOException {
        int listCount = dis.readInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < listCount; i++)
        {
            list.add(dis.readUTF());
        }
        return list;
    }

    private List<Object> readObjectList(DataInputStream dis) throws IOException {
        int listCount = dis.readInt();
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < listCount; i++)
        {
            list.add(dis.readUTF());
        }
        return list;
    }

    private List<Integer> readIntList(DataInputStream dis) throws IOException {
        int listCount = dis.readInt();
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < listCount; i++)
        {
            list.add(dis.readInt());
        }
        return list;
    }
}
