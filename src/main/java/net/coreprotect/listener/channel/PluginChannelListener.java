package net.coreprotect.listener.channel;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.WorldUtils;

public class PluginChannelListener {

    public static final String pluginChannel = "coreprotect:data";
    public static final int MAX_PACKET_BYTES = 32_766;
    private static final int MAX_PHRASE_UTF_BYTES = 4096;
    private static final int MAX_METADATA_UTF_BYTES = 1024;
    private static volatile PluginChannelListener instance;

    public PluginChannelListener() {
        instance = this;
    }

    public static PluginChannelListener getInstance() {
        return instance;
    }

    private static boolean isRegisteredChannelPlayer(CommandSender commandSender) {
        PluginChannelHandshakeListener handshake = PluginChannelHandshakeListener.getInstance();
        return handshake != null && handshake.isPluginChannelPlayer(commandSender);
    }

    private static String truncateModifiedUtf8(String value, int maxUtfBytes) {
        if (value == null || maxUtfBytes <= 0) {
            return "";
        }
        int limit = Math.min(maxUtfBytes, 65_535);
        int utfBytes = 0;
        int endIndex = 0;
        int length = value.length();
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(value.charAt(i + 1))) {
                if (utfBytes + 6 > limit) {
                    break;
                }
                utfBytes += 6;
                endIndex = i + 2;
                i++;
            }
            else {
                int charBytes = (c >= 0x0001 && c <= 0x007F) ? 1 : (c == 0 || c <= 0x07FF) ? 2 : 3;
                if (utfBytes + charBytes > limit) {
                    break;
                }
                utfBytes += charBytes;
                endIndex = i + 1;
            }
        }
        return endIndex == length ? value : value.substring(0, endIndex);
    }

    private static int writeBoundedUtf(DataOutputStream out, String value, int maxUtfBytes) throws IOException {
        String bounded = truncateModifiedUtf8(value, maxUtfBytes);
        int before = out.size();
        out.writeUTF(bounded);
        return out.size() - before;
    }

    public void sendData(CommandSender commandSender, long epochSeconds, Phrase phrase, String selector, String resultUser, String target, int amount, int x, int y, int z, int worldId, String rbFormat, boolean isContainer, boolean added) throws IOException {
        if (!isRegisteredChannelPlayer(commandSender)) {
            return;
        }

        String phraseSelector = Phrase.getPhraseSelector(phrase, selector);
        String worldName = WorldUtils.getWorldName(worldId);
        boolean hasRbFormat = rbFormat != null && !rbFormat.isEmpty();

        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);
        int remaining = MAX_PACKET_BYTES - 31; // int(4) + long(8) + 4*int(16) + 3*boolean(3)
        msgOut.writeInt(1);
        msgOut.writeLong(epochSeconds * 1000L);
        remaining -= writeBoundedUtf(msgOut, phraseSelector, Math.min(MAX_PHRASE_UTF_BYTES, remaining - 6));
        remaining -= writeBoundedUtf(msgOut, resultUser, Math.min(MAX_METADATA_UTF_BYTES, remaining - 4));
        int worldReserve = 2 + MAX_METADATA_UTF_BYTES;
        remaining -= writeBoundedUtf(msgOut, target, Math.max(0, remaining - 2 - worldReserve));
        msgOut.writeInt(amount);
        msgOut.writeInt(x);
        msgOut.writeInt(y);
        msgOut.writeInt(z);
        writeBoundedUtf(msgOut, worldName, Math.max(0, remaining - 2));
        msgOut.writeBoolean(hasRbFormat);
        msgOut.writeBoolean(isContainer);
        msgOut.writeBoolean(added);
        if (Config.getGlobal().NETWORK_DEBUG) {
            Chat.console(String.valueOf(epochSeconds * 1000L));
            Chat.console(phraseSelector);
            Chat.console(String.valueOf(resultUser));
            Chat.console(String.valueOf(target));
            Chat.console(String.valueOf(amount));
            Chat.console(String.valueOf(x));
            Chat.console(String.valueOf(y));
            Chat.console(String.valueOf(z));
            Chat.console(String.valueOf(worldName));
            Chat.console(String.valueOf(hasRbFormat));
            Chat.console(String.valueOf(isContainer));
            Chat.console(String.valueOf(added));
            Chat.console("");
        }

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendInfoData(CommandSender commandSender, long epochSeconds, Phrase phrase, String selector, String resultUser, int amount, int x, int y, int z, int worldId) throws IOException {
        if (!isRegisteredChannelPlayer(commandSender)) {
            return;
        }

        String phraseSelector = Phrase.getPhraseSelector(phrase, selector);
        String worldName = WorldUtils.getWorldName(worldId);

        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);
        int remaining = MAX_PACKET_BYTES - 28; // int(4) + long(8) + 4*int(16)
        msgOut.writeInt(2);
        msgOut.writeLong(epochSeconds * 1000L);
        remaining -= writeBoundedUtf(msgOut, phraseSelector, Math.min(MAX_PHRASE_UTF_BYTES, remaining - 4));
        remaining -= writeBoundedUtf(msgOut, resultUser, Math.min(MAX_METADATA_UTF_BYTES, remaining - 2));
        msgOut.writeInt(amount);
        msgOut.writeInt(x);
        msgOut.writeInt(y);
        msgOut.writeInt(z);
        writeBoundedUtf(msgOut, worldName, Math.max(0, remaining - 2));
        if (Config.getGlobal().NETWORK_DEBUG) {
            Chat.console(String.valueOf(epochSeconds * 1000L));
            Chat.console(phraseSelector);
            Chat.console(String.valueOf(resultUser));
            Chat.console(String.valueOf(amount));
            Chat.console(String.valueOf(x));
            Chat.console(String.valueOf(y));
            Chat.console(String.valueOf(z));
            Chat.console(String.valueOf(worldName));
            Chat.console("");
        }

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendMessageData(CommandSender commandSender, long epochSeconds, String resultUser, String message, boolean sign, int x, int y, int z, int worldId) throws IOException {
        if (!isRegisteredChannelPlayer(commandSender)) {
            return;
        }

        String worldName = WorldUtils.getWorldName(worldId);

        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);
        int remaining = MAX_PACKET_BYTES - 25; // int(4) + long(8) + boolean(1) + 3*int(12)
        msgOut.writeInt(3);
        msgOut.writeLong(epochSeconds * 1000L);
        remaining -= writeBoundedUtf(msgOut, resultUser, Math.min(MAX_METADATA_UTF_BYTES, remaining - 4));
        int worldReserve = 2 + MAX_METADATA_UTF_BYTES;
        remaining -= writeBoundedUtf(msgOut, message, Math.max(0, remaining - 2 - worldReserve));
        msgOut.writeBoolean(sign);
        msgOut.writeInt(x);
        msgOut.writeInt(y);
        msgOut.writeInt(z);
        writeBoundedUtf(msgOut, worldName, Math.max(0, remaining - 2));
        if (Config.getGlobal().NETWORK_DEBUG) {
            Chat.console(String.valueOf(epochSeconds * 1000L));
            Chat.console(String.valueOf(resultUser));
            Chat.console(String.valueOf(message));
            Chat.console(String.valueOf(sign));
            Chat.console(String.valueOf(x));
            Chat.console(String.valueOf(y));
            Chat.console(String.valueOf(z));
            Chat.console(String.valueOf(worldName));
            Chat.console("");
        }

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendUsernameData(CommandSender commandSender, long epochSeconds, String resultUser, String target) throws IOException {
        if (!isRegisteredChannelPlayer(commandSender)) {
            return;
        }

        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);
        int remaining = MAX_PACKET_BYTES - 12; // int(4) + long(8)
        msgOut.writeInt(4);
        msgOut.writeLong(epochSeconds * 1000L);
        remaining -= writeBoundedUtf(msgOut, resultUser, Math.min(MAX_METADATA_UTF_BYTES, remaining - 2));
        writeBoundedUtf(msgOut, target, Math.max(0, remaining - 2));

        if (Config.getGlobal().NETWORK_DEBUG) {
            Chat.console(String.valueOf(epochSeconds * 1000L));
            Chat.console(String.valueOf(resultUser));
            Chat.console(String.valueOf(target));
            Chat.console("");
        }

        send(commandSender, msgBytes.toByteArray());
    }

    public void sendTest(CommandSender commandSender, String type) throws IOException {
        if (!isRegisteredChannelPlayer(commandSender)) {
            return;
        }

        int worldId = 1;
        Random rand = new Random();
        long epochSeconds = (System.currentTimeMillis() / 1000L) - rand.nextInt(20);
        String selector = Selector.FIRST;
        String resultUser = "Anne";
        int amount = 5;
        int x = rand.nextInt(10);
        int y = rand.nextInt(10);
        int z = rand.nextInt(10);
        String rbFormat = "test";
        String message = "This is a test";
        boolean sign = true;

        switch (type == null ? "" : type) {
            case "2":
                sendInfoData(commandSender, epochSeconds, Phrase.LOOKUP_LOGIN, selector, resultUser, amount, x, y, z, worldId);
                break;
            case "3":
                sendMessageData(commandSender, epochSeconds, resultUser, message, sign, x, y, z, worldId);
                break;
            case "4":
                sendUsernameData(commandSender, epochSeconds, resultUser, "Arne");
                break;
            default:
                sendData(commandSender, epochSeconds, Phrase.LOOKUP_CONTAINER, selector, resultUser, "clay_ball", amount, x, y, z, worldId, rbFormat, false, true);
                break;
        }

        commandSender.sendMessage(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NETWORK_TEST));
    }

    private void send(CommandSender commandSender, byte[] msgBytes) {
        if (!(commandSender instanceof Player)) {
            return;
        }

        sendCoreProtectData((Player) commandSender, msgBytes);
    }

    private void sendCoreProtectData(Player player, byte[] data) {
        CoreProtect plugin = CoreProtect.getInstance();
        if (plugin == null) {
            return;
        }
        player.sendPluginMessage(plugin, pluginChannel, data);
    }
}
