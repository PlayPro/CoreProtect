package net.coreprotect.spigot;

import org.bukkit.command.CommandSender;

public interface SpigotInterface {

    public void addHoverComponent(Object message, String[] data);

    public void setHoverEvent(Object message, String text);

    public void sendComponent(CommandSender sender, String string, String bypass);

}
