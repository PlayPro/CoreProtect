package net.coreprotect.spigot;

import java.util.regex.Matcher;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Util;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class SpigotHandler extends SpigotAdapter implements SpigotInterface {

    public static ChatColor DARK_AQUA = ChatColor.DARK_AQUA;

    @Override
    public void addHoverComponent(Object message, String[] data) {
        ((TextComponent) message).addExtra(data[2]);
    }

    @Override
    public void sendComponent(CommandSender sender, String string, String bypass) {
        TextComponent message = new TextComponent();
        StringBuilder builder = new StringBuilder();

        if (sender instanceof ConsoleCommandSender) {
            string = string.replace(SpigotHandler.DARK_AQUA.toString(), ChatColor.DARK_AQUA.toString());
        }

        Matcher matcher = Util.tagParser.matcher(string);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value != null) {
                if (builder.length() > 0) {
                    addBuilder(message, builder);
                }

                String[] data = value.split("\\|", 3);
                if (data[0].equals(Chat.COMPONENT_COMMAND)) {
                    TextComponent component = new TextComponent(TextComponent.fromLegacyText(data[2]));
                    component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, data[1]));
                    SpigotAdapter.ADAPTER.setHoverEvent(component, Util.hoverCommandFilter(data[1]));
                    message.addExtra(component);
                }
                else if (data[0].equals(Chat.COMPONENT_POPUP)) {
                    SpigotAdapter.ADAPTER.addHoverComponent(message, data);
                }
            }
            else {
                builder.append(matcher.group(2));
            }
        }

        if (builder.length() > 0) {
            addBuilder(message, builder);
        }

        if (bypass != null) {
            message.addExtra(bypass);
        }

        sender.spigot().sendMessage(message);
    }

    private static void addBuilder(TextComponent message, StringBuilder builder) {
        String[] splitBuilder = builder.toString().split(SpigotHandler.DARK_AQUA.toString());
        for (int i = 0; i < splitBuilder.length; i++) {
            if (i > 0) {
                TextComponent textComponent = new TextComponent(splitBuilder[i]);
                textComponent.setColor(SpigotHandler.DARK_AQUA);
                message.addExtra(textComponent);
            }
            else {
                message.addExtra(splitBuilder[i]);
            }
        }

        builder.setLength(0);
    }

}
