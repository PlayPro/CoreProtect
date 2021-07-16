package net.coreprotect.spigot;

import org.bukkit.command.CommandSender;

public interface SpigotInterface {

    public void setHoverComponent(Object message, String[] data);

    public void sendComponent(CommandSender sender, String string, String bypass);

}
