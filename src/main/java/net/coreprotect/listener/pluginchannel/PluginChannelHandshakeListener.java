package net.coreprotect.listener.pluginchannel;

import net.coreprotect.CoreProtect;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Util;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PluginChannelHandshakeListener implements PluginMessageListener, Listener
{
    private final Set<UUID> pluginChannelPlayers;
    private static PluginChannelHandshakeListener instance;

    public PluginChannelHandshakeListener() {
        instance = this;
        pluginChannelPlayers = new HashSet<>();
    }

    public static PluginChannelHandshakeListener getInstance() {
        return instance;
    }

    public Set<UUID> getPluginChannelPlayers() {
        return pluginChannelPlayers;
    }

    public boolean isPluginChannelPlayer(UUID uuid) {
        return getPluginChannelPlayers().contains(uuid);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        getPluginChannelPlayers().remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String s, @NotNull Player player, byte[] bytes)
    {
        handleHandshake(s, player, bytes);
    }

    private void handleHandshake(String channel, Player player, byte[] bytes)
    {
        if (!player.hasPermission("coreprotect.register")) {
            return;
        }

        if (!channel.equals(CoreProtect.COREPROTECT_PLUGIN_CHANNEL_HANDSHAKE)) {
            return;
        }

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(in);

        try
        {
            Util.networkDebug(new String(bytes));
            String modVersion = dis.readUTF();
            Util.networkDebug(modVersion);
            String modId = dis.readUTF();
            Util.networkDebug(modId);
            int protocolVersion = dis.readInt();
            Util.networkDebug(String.valueOf(protocolVersion));

            if (protocolVersion != CoreProtect.COREPROTECT_NETWORKING_PROTOCOL) {
                Chat.console("Player " + player.getName() + " failed registering the CoreProtect channel using " + modId + " " + modVersion + " with protocol version " + protocolVersion);
            }

            getPluginChannelPlayers().add(player.getUniqueId());
            Chat.console("Player " + player.getName() + " registered the CoreProtect channel using " + modId + " " + modVersion + " with protocol version " + protocolVersion);

            player.sendPluginMessage(CoreProtect.getInstance(), CoreProtect.COREPROTECT_PLUGIN_CHANNEL_HANDSHAKE, sendRegistered());
        } catch (Exception exception) {
            Chat.console(exception.toString());
            exception.printStackTrace();
        }
    }

    private byte[] sendRegistered() throws IOException
    {
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);
        msgOut.writeBoolean(true);

        return msgBytes.toByteArray();
    }
}
