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
    public static final int SPIGOT_v1_13 = BukkitAdapter.BUKKIT_v1_13;
    public static final int SPIGOT_v1_14 = BukkitAdapter.BUKKIT_v1_14;
    public static final int SPIGOT_v1_15 = BukkitAdapter.BUKKIT_v1_15;
    public static final int SPIGOT_v1_16 = BukkitAdapter.BUKKIT_v1_16;
    public static final int SPIGOT_v1_17 = BukkitAdapter.BUKKIT_v1_17;

    public static void loadAdapter() {
        int SPIGOT_VERSION = ConfigHandler.SERVER_VERSION;
        if (!ConfigHandler.isSpigot) {
            SPIGOT_VERSION = SPIGOT_UNAVAILABLE;
        }

        switch (SPIGOT_VERSION) {
            case SPIGOT_UNAVAILABLE:
                SpigotAdapter.ADAPTER = new SpigotAdapter();
                break;
            case SPIGOT_v1_13:
            case SPIGOT_v1_14:
            case SPIGOT_v1_15:
                SpigotAdapter.ADAPTER = new SpigotHandler();
                break;
            case SPIGOT_v1_16:
            case SPIGOT_v1_17:
            default:
                SpigotAdapter.ADAPTER = new Spigot_v1_16();
                break;
        }
    }

    @Override
    public void setHoverComponent(Object message, String[] data) {
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

}
