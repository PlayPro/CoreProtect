package net.coreprotect.spigot;

import net.coreprotect.config.Config;
import net.coreprotect.utility.Color;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class Spigot_v1_16 extends SpigotHandler implements SpigotInterface {

    public Spigot_v1_16() {
        SpigotHandler.DARK_AQUA = ChatColor.of("#31b0e8");
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
            e.printStackTrace();
        }
    }

    @Override
    public void setHoverEvent(Object component, String text) {
        if (Config.getGlobal().HOVER_EVENTS) {
            ((TextComponent) component).setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(text))));
        }
    }

}
