package com.hexvane.aetherhaven.prop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("prop")
class PropLootExclusionsTest {

    @Test
    void parseJson_readsExcludedIds() {
        Set<String> ids =
            PropLootExclusions.parseJson(
                """
                {"excludedPropIds":["cabbage_trough","balloons","festival_flag"]}
                """
            );
        assertEquals(Set.of("cabbage_trough", "balloons", "festival_flag"), ids);
    }

    @Test
    void parseJson_skipsBlankAndNonStrings() {
        Set<String> ids =
            PropLootExclusions.parseJson(
                """
                {"excludedPropIds":["  pig_pile  ","",1,null]}
                """
            );
        assertEquals(Set.of("pig_pile"), ids);
    }

    @Test
    void parseJson_missingArray_returnsEmpty() {
        assertTrue(PropLootExclusions.parseJson("{}").isEmpty());
        assertTrue(PropLootExclusions.parseJson("[]").isEmpty());
        assertTrue(PropLootExclusions.parseJson("not json").isEmpty());
    }
}
