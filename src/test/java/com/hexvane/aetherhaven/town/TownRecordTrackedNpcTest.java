package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hexvane.aetherhaven.tourist.TouristRecord;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TownRecordTrackedNpcTest {

    @Test
    void collectsGuildAdventurersGuardsAndTouristsForDissolution() {
        TownRecord town =
            new TownRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test",
                0,
                0,
                0,
                1,
                1,
                System.currentTimeMillis()
            );
        UUID guildMaster = UUID.randomUUID();
        UUID adventurer = UUID.randomUUID();
        UUID slotMapOnlyAdventurer = UUID.randomUUID();
        UUID guard = UUID.randomUUID();
        UUID tourist = UUID.randomUUID();

        town.setGuildMasterEntityUuid(guildMaster);
        town.getGuildHallAdventurerNpcIds().add(adventurer.toString());
        town.getGuildHallAdventurerSlotByNpcId().put(slotMapOnlyAdventurer.toString(), 2);
        town.getHiredGuardRecords().add(new HiredGuardRecord("guard", guard, "default", false));
        town.getTouristRecords().add(
            new TouristRecord("tourist", tourist, UUID.randomUUID(), false, false, 1L, 20)
        );

        Set<UUID> tracked = new LinkedHashSet<>();
        town.collectTrackedNpcEntityUuids(tracked);

        assertEquals(Set.of(guildMaster, adventurer, slotMapOnlyAdventurer, guard, tourist), tracked);
    }
}
