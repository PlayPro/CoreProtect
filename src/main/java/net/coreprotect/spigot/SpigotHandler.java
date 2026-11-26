package net.coreprotect.spigot;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;

import org.bukkit.DyeColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.model.entity.VillagerReputationData;
import net.coreprotect.utility.AdapterMethods;
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

    private static final Class<?> REPUTATION_TYPE = AdapterMethods.findType("org.bukkit.entity.Villager$ReputationType");
    public static ChatColor DARK_AQUA = ChatColor.of("#31b0e8");

    public SpigotHandler() {
        Color.DARK_AQUA = SpigotHandler.DARK_AQUA.toString();
    }

    @Override
    public DyeColor getCushionColor(Entity entity) {
        try {
            Object handle = AdapterMethods.invokeNoArguments(entity, "getHandle");
            Object color = AdapterMethods.invokeNoArguments(handle, "getColor");
            Object id = AdapterMethods.invokeNoArguments(color, "getId");
            if (!(id instanceof Number)) {
                return null;
            }
            return DyeColor.getByWoolData(((Number) id).byteValue());
        }
        catch (ReflectiveOperationException | LinkageError e) {
            ErrorReporter.report(e);
            return null;
        }
    }

    @Override
    public void setCushionColor(Entity entity, DyeColor color) {
        if (color == null) {
            return;
        }
        try {
            Object handle = AdapterMethods.invokeNoArguments(entity, "getHandle");
            if (handle == null) {
                return;
            }
            Method getter = AdapterMethods.find(handle.getClass(), "getColor");
            if (getter == null) {
                return;
            }
            Class<?> colorClass = getter.getReturnType();
            Method resolveColor = AdapterMethods.find(colorClass, "byId", int.class);
            Method setColor = AdapterMethods.find(handle.getClass(), "setColor", colorClass);
            if (resolveColor != null && setColor != null) {
                Object value = resolveColor.invoke(null, (int) color.getWoolData());
                if (value != null) {
                    setColor.invoke(handle, value);
                }
            }
        }
        catch (ReflectiveOperationException | LinkageError e) {
            ErrorReporter.report(e);
        }
    }

    @Override
    public void addHoverComponent(Object message, String[] data) {
        if (data == null || data.length < 3) {
            return;
        }
        try {
            TextComponent component = new TextComponent(TextComponent.fromLegacyText(processComponent(data[2])));
            if (Config.getGlobal().HOVER_EVENTS) {
                String tooltipText = processComponent(data[1]).replace(Color.MAGIC, "");
                BaseComponent[] displayComponent = TextComponent.fromLegacyText(tooltipText);
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(displayComponent)));
            }
            ((TextComponent) message).addExtra(component);
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
                if (data.length < 3) {
                    builder.append(Chat.COMPONENT_TAG_OPEN).append(value).append(Chat.COMPONENT_TAG_CLOSE);
                }
                else if (data[0].equals(Chat.COMPONENT_COMMAND)) {
                    String command = processComponent(data[1]);
                    TextComponent component = new TextComponent(TextComponent.fromLegacyText(processComponent(data[2])));
                    component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
                    SpigotAdapter.ADAPTER.setHoverEvent(component, StringUtils.hoverCommandFilter(command));
                    message.addExtra(component);
                }
                else if (data[0].equals(Chat.COMPONENT_POPUP)) {
                    SpigotAdapter.ADAPTER.addHoverComponent(message, data);
                }
                else {
                    builder.append(Chat.COMPONENT_TAG_OPEN).append(value).append(Chat.COMPONENT_TAG_CLOSE);
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
        if (REPUTATION_TYPE == null) {
            return false;
        }
        try {
            Method setReputation = AdapterMethods.find(villager.getClass(), "setReputation", UUID.class, REPUTATION_TYPE, int.class);
            if (setReputation == null) {
                return false;
            }

            for (Object reputationObject : reputations) {
                VillagerReputationData data = VillagerReputationData.parse(reputationObject);
                if (data == null) {
                    continue;
                }

                for (VillagerReputationData.Value value : data.values()) {
                    Object type = BukkitAdapter.getLegacyEnumValue(REPUTATION_TYPE, value.type());
                    if (type != null) {
                        setReputation.invoke(villager, data.uuid(), type, value.amount());
                    }
                }
            }

            return true;
        }
        catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    @Override
    public Object getVillagerGossipDecayTime(Villager villager) {
        try {
            return AdapterMethods.invokeNoArguments(villager, "getGossipDecayTime");
        }
        catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    @Override
    public void setVillagerGossipDecayTime(Villager villager, Object value) {
        if (value instanceof Number) {
            try {
                Method method = AdapterMethods.find(villager.getClass(), "setGossipDecayTime", long.class);
                if (method != null) {
                    method.invoke(villager, ((Number) value).longValue());
                }
            }
            catch (ReflectiveOperationException | LinkageError e) {
            }
        }
    }

    private static void addBuilder(TextComponent message, StringBuilder builder) {
        String text = Chat.unescapeComponentTags(builder.toString());
        String[] splitBuilder = text.split(SpigotHandler.DARK_AQUA.toString());
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
