package net.coreprotect.listener.pluginchannel;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public class PluginChannelListener implements Listener {

    public static final String pluginChannel = "coreprotect:data";
    private static PluginChannelListener instance;

    public PluginChannelListener() {
        instance = this;
    }

    public static PluginChannelListener getInstance() {
        return instance;
    }

    public void sendData(CommandSender commandSender, long timeAgo, Phrase phrase, String selector, String resultUser, String target, int amount, int x, int y, int z, int worldId, String rbFormat, boolean isContainer, boolean added) throws IOException {
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
        if (Config.getGlobal().NETWORKING_DEBUG) {
            Chat.console(String.valueOf(timeAgo * 1000));
            Chat.console(phraseSelector);
            Chat.console(resultUser);
            Chat.console(target);
            Chat.console(String.valueOf(amount));
            Chat.console(String.valueOf(x));
            Chat.console(String.valueOf(y));
            Chat.console(String.valueOf(z));
            Chat.console(worldName);
            Chat.console(String.valueOf(!rbFormat.isEmpty()));
            Chat.console(String.valueOf(isContainer));
            Chat.console(String.valueOf(added));
            Chat.console("");
        }

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendInfoData(CommandSender commandSender, long timeAgo, Phrase phrase, String selector, String resultUser, int amount, int x, int y, int z, int worldId) throws IOException {
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
        if (Config.getGlobal().NETWORKING_DEBUG) {
            Chat.console(String.valueOf(timeAgo * 1000));
            Chat.console(phraseSelector);
            Chat.console(resultUser);
            Chat.console(String.valueOf(amount));
            Chat.console(String.valueOf(x));
            Chat.console(String.valueOf(y));
            Chat.console(String.valueOf(z));
            Chat.console(worldName);
            Chat.console("");
        }

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendMessageData(CommandSender commandSender, long timeAgo, String resultUser, String message, boolean sign, int x, int y, int z, int worldId) throws IOException {
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
        if (Config.getGlobal().NETWORKING_DEBUG) {
            Chat.console(String.valueOf(timeAgo * 1000));
            Chat.console(resultUser);
            Chat.console(message);
            Chat.console(String.valueOf(sign));
            Chat.console(String.valueOf(x));
            Chat.console(String.valueOf(y));
            Chat.console(String.valueOf(z));
            Chat.console(worldName);
            Chat.console("");
        }

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendUsernameData(CommandSender commandSender, long timeAgo, String resultUser, String target) throws IOException {
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);

        msgOut.writeInt(4);
        msgOut.writeLong(timeAgo * 1000);
        msgOut.writeUTF(resultUser);
        msgOut.writeUTF(target);

        if (Config.getGlobal().NETWORKING_DEBUG) {
            Chat.console(String.valueOf(timeAgo * 1000));
            Chat.console(resultUser);
            Chat.console(target);
            Chat.console("");
        }

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendTest(CommandSender commandSender, String type) throws IOException {
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
        commandSender.sendMessage(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NETWORK_TESTDATA));
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
        if (!player.hasPermission("coreprotect.networking")) {
            return;
        }

        player.sendPluginMessage(CoreProtect.getInstance(), pluginChannel, data);
    }
}
