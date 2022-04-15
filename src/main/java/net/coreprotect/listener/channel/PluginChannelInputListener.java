package net.coreprotect.listener.channel;

import net.coreprotect.config.Config;
import net.coreprotect.utility.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
            int time = dis.readInt();
            List<String> restrictUsers = readStringList(dis);
            List<String> excludedUsers = readStringList(dis);
            List<Object> restrictBlocks = readObjectList(dis);
            List<Object> excludedBlocks = readObjectList(dis);
            List<Integer> actionList = readIntList(dis);
            int radius = dis.readInt();
            Location location = player.getLocation();
            String world = dis.readUTF();
            double x = dis.readDouble();
            double y = dis.readDouble();
            double z = dis.readDouble();
            if (!world.isEmpty() && Bukkit.getServer().getWorld(world) != null) {
                location.setWorld(Bukkit.getServer().getWorld(world));
            }
            if (x != 0) {
                location.setX(x);
            }
            if (y != 0) {
                location.setY(y);
            }
            if (z != 0) {
                location.setZ(z);
            }

            if (Config.getGlobal().NETWORK_DEBUG) {
                Chat.console(new String(bytes));
                Chat.console(String.valueOf(time));
                Chat.console(String.valueOf(restrictUsers));
                Chat.console(String.valueOf(excludedUsers));
                Chat.console(String.valueOf(restrictBlocks));
                Chat.console(String.valueOf(excludedBlocks));
                Chat.console(String.valueOf(actionList));
                Chat.console(String.valueOf(radius));
                Chat.console(location.toString());
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

            StringBuilder cmd = new StringBuilder();
            cmd.append(command).append(" lookup ").append(time).append(" ").append(restrictUsers);
            cmd.append(" ").append(excludedUsers).append(" ").append(restrictBlocks).append(" ").append(excludedBlocks);
            cmd.append(" ").append(actionList).append(" ").append(radius);

            Bukkit.dispatchCommand(player, cmd.toString());
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
