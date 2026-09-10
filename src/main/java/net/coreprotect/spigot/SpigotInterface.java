package net.coreprotect.spigot;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Villager;

public interface SpigotInterface {

    public void addHoverComponent(Object message, String[] data);

    public void setHoverEvent(Object message, String text);

    public void sendComponent(CommandSender sender, String string, String bypass);

    public boolean setVillagerReputations(Villager villager, List<?> reputations);

    public Object getVillagerGossipDecayTime(Villager villager);

    public void setVillagerGossipDecayTime(Villager villager, Object value);

}
