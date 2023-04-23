package net.coreprotect.listener.channel;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.coreprotect.command.NetworkCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;

import net.coreprotect.config.Config;
import net.coreprotect.utility.Chat;
import org.jetbrains.annotations.NotNull;

public class PluginChannelInputListener implements PluginMessageListener, Listener {

    public static final String pluginChannel = "coreprotect:input";
    private static PluginChannelInputListener instance;
    private final Set<UUID> silentChatPlayers;

    public PluginChannelInputListener() {
        instance = this;
        silentChatPlayers = new HashSet<>();
    }

    public static PluginChannelInputListener getInstance() {
        return instance;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String s, @NotNull Player player, byte[] bytes) {
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
            String input = dis.readUTF();
            boolean silentChat = dis.readBoolean();

            if (Config.getGlobal().NETWORK_DEBUG) {
                Chat.console(new String(bytes));
                Chat.console(input);
                Chat.console(String.valueOf(silentChat));
                Chat.console("");
            }

            String command;
            if (hasPermission(player, "co")) {
                command = "co";
            }
            else if (hasPermission(player, "core")) {
                command = "core";
            }
            else if (hasPermission(player, "coreprotect")) {
                command = "coreprotect";
            }
            else {
                command = "co";
            }

            if (silentChat) {
                // Silent chat if player enabled option
                getSilentChatPlayers().add(player.getUniqueId());
            }

            String[] inputData = input.split(" ");
            String type = inputData[0];
            boolean hasPermission = hasPermission(player, type);

            NetworkCommand.runServerSideCommand(player, command, hasPermission, inputData);
        }
        catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private boolean hasPermission(Player player, String type) {
        return player.hasPermission("coreprotect." + type);
    }

    public Set<UUID> getSilentChatPlayers() {
        return silentChatPlayers;
    }

    public boolean isNotSilentChatPlayer(CommandSender commandSender) {
        if (!(commandSender instanceof Player && PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(commandSender))) {
            return true;
        }

        return !getSilentChatPlayers().contains(((Player) commandSender).getUniqueId());
    }
}
