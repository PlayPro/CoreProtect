package net.coreprotect.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
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

        if (resultc < 3 || resultc > 5) {
            sendUsage(player);
            return;
        }

        Server server = Bukkit.getServer();
        String firstArgument = args[1].trim();
        boolean coordinateFirst = resultc == 4 && isCoordinateCharactersOnly(firstArgument);
        World exactWorld = coordinateFirst ? server.getWorld(firstArgument) : null;
        boolean hasWorldArg = resultc == 5 || (resultc == 4 && (!coordinateFirst || exactWorld != null));
        int coordStart = hasWorldArg ? 2 : 1;
        boolean hasY = resultc - coordStart == 3;
        double x = parseCoordinate(args[coordStart]);
        double y = hasY ? parseCoordinate(args[coordStart + 1]) : 0.0;
        double z = parseCoordinate(args[resultc - 1]);
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            sendUsage(player);
            return;
        }

        Player executor = (Player) player;
        Location location = executor.getLocation().clone();
        World world = location.getWorld();
        if (exactWorld != null) {
            world = exactWorld;
        }
        else if (hasWorldArg) {
            int wid = WorldUtils.matchWorld(firstArgument);
            world = wid == -1 ? null : server.getWorld(WorldUtils.getWorldName(wid));
        }
        if (world == null) {
            Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, firstArgument)).build());
            return;
        }

        location.setWorld(world);
        location.setX(x);
        if (hasY) {
            location.setY(y);
        }
        location.setZ(z);

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            if (Bukkit.getServer() != server || !executor.isOnline() || server.getPlayer(executor.getUniqueId()) != executor) {
                return;
            }
            if (!location.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                location.getWorld().getChunkAt(location);
            }

            Teleport.performSafeTeleport(executor, location, true);
        }, location);

        ConfigHandler.teleportThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
    }

    private static void sendUsage(CommandSender player) {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co teleport <world> <x> <y> <z>"));
    }

    private static double parseCoordinate(String value) {
        String trimmed = value.trim();
        if (!ParserUtils.isSignedDecimal(trimmed)) {
            return Double.NaN;
        }
        double coordinate = Double.parseDouble(trimmed);
        return coordinate >= Integer.MIN_VALUE && coordinate < (double) Integer.MAX_VALUE + 1.0 ? coordinate : Double.NaN;
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
