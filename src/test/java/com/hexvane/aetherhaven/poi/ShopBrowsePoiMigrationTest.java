package com.hexvane.aetherhaven.poi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class ShopBrowsePoiMigrationTest {

    @Test
    void migrate_splitsCombinedWorkShopDeskAndClonesBrowsePoi() {
        UUID townId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        Set<String> tags = new HashSet<>();
        tags.add("WORK");
        tags.add("SHOP");
        PoiEntry desk =
            new PoiEntry(
                UUID.randomUUID(),
                townId,
                10,
                2,
                20,
                tags,
                1,
                plotId,
                "Furniture_Village_Counter",
                PoiInteractionKind.WORK_SURFACE,
                false,
                null,
                10.5,
                2.0,
                19.5,
                null,
                "merchant"
            );

        List<PoiEntry> migrated = ShopBrowsePoiMigration.migrate(List.of(desk));
        assertEquals(2, migrated.size());

        PoiEntry work = migrated.stream().filter(e -> e.getTags().contains("WORK")).findFirst().orElseThrow();
        PoiEntry shop = migrated.stream().filter(e -> e.getTags().contains("SHOP")).findFirst().orElseThrow();
        assertFalse(work.getTags().contains("SHOP"));
        assertEquals("merchant", work.getWorkResidentKind());
        assertFalse(shop.getTags().contains("WORK"));
        assertEquals(PoiInteractionKind.SIT, shop.getInteractionKind());
        assertEquals(10, shop.getX());
        assertEquals(2, shop.getY());
        assertEquals(19, shop.getZ());
    }

    @Test
    void migrate_doesNotCloneWhenShopOnlyAlreadyExists() {
        UUID townId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        Set<String> deskTags = new HashSet<>();
        deskTags.add("WORK");
        deskTags.add("SHOP");
        Set<String> browseTags = new HashSet<>();
        browseTags.add("SHOP");
        PoiEntry desk =
            new PoiEntry(
                UUID.randomUUID(),
                townId,
                1,
                1,
                1,
                deskTags,
                1,
                plotId,
                null,
                PoiInteractionKind.WORK_SURFACE
            );
        PoiEntry browse =
            new PoiEntry(
                UUID.randomUUID(),
                townId,
                2,
                1,
                2,
                browseTags,
                1,
                plotId,
                null,
                PoiInteractionKind.SIT
            );

        List<PoiEntry> migrated = ShopBrowsePoiMigration.migrate(List.of(desk, browse));
        assertEquals(2, migrated.size());
        assertTrue(migrated.stream().anyMatch(e -> e.getTags().contains("WORK") && !e.getTags().contains("SHOP")));
        assertEquals(1, migrated.stream().filter(e -> e.getTags().contains("SHOP") && !e.getTags().contains("WORK")).count());
    }
}
