package net.coreprotect.command.lookup;

import java.sql.Connection;
import java.sql.Statement;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.BlockLookup;
import net.coreprotect.database.lookup.InteractionLookup;
import net.coreprotect.database.lookup.SignMessageLookup;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class BlockLookupThread implements Runnable {
    private final CommandSender player;
    private final Command command;
    private final Block block;
    private final BlockState blockState;
    private final int page;
    private final int limit;
    private final int type;

    public BlockLookupThread(CommandSender player, Command command, Block block, BlockState blockState, int page, int limit, int type) {
        this.player = player;
        this.command = command;
        this.block = block;
        this.blockState = blockState;
        this.page = page;
        this.limit = limit;
        this.type = type;
    }

    @Override
    public void run() {
        try (Connection connection = Database.getConnection(true)) {
            ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });
            if (connection != null) {
                Statement statement = connection.createStatement();
                if (type == 8) {
                    java.util.List<String> signData = SignMessageLookup.performLookup(command.getName(), statement, blockState.getLocation(), player, page, limit);
                    for (String signMessage : signData) {
                        String bypass = null;

                        if (signMessage.contains("\n")) {
                            String[] split = signMessage.split("\n");
                            signMessage = split[0];
                            bypass = split[1];
                        }

                        if (signMessage.length() > 0) {
                            Chat.sendComponent(player, signMessage, bypass);
                        }
                    }
                }
                else {
                    String blockdata = null;
                    if (type == 7) {
                        blockdata = InteractionLookup.performLookup(command.getName(), statement, block, player, 0, page, limit);
                    }
                    else {
                        blockdata = BlockLookup.performLookup(command.getName(), statement, blockState, player, 0, page, limit);
                    }
                    if (blockdata.contains("\n")) {
                        for (String b : blockdata.split("\n")) {
                            Chat.sendComponent(player, b);
                        }
                    }
                    else if (blockdata.length() > 0) {
                        Chat.sendComponent(player, blockdata);
                    }
                }
                statement.close();
            }
            else {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
    }
}
