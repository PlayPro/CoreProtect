package net.coreprotect.listener.pluginchannel;

import net.coreprotect.CoreProtect;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Util;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

public class PluginChannelListener implements Listener
{
    private static PluginChannelListener instance;

    public PluginChannelListener() {
        instance = this;
    }

    public static PluginChannelListener getInstance() {
        return instance;
    }

    public void sendData(CommandSender commandSender, long timeAgo, Phrase phrase, String selector, String resultUser, String target, int amount, int x, int y, int z, int worldId, String rbFormat, boolean isContainer, boolean added) throws IOException
    {
        String phraseSelector = Phrase.getPhraseSelector(phrase, selector);
        String worldName = Util.getWorldName(worldId);

        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);
        msgOut.writeInt(1);
        msgOut.writeLong(timeAgo * 1000);
        msgOut.writeUTF(phraseSelector);
        msgOut.writeUTF(resultUser);
        msgOut.writeUTF(target);
        msgOut.writeInt(amount);
        msgOut.writeInt(x);
        msgOut.writeInt(y);
        msgOut.writeInt(z);
        msgOut.writeUTF(worldName);
        msgOut.writeBoolean(!rbFormat.isEmpty());
        msgOut.writeBoolean(isContainer);
        msgOut.writeBoolean(added);
        Util.networkDebug(String.valueOf(timeAgo*1000));
        Util.networkDebug(phraseSelector);
        Util.networkDebug(resultUser);
        Util.networkDebug(target);
        Util.networkDebug(String.valueOf(amount));
        Util.networkDebug(String.valueOf(x));
        Util.networkDebug(String.valueOf(y));
        Util.networkDebug(String.valueOf(z));
        Util.networkDebug(worldName);
        Util.networkDebug(String.valueOf(!rbFormat.isEmpty()));
        Util.networkDebug(String.valueOf(isContainer));
        Util.networkDebug(String.valueOf(added));
        Util.networkDebug("");

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendInfoData(CommandSender commandSender, long timeAgo, Phrase phrase, String selector, String resultUser, int amount, int x, int y, int z, int worldId) throws IOException
    {
        String phraseSelector = Phrase.getPhraseSelector(phrase, selector);
        String worldName = Util.getWorldName(worldId);

        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);

        msgOut.writeInt(2);
        msgOut.writeLong(timeAgo * 1000);
        msgOut.writeUTF(phraseSelector);
        msgOut.writeUTF(resultUser);
        msgOut.writeInt(amount);
        msgOut.writeInt(x);
        msgOut.writeInt(y);
        msgOut.writeInt(z);
        msgOut.writeUTF(worldName);
        Util.networkDebug(String.valueOf(timeAgo*1000));
        Util.networkDebug(phraseSelector);
        Util.networkDebug(resultUser);
        Util.networkDebug(String.valueOf(amount));
        Util.networkDebug(String.valueOf(x));
        Util.networkDebug(String.valueOf(y));
        Util.networkDebug(String.valueOf(z));
        Util.networkDebug(worldName);
        Util.networkDebug("");

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendMessageData(CommandSender commandSender, long timeAgo, String resultUser, String message, boolean sign, int x, int y, int z, int worldId) throws IOException
    {
        String worldName = Util.getWorldName(worldId);

        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);

        msgOut.writeInt(3);
        msgOut.writeLong(timeAgo * 1000);
        msgOut.writeUTF(resultUser);
        msgOut.writeUTF(message);
        msgOut.writeBoolean(sign);
        msgOut.writeInt(x);
        msgOut.writeInt(y);
        msgOut.writeInt(z);
        msgOut.writeUTF(worldName);
        Util.networkDebug(String.valueOf(timeAgo*1000));
        Util.networkDebug(resultUser);
        Util.networkDebug(message);
        Util.networkDebug(String.valueOf(sign));
        Util.networkDebug(String.valueOf(x));
        Util.networkDebug(String.valueOf(y));
        Util.networkDebug(String.valueOf(z));
        Util.networkDebug(worldName);
        Util.networkDebug("");

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendUsernameData(CommandSender commandSender, long timeAgo, String resultUser, String target) throws IOException
    {
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);

        msgOut.writeInt(4);
        msgOut.writeLong(timeAgo * 1000);
        msgOut.writeUTF(resultUser);
        msgOut.writeUTF(target);
        Util.networkDebug(String.valueOf(timeAgo*1000));
        Util.networkDebug(resultUser);
        Util.networkDebug(target);
        Util.networkDebug("");

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendTest(CommandSender commandSender, String type) throws IOException
    {
        int worldId = 1;
        Random rand = new Random();
        int timeAgo = rand.nextInt(20);
        String selector = Selector.FIRST;
        String resultUser = "Anne";
        int amount = 5;
        int x = rand.nextInt(10);
        int y = rand.nextInt(10);
        int z = rand.nextInt(10);
        String rbFormat = "test";
        String message = "This is a test";
        boolean sign = true;

        switch (type) {
            case "2":
                sendInfoData(commandSender, timeAgo, Phrase.LOOKUP_LOGIN, selector, resultUser, amount, x, y, z, worldId);
                break;
            case "3":
                sendMessageData(commandSender, timeAgo, resultUser, message, sign, x, y, z, worldId);
                break;
            case "4":
                 sendUsernameData(commandSender, timeAgo, resultUser, "Arne");
                break;
            default:
                sendData(commandSender, timeAgo, Phrase.LOOKUP_CONTAINER, selector, resultUser, "clay_ball", amount, x, y, z, worldId, rbFormat, false, true);
                break;
        }

    }

    private void send(CommandSender commandSender, byte[] msgBytes) {
        if (commandSender instanceof ConsoleCommandSender) {
            return;
        }

        PluginChannelListener.getInstance().sendCoreProtectPluginChannel((Player) commandSender, msgBytes);
    }

    public void sendCoreProtectPluginChannel(Player player, byte[] data) {
        if (PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(player.getUniqueId())) {
            sendCoreProtectData(player, data);
        }
    }

    private void sendCoreProtectData(Player player, byte[] data) {
        if (!player.hasPermission("coreprotect.register")) {
            return;
        }

        player.sendPluginMessage(CoreProtect.getInstance(), CoreProtect.coreProtectPluginChannelData, data);
    }
}
