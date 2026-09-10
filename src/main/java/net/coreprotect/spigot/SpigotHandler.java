package net.coreprotect.spigot;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Villager;

import net.coreprotect.config.Config;
import net.coreprotect.model.entity.VillagerReputationData;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.Util;
import net.coreprotect.utility.ErrorReporter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class SpigotHandler extends SpigotAdapter implements SpigotInterface {

    public static ChatColor DARK_AQUA = ChatColor.of("#31b0e8");

    public SpigotHandler() {
        Color.DARK_AQUA = SpigotHandler.DARK_AQUA.toString();
    }

    @Override
    public void addHoverComponent(Object message, String[] data) {
        try {
            if (Config.getGlobal().HOVER_EVENTS) {
                String tooltipText = data[1]; // text displayed inside tooltip
                TextComponent component = new TextComponent(TextComponent.fromLegacyText(data[2]));
                // BaseComponent[] displayComponent = TextComponent.fromLegacyText(processComponent(tooltipText));

                if (tooltipText.contains(Color.MAGIC)) {
                    tooltipText = tooltipText.replace(Color.MAGIC, "");

                    // to-do
                    /*
                    ComponentBuilder formattedComponent = new ComponentBuilder();
                    StringBuilder messageTest = new StringBuilder();
                    String colorChar = String.valueOf(ChatColor.COLOR_CHAR);
                    boolean isObfuscated = false;

                    String[] tooltip = tooltipText.split(colorChar);
                    for (String splitText : tooltip) {
                        boolean setObfuscated = splitText.startsWith("k");
                        splitText = setObfuscated ? splitText.substring(1) : (splitText.length() > 0 ? colorChar : "") + splitText;
                        if ((setObfuscated && !isObfuscated) || (!setObfuscated && isObfuscated)) {
                            formattedComponent.append(TextComponent.fromLegacyText(processComponent(messageTest.toString())));
                            formattedComponent.obfuscated(false); // setObfuscated
                            formattedComponent.append(TextComponent.fromLegacyText(processComponent(splitText)));
                            messageTest.setLength(0);
                            isObfuscated = !isObfuscated;
                        }
                        else {
                            messageTest.append(splitText);
                        }
                    }

                    if (messageTest.length() > 0) {
                        formattedComponent.append(TextComponent.fromLegacyText(processComponent(messageTest.toString())));
                    }

                    displayComponent = formattedComponent.create();
                    */
                }

                BaseComponent[] displayComponent = TextComponent.fromLegacyText(processComponent(tooltipText));
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(displayComponent)));
                ((TextComponent) message).addExtra(component);
            }
            else {
                super.addHoverComponent(message, data);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    @Override
    public void setHoverEvent(Object component, String text) {
        if (Config.getGlobal().HOVER_EVENTS) {
            ((TextComponent) component).setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(text))));
        }
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
                    SpigotAdapter.ADAPTER.setHoverEvent(component, StringUtils.hoverCommandFilter(data[1]));
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

    @Override
    public boolean setVillagerReputations(Villager villager, List<?> reputations) {
        try {
            Class<?> reputationTypeClass = Class.forName("org.bukkit.entity.Villager$ReputationType");
            Method setReputation = villager.getClass().getMethod("setReputation", UUID.class, reputationTypeClass, int.class);

            for (Object reputationObject : reputations) {
                VillagerReputationData data = VillagerReputationData.parse(reputationObject);
                if (data == null) {
                    continue;
                }

                for (VillagerReputationData.Value value : data.values()) {
                    Object type = parseEnumValue(reputationTypeClass, value.type());
                    if (type != null) {
                        setReputation.invoke(villager, data.uuid(), type, value.amount());
                    }
                }
            }

            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    @Override
    public Object getVillagerGossipDecayTime(Villager villager) {
        return invokeNoArgumentMethod(villager, "getGossipDecayTime");
    }

    @Override
    public void setVillagerGossipDecayTime(Villager villager, Object value) {
        if (value instanceof Number) {
            try {
                villager.getClass().getMethod("setGossipDecayTime", long.class).invoke(villager, ((Number) value).longValue());
            }
            catch (Exception e) {
            }
        }
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

    private static Object invokeNoArgumentMethod(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        }
        catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object parseEnumValue(Class<?> enumClass, String value) {
        try {
            return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), value);
        }
        catch (Exception e) {
            return null;
        }
    }

}
