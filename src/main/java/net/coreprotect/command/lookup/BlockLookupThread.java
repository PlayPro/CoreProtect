package net.coreprotect.command.lookup;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import net.coreprotect.database.lookup.BlockLookup;
import net.coreprotect.database.lookup.InteractionLookup;
import net.coreprotect.database.lookup.SignMessageLookup;
import net.coreprotect.utility.Chat;

public class BlockLookupThread implements Runnable {
    private final CommandSender player;
    private final Command command;
    private final Block block;
    private final BlockState blockState;
    private final int page;
    private final int limit;
    private final int type;
    private final Integer entitySpawnRowId;

    public BlockLookupThread(CommandSender player, Command command, Block block, BlockState blockState, int page, int limit, int type, Integer entitySpawnRowId) {
        this.player = player;
        this.command = command;
        this.block = block;
        this.blockState = blockState;
        this.page = page;
        this.limit = limit;
        this.type = type;
        this.entitySpawnRowId = entitySpawnRowId;
    }

    @Override
    public void run() {
        LookupWorker.run(player, statement -> {
            if (type == 8) {
                List<String> results = SignMessageLookup.performLookup(command.getName(), statement, blockState.getLocation(), player, page, limit);
                Chat.sendSignLookupComponents(player, results);
            }
            else {
                String result = type == 7
                        ? InteractionLookup.performLookup(command.getName(), statement, block, player, 0, page, limit)
                        : BlockLookup.performLookup(command.getName(), statement, blockState, player, 0, page, limit, entitySpawnRowId);
                Chat.sendLookupComponents(player, result);
            }
        });
    }
}
