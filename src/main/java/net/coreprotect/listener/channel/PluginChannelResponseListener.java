package net.coreprotect.listener.channel;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.utility.Chat;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public class PluginChannelResponseListener implements Listener {

    public static final String pluginChannel = "coreprotect:response";
    private static PluginChannelResponseListener instance;

    public PluginChannelResponseListener() {
        instance = this;
    }

    public static PluginChannelResponseListener getInstance() {
        return instance;
    }

    public void sendData(CommandSender commandSender, String message, String type) {
        if (!PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(commandSender)) {
            return;
        }

        try
        {
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeUTF(type);
            msgOut.writeUTF(message);
            if (Config.getGlobal().NETWORK_DEBUG) {
                Chat.console(type);
                Chat.console(message);
                Chat.console("");
            }

            send(commandSender, msgBytes.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void send(CommandSender commandSender, byte[] msgBytes) {
        if (!(commandSender instanceof Player)) {
            return;
        }

        PluginChannelResponseListener.getInstance().sendCoreProtectData((Player) commandSender, msgBytes);
    }

    private void sendCoreProtectData(Player player, byte[] data) {
        if (!player.hasPermission("coreprotect.networking")) {
            return;
        }

        player.sendPluginMessage(CoreProtect.getInstance(), pluginChannel, data);
    }
}
