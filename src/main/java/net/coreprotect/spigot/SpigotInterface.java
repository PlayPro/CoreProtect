package net.coreprotect.spigot;

import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.event.block.SignChangeEvent;

public interface SpigotInterface {

    public void addHoverComponent(Object message, String[] data);

    public void setHoverEvent(Object message, String text);

    public void sendComponent(CommandSender sender, String string, String bypass);

    public String getLine(Sign sign, int line);

    public void setLine(Sign sign, int line, String string);

    public boolean isSignFront(SignChangeEvent event);

}
