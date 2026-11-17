package net.coreprotect.listener.channel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ErrorReporter;

public class PluginChannelHandshakeListener implements PluginMessageListener, Listener {

    public static final String pluginChannel = "coreprotect:handshake";
    private static final int MAX_HANDSHAKE_BYTES = 1024;
    private static final int MAX_METADATA_LENGTH = 64;
    private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\\u001B\\[[0-9;?]*[ -/]*[@-~]");
    private final int networkingProtocolVersion = 1;
    private final Set<UUID> pluginChannelPlayers;
    private static volatile PluginChannelHandshakeListener instance;

    public PluginChannelHandshakeListener() {
        instance = this;
        pluginChannelPlayers = ConcurrentHashMap.newKeySet();
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
        UUID uuid = ((Player) commandSender).getUniqueId();
        return uuid != null && getPluginChannelPlayers().contains(uuid);
    }

    public boolean verifyNetworkingPermission(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            return false;
        }
        Player player = (Player) commandSender;
        UUID uuid = player.getUniqueId();
        if (uuid == null || !pluginChannelPlayers.contains(uuid)) {
            return false;
        }
        if (!player.hasPermission("coreprotect.networking")) {
            pluginChannelPlayers.remove(uuid);
            return false;
        }
        return true;
    }

    public void clearPlayers() {
        pluginChannelPlayers.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        getPluginChannelPlayers().remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String s, Player player, byte[] bytes) {
        handleHandshake(s, player, bytes);
    }

    private void handleHandshake(String channel, Player player, byte[] bytes) {
        if (player == null || !player.hasPermission("coreprotect.networking")) {
            return;
        }

        if (!pluginChannel.equals(channel)) {
            return;
        }

        if (bytes == null || bytes.length == 0 || bytes.length > MAX_HANDSHAKE_BYTES) {
            return;
        }

        final String modVersion;
        final String modId;
        final int protocolVersion;
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            modVersion = sanitizeMetadata(dis.readUTF());
            modId = sanitizeMetadata(dis.readUTF());
            protocolVersion = dis.readInt();
        }
        catch (IOException exception) {
            if (Config.getGlobal().NETWORK_DEBUG) {
                Chat.console("Ignored malformed CoreProtect handshake from " + player.getName() + ": " + exception.getClass().getSimpleName());
            }
            return;
        }

        try {
            if (Config.getGlobal().NETWORK_DEBUG) {
                Chat.console(sanitizeMetadata(new String(bytes, StandardCharsets.UTF_8)));
                Chat.console(modVersion);
                Chat.console(modId);
                Chat.console(String.valueOf(protocolVersion));
            }

            if (protocolVersion != networkingProtocolVersion) {
                Chat.console(Phrase.build(Phrase.NETWORK_CONNECTION, player.getName(), modId, modVersion, Selector.SECOND));
                return;
            }

            getPluginChannelPlayers().add(player.getUniqueId());
            Chat.console(Phrase.build(Phrase.NETWORK_CONNECTION, player.getName(), modId, modVersion, Selector.FIRST));

            CoreProtect plugin = CoreProtect.getInstance();
            if (plugin != null) {
                player.sendPluginMessage(plugin, pluginChannel, sendRegistered());
            }
        }
        catch (Exception exception) {
            ErrorReporter.report(exception);
        }
    }

    private static String sanitizeMetadata(String value) {
        if (value == null) {
            return "unknown";
        }
        String stripped = ANSI_ESCAPE_PATTERN.matcher(value).replaceAll("");
        StringBuilder sanitized = new StringBuilder(Math.min(stripped.length(), MAX_METADATA_LENGTH));
        boolean lastWasSpace = false;
        for (int i = 0; i < stripped.length() && sanitized.length() < MAX_METADATA_LENGTH; i++) {
            char c = stripped.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                if (!lastWasSpace && sanitized.length() > 0) {
                    sanitized.append(' ');
                    lastWasSpace = true;
                }
                continue;
            }
            sanitized.append(c);
            lastWasSpace = false;
        }
        String result = sanitized.toString().trim();
        return result.isEmpty() ? "unknown" : result;
    }

    private byte[] sendRegistered() throws IOException {
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);
        msgOut.writeBoolean(true);

        return msgBytes.toByteArray();
    }
}
