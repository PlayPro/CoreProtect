package net.coreprotect.command.lookup;

import java.sql.Statement;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.lookup.ChestTransactionLookup;
import net.coreprotect.utility.Chat;

public class ChestTransactionLookupThread extends LookupWorker {
    private final Command command;
    private final Location location;
    private final int page;
    private final int limit;
    private final Integer entitySpawnRowId;

    public ChestTransactionLookupThread(CommandSender player, Command command, Location location, int page, int limit) {
        super(player);
        this.command = command;
        this.location = location;
        this.page = page;
        this.limit = limit;
        this.entitySpawnRowId = ConfigHandler.lookupEntityContainer.get(player.getName());
    }

    @Override
    protected void query(Statement statement) {
        List<String> results = ChestTransactionLookup.performLookup(command.getName(), statement, location, player, page, limit, false, entitySpawnRowId);
        Chat.sendLookupComponents(player, results);
    }
}
