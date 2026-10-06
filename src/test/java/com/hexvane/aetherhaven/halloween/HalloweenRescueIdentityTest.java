package com.hexvane.aetherhaven.halloween;

import com.hexvane.aetherhaven.town.TownRecord;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("entity")
class HalloweenRescueIdentityTest {
    private static final Clock OCTOBER=Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),ZoneOffset.UTC);
    @Test void oldOpenDialogueLosesAcceptanceWhenEncounterIsReplaced() {
        UUID owner=UUID.randomUUID(),old=UUID.randomUUID(),next=UUID.randomUUID();
        var town=new TownRecord();town.setOwner(owner,"Owner");var receipt=town.halloweenRescue();
        var claim=receipt.reserve("old-world",10,120,30);assertTrue(receipt.bind(claim.token(),old));
        assertTrue(HalloweenResident.canAcceptRescue(town,owner,old,"old-world",OCTOBER));
        assertTrue(receipt.retire(receipt.current()));var replacement=receipt.reserve("new-world",20,130,40);
        assertTrue(receipt.bind(replacement.token(),next));
        assertFalse(HalloweenResident.canAcceptRescue(town,owner,old,"old-world",OCTOBER));
        assertFalse(HalloweenResident.canAcceptRescue(town,owner,next,"old-world",OCTOBER));
        assertTrue(HalloweenResident.canAcceptRescue(town,owner,next,"new-world",OCTOBER));
        town.completeQuest(HalloweenResident.RESCUE_QUEST);
        assertFalse(HalloweenResident.canAcceptRescue(town,owner,next,"new-world",OCTOBER));
    }
}
