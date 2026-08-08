package com.hexvane.aetherhaven.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class VillagerBirthdayIndexTest {
    @Test
    void indexesConfiguredBirthdays() {
        VillagerDefinition farmer = new VillagerDefinition();
        setField(farmer, "npcRoleId", "Aetherhaven_Farmer");
        setField(farmer, "displayName", "Irienne Mossmark");
        setField(farmer, "birthdaySeason", "Summer");
        setField(farmer, "birthdayDay", 2);

        VillagerDefinitionCatalog catalog =
            VillagerDefinitionCatalog.forTests(Map.of("Aetherhaven_Farmer", farmer));
        VillagerBirthdayIndex index = VillagerBirthdayIndex.fromCatalog(catalog);

        assertEquals(1, index.birthdaysOn(Season.SUMMER, 2).size());
        assertEquals("Aetherhaven_Farmer", index.birthdaysOn(Season.SUMMER, 2).get(0).getNpcRoleId());
        assertTrue(index.birthdaysOn(Season.SPRING, 2).isEmpty());
    }

    private static void setField(VillagerDefinition def, String field, Object value) {
        try {
            var f = VillagerDefinition.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(def, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
