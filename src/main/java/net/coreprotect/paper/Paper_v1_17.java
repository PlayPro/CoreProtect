package net.coreprotect.paper;

import org.bukkit.block.Sign;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Paper_v1_17 extends PaperHandler {

    @Override
    public String getLine(Sign sign, int line) {
        if (line >= 4) {
            return "";
        }

        // https://docs.adventure.kyori.net/serializer/
        return LegacyComponentSerializer.legacySection().serialize(sign.line(line));
    }

}
