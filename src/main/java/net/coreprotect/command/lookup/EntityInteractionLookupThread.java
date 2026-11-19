package net.coreprotect.command.lookup;

import java.sql.Statement;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.lookup.EntityInteractionLookup;
import net.coreprotect.utility.Chat;

public final class EntityInteractionLookupThread extends LookupWorker {
    private final Command command;
    private final int page;
    private final int limit;
    private final Integer entitySpawnRowId;

    public EntityInteractionLookupThread(CommandSender player, Command command, int page, int limit) {
        super(player);
        this.command = command;
        this.page = page;
        this.limit = limit;
        this.entitySpawnRowId = ConfigHandler.lookupEntityInteraction.get(player.getName());
    }

    @Override
    protected void query(Statement statement) {
        List<String> results = EntityInteractionLookup.performLookup(command.getName(), statement, player, page, limit, entitySpawnRowId);
        Chat.sendLookupComponents(player, results);
    }
}
