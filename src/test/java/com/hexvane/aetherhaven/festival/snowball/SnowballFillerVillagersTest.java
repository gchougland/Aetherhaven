package com.hexvane.aetherhaven.festival.snowball;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class SnowballFillerVillagersTest {
    @Test
    void pickFromLivePoolCapsToNeedAndKeepsOnlyGivenVillagers() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID c = UUID.fromString("00000000-0000-0000-0000-000000000003");
        List<UUID> picked = SnowballFillerVillagers.pickFromLivePool(List.of(a, b, c), 2, new Random(1L));
        assertEquals(2, picked.size());
        assertTrue(List.of(a, b, c).containsAll(picked));
    }

    @Test
    void pickFromLivePoolIsEmptyWhenNoneAreNeeded() {
        assertEquals(List.of(), SnowballFillerVillagers.pickFromLivePool(List.of(UUID.randomUUID()), 0, new Random(1L)));
    }
}
