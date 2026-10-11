package com.hexvane.aetherhaven.guild;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuildResidentHousingTest {
    @Test void invitationStartsFreshHousingWithoutChargingTheGiftAgain() {
        var town = new TownRecord(); var id = UUID.randomUUID();
        String quest = AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK;
        town.completeQuest(quest);
        town.getGuildHallAdventurerNpcIds().add(id.toString());
        GuildResidentInvitation.admit(town, id, "prowl");
        GuildResidentInvitation.startHousing(town, id);
        assertTrue(town.hasQuestActive(quest)); assertFalse(town.hasQuestCompleted(quest));
        assertEquals(id, town.getQuestTargetEntityUuid(quest));
        assertFalse(town.getGuildHallAdventurerNpcIds().contains(id.toString()));
        assertTrue(town.getTouristRecords().getFirst().isCitizen());
    }
}
