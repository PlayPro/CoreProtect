package net.coreprotect.command.parser;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ItemSearchParserTest {
    @Test void quotedNamesMayContainSpacesAndParameterLikeText() {
        var values = ItemSearchParser.parse(new String[] {"find", "name:\"Old", "u:Bob", "Pickaxe\"", "source:online"});
        assertEquals("Old u:Bob Pickaxe", values.get("name"));
        assertEquals("online", values.get("source"));
    }
    @Test void rejectsAmbiguousOrUnknownArguments() {
        assertThrows(IllegalArgumentException.class, () -> ItemSearchParser.parse(new String[] {"find", "name:a", "name:b"}));
        assertThrows(IllegalArgumentException.class, () -> ItemSearchParser.parse(new String[] {"find", "name:\"a"}));
        assertThrows(IllegalArgumentException.class, () -> ItemSearchParser.parse(new String[] {"find", "radius:5"}));
        assertThrows(IllegalArgumentException.class, () -> ItemSearchParser.parse(new String[] {"find", "source:offline"}));
        assertThrows(IllegalArgumentException.class, () -> ItemSearchParser.parse(new String[] {"find", "page:0"}));
    }
}
