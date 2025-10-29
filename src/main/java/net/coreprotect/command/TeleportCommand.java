package net.coreprotect.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatMessage;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Teleport;
import net.coreprotect.utility.WorldUtils;

public class TeleportCommand {

    protected static void runCommand(CommandSender player, boolean permission, String[] args) {
        if (!permission) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            return;
        }

        if (!(player instanceof Player)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.TELEPORT_PLAYERS));
            return;
        }

        if (ConfigHandler.teleportThrottle.get(player.getName()) != null) {
            Object[] lookupThrottle = ConfigHandler.teleportThrottle.get(player.getName());
            if ((boolean) lookupThrottle[0] || ((System.currentTimeMillis() - (long) lookupThrottle[1])) < 500) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.COMMAND_THROTTLED));
                return;
            }
        }

        final Location location = parseTeleportLocation((Player) player, args);
        if (location == null) {
            return;
        }

        // folia: safe teleportation logic
        if (ConfigHandler.isFolia) {
            location.getWorld().getChunkAtAsync(location).thenAccept(chunk -> Scheduler.runTask(CoreProtect.getInstance(), () -> Teleport.performSafeTeleport(((Player) player), location, true), location));
        }
        else {
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                if (!location.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                    location.getWorld().getChunkAt(location);
                }
                Teleport.performSafeTeleport(((Player) player), location, true);
            }, location);
        }

        ConfigHandler.teleportThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
    }

    private static Location parseTeleportLocation(Player player, String[] args) {
        if (args.length < 3) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co teleport <world> <x> <y> <z>"));
            return null;
        }

        String worldName = args[1];
        int wid = WorldUtils.matchWorld(worldName);
        if (wid == -1 && args.length >= 5) {
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, worldName)).build());
            return null;
        }

        Location location = player.getLocation().clone();
        World world = (wid > -1) ? Bukkit.getServer().getWorld(WorldUtils.getWorldName(wid)) : location.getWorld();
        if (world == null) {
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, worldName)).build());
            return null;
        }

        String x = null;
        String y = null;
        String z = null;

        for (int i = 1; i < args.length; i++) {
            if (i == 1 && wid > -1) {
                continue;
            }

            if (x == null) {
                x = args[i];
            }
            else if (z == null) {
                z = args[i];
            }
            else if (y == null) {
                y = z;
                z = args[i];
            }
        }

        if (y == null) {
            y = Double.toString(Math.max(63, location.getY()));
        }
        if (x == null || y == null || z == null) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co teleport <world> <x> <y> <z>"));
            return null;
        }

        x = x.replaceAll("[^0-9.\\-]", "");
        y = y.replaceAll("[^0-9.\\-]", "");
        z = z.replaceAll("[^0-9.\\-]", "");

        String xValidate = x.replaceAll("[^.\\-]", "");
        String yValidate = y.replaceAll("[^.\\-]", "");
        String zValidate = z.replaceAll("[^.\\-]", "");

        if ((x.isEmpty() || x.length() >= 12 || x.equals(xValidate)) || (y.isEmpty() || y.length() >= 12 || y.equals(yValidate)) || (z.isEmpty() || z.length() >= 12 || z.equals(zValidate))) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co teleport <world> <x> <y> <z>"));
            return null;
        }

        try {
            location.setWorld(world);
            location.setX(Double.parseDouble(x));
            location.setY(Double.parseDouble(y));
            location.setZ(Double.parseDouble(z));
        }
        catch (NumberFormatException e) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co teleport <world> <x> <y> <z>"));
            return null;
        }

        return location;
    }
}
