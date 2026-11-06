package net.kaleidoscope.deco;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DecoRuleTest {
    @Test void wandAndSneakAreBothRequired() {
        var rule = new DecoRule(List.of(), true, 1, "kaleidoscope_deco:table_left", List.of(), false);
        assertFalse(rule.matches("minecraft:stick", true, tag -> false));
        assertFalse(rule.matches("kaleidoscope_deco:state_tool", false, tag -> false));
        assertTrue(rule.matches("kaleidoscope_deco:state_tool", true, tag -> false));
    }
    @Test void wildcardDoesNotMatchEmptyHandAndTagsAreExplicit() {
        var wildcard = new DecoRule(List.of("*"), false, -1, "", List.of(), true);
        assertFalse(wildcard.matches("minecraft:air", false, tag -> false));
        var beds = new DecoRule(List.of("#minecraft:beds"), false, -1, "", List.of(), false);
        assertTrue(beds.matches("minecraft:red_bed", false, "minecraft:beds"::equals));
        assertFalse(beds.matches("minecraft:stone", false, tag -> false));
    }
    @Test void malformedCatalogCannotBecomeAnExecutableRule() {
        assertThrows(IllegalArgumentException.class, () -> new DecoRule(List.of(), false, 2, "", List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new DecoRule(List.of(), false, -1, "../../invalid", List.of(), false));
    }
}
