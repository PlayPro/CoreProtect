package net.coreprotect.spigot;

import net.coreprotect.config.Config;
import net.coreprotect.utility.Color;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class Spigot_v1_16 extends SpigotHandler implements SpigotInterface {

    public Spigot_v1_16() {
        SpigotHandler.DARK_AQUA = ChatColor.of("#31b0e8");
        Color.DARK_AQUA = SpigotHandler.DARK_AQUA.toString();
    }

    @Override
    public void setHoverComponent(Object message, String[] data) {
        try {
            if (Config.getGlobal().HOVER_EVENTS) {
                TextComponent component = new TextComponent(TextComponent.fromLegacyText(data[2]));
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(data[1]))));
                ((TextComponent) message).addExtra(component);
            }
            else {
                super.setHoverComponent(message, data);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
