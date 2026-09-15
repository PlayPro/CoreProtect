package net.coreprotect.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import net.coreprotect.config.ConfigHandler;

class UndoCommandTest {
    @Test
    void rejectedInverseKeepsOriginalOperationForRetry() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("undo-test");
        when(sender.hasPermission("coreprotect.restore")).thenReturn(true);
        String[] original = {"RB", "u:griefer", "t:1h"};
        List<Object> saved = new ArrayList<>(Arrays.asList(10L, 20L, original, null));
        ConfigHandler.lastRollback.put("undo-test", saved);
        try (MockedStatic<RollbackRestoreCommand> rollback = mockStatic(RollbackRestoreCommand.class)) {
            UndoCommand.runCommand(sender, null, true, new String[] {"undo"});
            rollback.verify(() -> RollbackRestoreCommand.runCommand(eq(sender), isNull(), eq(true),
                    argThat(args -> args[0].equals("restore")), (Location) isNull(), eq(10L), eq(20L)));
            assertSame(saved, ConfigHandler.lastRollback.get("undo-test"));
            assertEquals("RB", original[0]);
            UndoCommand.runCommand(sender, null, true, new String[] {"undo"});
            assertEquals("RB", original[0]);
        }
        finally {
            ConfigHandler.lastRollback.remove("undo-test");
        }
    }

    @Test
    void inverseRequiresItsOwnPermission() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("permission-test");
        List<Object> saved = new ArrayList<>(Arrays.asList(10L, 20L, new String[] {"restore", "t:1h"}, null));
        ConfigHandler.lastRollback.put("permission-test", saved);
        try (MockedStatic<RollbackRestoreCommand> rollback = mockStatic(RollbackRestoreCommand.class)) {
            UndoCommand.runCommand(sender, null, true, new String[] {"undo"});
            rollback.verify(() -> RollbackRestoreCommand.runCommand(eq(sender), isNull(), eq(false),
                    argThat(args -> args[0].equals("rollback")), (Location) isNull(), eq(10L), eq(20L)));
            assertSame(saved, ConfigHandler.lastRollback.get("permission-test"));
        }
        finally {
            ConfigHandler.lastRollback.remove("permission-test");
        }
    }
}
