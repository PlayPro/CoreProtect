package net.coreprotect.listener.channel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.World;

import net.coreprotect.CoreProtect;
import net.coreprotect.command.TabHandler;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;

public class PluginChannelHandshakeListener implements PluginMessageListener, Listener {

    public static final String pluginChannel = "coreprotect:handshake";
    private final int networkingProtocolVersion = 1;
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

    public boolean isPluginChannelPlayer(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            return false;
        }

        return getPluginChannelPlayers().contains(((Player) commandSender).getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        PluginChannelInputListener.getInstance().getSilentChatPlayers().remove(playerUuid);
        getPluginChannelPlayers().remove(playerUuid);
    }

    @Override
    public void onPluginMessageReceived(String s, Player player, byte[] bytes) {
        handleHandshake(s, player, bytes);
    }

    private void handleHandshake(String channel, Player player, byte[] bytes) {
        if (!player.hasPermission("coreprotect.networking")) {
            return;
        }

        if (!channel.equals(pluginChannel)) {
            return;
        }

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(in);

        try {
            String modVersion = dis.readUTF();
            String modId = dis.readUTF();
            int protocolVersion = dis.readInt();
            if (Config.getGlobal().NETWORK_DEBUG) {
                Chat.console(new String(bytes));
                Chat.console(modVersion);
                Chat.console(modId);
                Chat.console(String.valueOf(protocolVersion));
                Chat.console("");
            }

            if (protocolVersion != networkingProtocolVersion) {
                Chat.console(Phrase.build(Phrase.NETWORK_CONNECTION, player.getName(), modId, modVersion, Selector.SECOND));
                return;
            }

            getPluginChannelPlayers().add(player.getUniqueId());
            Chat.console(Phrase.build(Phrase.NETWORK_CONNECTION, player.getName(), modId, modVersion, Selector.FIRST));

            player.sendPluginMessage(CoreProtect.getInstance(), pluginChannel, sendRegistered());
        }
        catch (Exception exception) {
            Chat.console(exception.toString());
            exception.printStackTrace();
        }
    }

    private byte[] sendRegistered() throws IOException {
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);
        msgOut.writeBoolean(true);
        String[] actionsList = TabHandler.getActions();
        msgOut.writeInt(actionsList.length);
        for (String action : actionsList) {
            msgOut.writeUTF(action);
        }
        List<World> worlds = Bukkit.getServer().getWorlds();
        msgOut.writeInt(worlds.size());
        for (World world : worlds) {
            msgOut.writeUTF(world.getName());
        }
        msgOut.writeUTF(ConfigHandler.EDITION_NAME + " v" + CoreProtect.getInstance().getDescription().getVersion());

        return msgBytes.toByteArray();
    }
}
