package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("autonomy")
class VillagerConversationPresentationTest {
    @Test void listenerWaitsAtLeastOneSecondAndRespondsOnlyOnce() {
        var session = new VillagerLifeState.Session(UUID.randomUUID(), UUID.randomUUID(), 100, "Food");
        session.respondLater(false, "Agree", "Love", true, 5000);
        assertNull(session.takeResponse(5999));
        assertNull(session.takeResponse(6199));
        assertFalse(session.finishedTalking(100000), "A queued final reaction must not be discarded");
        var response = session.takeResponse(6200);
        assertNotNull(response);
        assertFalse(response.toFirst());
        assertEquals("Love", response.bubble());
        assertTrue(response.hearts());
        assertNull(session.takeResponse(100000));
    }
    @Test void presentationIsOccasionalAndOnlyUsesSmallMatchingItems() {
        assertEquals("Food_Bread", VillagerConversationItems.forTopic("Item_Food_Bread", .2));
        assertEquals("Plant_Crop_Carrot_Item", VillagerConversationItems.forTopic("Item_Plant_Crop_Carrot", .2));
        assertEquals("Rock_Gem_Ruby", VillagerConversationItems.forTopic("Item_Rock_Gem_Ruby", .39));
        assertNull(VillagerConversationItems.forTopic("Item_Food_Bread", .4));
        assertNull(VillagerConversationItems.forTopic("Item_Furniture_Bed", .2));
        assertNull(VillagerConversationItems.forTopic("Love", .2));
        assertNull(VillagerConversationItems.forTopic(null, .2));
        assertNull(VillagerConversationItems.forTopic("Item_Food_Bread", Double.NaN));
    }
}
