package net.coreprotect.listener.player.inspector;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import net.coreprotect.database.lookup.InteractionLookup;
import net.coreprotect.utility.Chat;

public class InteractionInspector extends BaseInspector {

    public void performInteractionLookup(Player player, Block block) {
        startInspection(player, () -> withStatement(player, (connection, statement) -> {
            String results = InteractionLookup.performLookup(null, statement, block, player, 0, 1, 7);
            Chat.sendLookupComponents(player, results);
        }));
    }
}
