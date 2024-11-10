package net.coreprotect.spigot;

import java.util.regex.Matcher;

import org.bukkit.command.CommandSender;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Util;

public class SpigotAdapter implements SpigotInterface {

    public static SpigotInterface ADAPTER;
    public static final int SPIGOT_UNAVAILABLE = 0;
    public static final int SPIGOT_V1_13 = BukkitAdapter.BUKKIT_V1_13;
    public static final int SPIGOT_V1_14 = BukkitAdapter.BUKKIT_V1_14;
    public static final int SPIGOT_V1_15 = BukkitAdapter.BUKKIT_V1_15;
    public static final int SPIGOT_V1_16 = BukkitAdapter.BUKKIT_V1_16;
    public static final int SPIGOT_V1_17 = BukkitAdapter.BUKKIT_V1_17;
    public static final int SPIGOT_V1_18 = BukkitAdapter.BUKKIT_V1_18;
    public static final int SPIGOT_V1_19 = BukkitAdapter.BUKKIT_V1_19;
    public static final int SPIGOT_V1_20 = BukkitAdapter.BUKKIT_V1_20;
    public static final int SPIGOT_V1_21 = BukkitAdapter.BUKKIT_V1_21;

    public static void loadAdapter() {
        int spigotVersion = ConfigHandler.SERVER_VERSION;
        if (!ConfigHandler.isSpigot) {
            spigotVersion = SPIGOT_UNAVAILABLE;
        }

        switch (spigotVersion) {
            case SPIGOT_UNAVAILABLE:
                SpigotAdapter.ADAPTER = new SpigotAdapter();
                break;
            case SPIGOT_V1_13:
            case SPIGOT_V1_14:
            case SPIGOT_V1_15:
            case SPIGOT_V1_16:
            case SPIGOT_V1_17:
            case SPIGOT_V1_18:
            case SPIGOT_V1_19:
            case SPIGOT_V1_20:
            case SPIGOT_V1_21:
            default:
                SpigotAdapter.ADAPTER = new SpigotHandler();
                break;
        }
    }

    @Override
    public void addHoverComponent(Object message, String[] data) {
        return;
    }

    @Override
    public void setHoverEvent(Object message, String text) {
        return;
    }

    @Override
    public void sendComponent(CommandSender sender, String string, String bypass) {
        StringBuilder message = new StringBuilder();

        Matcher matcher = Util.tagParser.matcher(string);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value != null) {
                String[] data = value.split("\\|", 3);
                if (data[0].equals(Chat.COMPONENT_COMMAND) || data[0].equals(Chat.COMPONENT_POPUP)) {
                    message.append(data[2]);
                }
            }
            else {
                message.append(matcher.group(2));
            }
        }

        if (bypass != null) {
            message.append(bypass);
        }

        Chat.sendMessage(sender, message.toString());
    }

    public String processComponent(String component) {
        return component.replace(Chat.COMPONENT_PIPE, "|");
    }
}
