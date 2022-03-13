package net.coreprotect.listener.pluginchannel;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.coreprotect.CoreProtect;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Util;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChannelEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PluginChannelListener implements Listener
{
    private final Set<UUID> pluginChannelPlayers;
    private static PluginChannelListener instance;

    public PluginChannelListener() {
        instance = this;
        pluginChannelPlayers = new HashSet<>();
    }

    public static PluginChannelListener getInstance() {
        return instance;
    }

    public Set<UUID> getPluginChannelPlayers() {
        return pluginChannelPlayers;
    }

    @EventHandler
    public void onPlayerRegisterChannel(PlayerRegisterChannelEvent event) {
        handleChannelEvent(event, Action.REGISTER);
    }

    @EventHandler
    public void onPlayerUnregisterChannel(PlayerUnregisterChannelEvent event) {
        handleChannelEvent(event, Action.UNREGISTER);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        getPluginChannelPlayers().remove(event.getPlayer().getUniqueId());
    }

    public void sendData(CommandSender commandSender, String timeAgo, Phrase phrase, String selector, String resultUser, String target, int amount, int x, int y, int z, int worldId, String rbFormat) throws IOException
    {
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);
        msgOut.writeUTF(timeAgo);
        msgOut.writeUTF(Phrase.getPhraseSelector(phrase, selector));
        msgOut.writeUTF(resultUser);
        msgOut.writeUTF(target);
        msgOut.writeInt(amount);
        msgOut.writeInt(x);
        msgOut.writeInt(y);
        msgOut.writeInt(z);
        msgOut.writeUTF(Util.getWorldName(worldId));
        msgOut.writeBoolean(!rbFormat.isEmpty());

        send(commandSender, msgBytes);
    }

    public void sendSignData(CommandSender commandSender, String timeAgo, String resultUser, String message, int x, int y, int z, int worldId) throws IOException
    {
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);
        msgOut.writeUTF(timeAgo);
        msgOut.writeUTF(resultUser);
        msgOut.writeUTF(message);
        msgOut.writeInt(x);
        msgOut.writeInt(y);
        msgOut.writeInt(z);
        msgOut.writeUTF(Util.getWorldName(worldId));

        send(commandSender, msgBytes);
    }

    private void send(CommandSender commandSender, ByteArrayOutputStream msgBytes) {
        if (commandSender instanceof ConsoleCommandSender) {
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeShort(msgBytes.toByteArray().length);
        out.write(msgBytes.toByteArray());
        PluginChannelListener.getInstance().sendCoreProtectPluginChannel((Player) commandSender, out.toByteArray());
    }

    public void sendCoreProtectPluginChannel(Player player, byte[] data) {
        if (getPluginChannelPlayers().contains(player.getUniqueId())) {
            sendCoreProtectData(player, data);
        }
    }

    private void handleChannelEvent(PlayerChannelEvent event, Action action) {
        if (!event.getChannel().equals(CoreProtect.COREPROTECT_PLUGIN_CHANNEL)) {
            return;
        }

        Player player = event.getPlayer();
        switch (action) {
            case REGISTER:
                getPluginChannelPlayers().add(player.getUniqueId());
                Chat.console("Player " + player.getName() + " registered the CoreProtect channel");
                break;
            case UNREGISTER:
                getPluginChannelPlayers().remove(player.getUniqueId());
                Chat.console("Player " + player.getName() + " unregistered the CoreProtect channel");
                break;
        }
    }

    private void sendCoreProtectData(Player player, byte[] data) {
        if (!player.hasPermission("coreprotect.register")) {
            return;
        }

        player.sendPluginMessage(CoreProtect.getInstance(), CoreProtect.COREPROTECT_PLUGIN_CHANNEL, data);
    }

    public enum Action {
        REGISTER,
        UNREGISTER
    }
}
