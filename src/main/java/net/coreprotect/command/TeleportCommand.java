package net.coreprotect.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.CoreProtect;
import net.coreprotect.command.parser.ParserUtils;
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
        int resultc = args.length;

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

        if (resultc < 3) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co teleport <world> <x> <y> <z>"));
            return;
        }

        String worldName = args[1];
        int wid = WorldUtils.matchWorld(worldName);
        boolean hasWorldArg = (resultc >= 5)
                || (wid > -1 && resultc >= 4)
                || (wid == -1 && resultc == 4 && !isCoordinateCharactersOnly(worldName.trim()) && isValidCoordinate(args[2]) && isValidCoordinate(args[3]));
        if (wid == -1 && hasWorldArg) {
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, worldName)).build());
            return;
        }
        if (wid > -1 && resultc == 3 && !isValidCoordinate(worldName)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co teleport <world> <x> <y> <z>"));
            return;
        }

        Location location = ((Player) player).getLocation().clone();
        World world = location.getWorld();
        if (hasWorldArg) {
            world = Bukkit.getServer().getWorld(WorldUtils.getWorldName(wid));
        }
        if (world == null) {
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, worldName)).build());
            return;
        }

        int coordStart = hasWorldArg ? 2 : 1;
        int coordCount = args.length - coordStart;
        if (coordCount < 2) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co teleport <world> <x> <y> <z>"));
            return;
        }

        String x = args[coordStart].trim();
        String y = (coordCount >= 3) ? args[coordStart + 1].trim() : null;
        String z = (coordCount >= 3) ? args[coordStart + 2].trim() : args[coordStart + 1].trim();

        if (!isValidCoordinate(x) || (y != null && !isValidCoordinate(y)) || !isValidCoordinate(z)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co teleport <world> <x> <y> <z>"));
            return;
        }

        double targetY = (y != null) ? Double.parseDouble(y) : (location.getBlockY() > 63 ? 63.0 : location.getY());

        location.setWorld(world);
        location.setX(Double.parseDouble(x));
        location.setY(targetY);
        location.setZ(Double.parseDouble(z));

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            if (!location.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                location.getWorld().getChunkAt(location);
            }

            Teleport.performSafeTeleport(((Player) player), location, true);
        }, location);

        ConfigHandler.teleportThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
    }

    private static boolean isValidCoordinate(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() >= 12 || !ParserUtils.isSignedDecimal(trimmed)) {
            return false;
        }
        try {
            return Double.isFinite(Double.parseDouble(trimmed));
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isCoordinateCharactersOnly(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && c != '.' && c != '-') {
                return false;
            }
        }
        return true;
    }
}
