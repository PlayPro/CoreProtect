package net.coreprotect.listener.channel;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.coreprotect.command.NetworkCommand;
import net.coreprotect.config.ConfigHandler;
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
    private final Set<UUID> playersUsingInputChannel;

    public PluginChannelInputListener() {
        instance = this;
        silentChatPlayers = new HashSet<>();
        playersUsingInputChannel = new HashSet<>();
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

        playersUsingInputChannel.add(player.getUniqueId());

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(in);

        try {
            String input = dis.readUTF();
            final int[] pages = {dis.readInt()};
            int amountRows = dis.readInt();
            boolean silentChat = dis.readBoolean();

            if (Config.getGlobal().NETWORK_DEBUG) {
                Chat.console(new String(bytes));
                Chat.console(input);
                Chat.console(String.valueOf(pages[0]));
                Chat.console(String.valueOf(amountRows));
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

            String[] inputData = input.split(";");
            String type = inputData[0];
            boolean hasPermission = hasPermission(player, type);

            if (type.equals("lookup")) {
                inputData = (input + ";1:" + amountRows).split(";");
            }

            NetworkCommand.runServerSideCommand(player, command, hasPermission, inputData);

            class sendLookupPages implements Runnable {
                @Override
                public void run() {
                    try {
                        //Hopefully page has been saved by now
                        Thread.sleep(100);
                        boolean hasPlayerPages = ConfigHandler.playerPages.containsKey(player.getUniqueId());

                        if (type.equals("lookup") && hasPlayerPages) {
                            int playerPages = ConfigHandler.playerPages.get(player.getUniqueId());
                            if (pages[0] > playerPages) {
                                pages[0] = playerPages;
                            }

                            for (int page = 2; page < pages[0]; page++) {
                                Thread.sleep(100);
                                NetworkCommand.runServerSideCommand(player, command, hasPermission, new String[]{type, page + ":" + amountRows});
                            }
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    finally {
                        UUID playerUuid = player.getUniqueId();
                        ConfigHandler.playerPages.remove(playerUuid);

                        // Sent finished flag
                        // Disable silent chat if player enabled option
                        getSilentChatPlayers().remove(playerUuid);
                        getPlayersUsingInputChannel().remove(playerUuid);
                    }
                }
            }
            Thread lookupPagesThread = new Thread(new sendLookupPages());
            lookupPagesThread.start();
        }
        catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private boolean hasPermission(Player player, String type) {
        return player.hasPermission("coreprotect." + type);
    }

    public boolean isPlayerUsingInputChannel(CommandSender commandSender) {
        if (!(commandSender instanceof Player) || !PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(commandSender)) {
            return false;
        }

        return true;
    }

    public Set<UUID> getPlayersUsingInputChannel() {
        return playersUsingInputChannel;
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
