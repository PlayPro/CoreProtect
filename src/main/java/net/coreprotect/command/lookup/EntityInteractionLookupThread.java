package net.coreprotect.command.lookup;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.lookup.EntityInteractionLookup;
import net.coreprotect.utility.Chat;

public final class EntityInteractionLookupThread implements Runnable {
    private final CommandSender player;
    private final Command command;
    private final int page;
    private final int limit;
    private final Integer entitySpawnRowId;

    public EntityInteractionLookupThread(CommandSender player, Command command, int page, int limit) {
        this.player = player;
        this.command = command;
        this.page = page;
        this.limit = limit;
        this.entitySpawnRowId = ConfigHandler.lookupEntityInteraction.get(player.getName());
    }

    @Override
    public void run() {
        LookupWorker.run(player, statement -> {
            List<String> results = EntityInteractionLookup.performLookup(command.getName(), statement, player, page, limit, entitySpawnRowId);
            Chat.sendLookupComponents(player, results);
        });
    }
}
