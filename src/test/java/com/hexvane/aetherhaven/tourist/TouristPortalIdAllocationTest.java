package com.hexvane.aetherhaven.tourist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("tourist")
class TouristPortalIdAllocationTest {

    @Test
    void allocate_mintsWhenPreferredBlank() {
        UUID a = TouristPortalIdAllocation.allocate(new Vector3i(1, 2, 3), null, id -> null);
        UUID b = TouristPortalIdAllocation.allocate(new Vector3i(1, 2, 3), "  ", id -> null);
        assertNotEquals(a, b);
    }

    @Test
    void allocate_reusesFreePreferredId() {
        UUID preferred = UUID.fromString("e3db9165-f526-4def-bb89-10a74ffd8285");
        UUID got =
            TouristPortalIdAllocation.allocate(
                new Vector3i(-921, 124, 938), preferred.toString(), id -> null
            );
        assertEquals(preferred, got);
    }

    @Test
    void allocate_reusesPreferredWhenAlreadyBoundToSameBlock() {
        UUID preferred = UUID.fromString("e3db9165-f526-4def-bb89-10a74ffd8285");
        TouristPortalRecord owner = new TouristPortalRecord();
        owner.setPortalId(preferred);
        owner.setBlockPosition(new Vector3i(-921, 124, 938));
        UUID got =
            TouristPortalIdAllocation.allocate(
                new Vector3i(-921, 124, 938), preferred.toString(), id -> owner
            );
        assertEquals(preferred, got);
    }

    @Test
    void allocate_mintsWhenPreferredOwnedByDifferentBlock() {
        UUID shared = UUID.fromString("e3db9165-f526-4def-bb89-10a74ffd8285");
        TouristPortalRecord lyre = new TouristPortalRecord();
        lyre.setPortalId(shared);
        lyre.setBlockPosition(new Vector3i(-921, 124, 938));

        Map<UUID, TouristPortalRecord> byId = new HashMap<>();
        byId.put(shared, lyre);

        UUID llamareth =
            TouristPortalIdAllocation.allocate(
                new Vector3i(-1095, 123, 130), shared.toString(), byId::get
            );
        assertNotEquals(shared, llamareth);
    }

    @Test
    void preferredIdFromBlock_ignoresTemplatePlacement() {
        TouristPortalBlock template =
            new TouristPortalBlock("e3db9165-f526-4def-bb89-10a74ffd8285", "", "", true);
        assertNull(TouristPortalIdAllocation.preferredIdFromBlock(template));

        TouristPortalBlock configured =
            new TouristPortalBlock(
                "e3db9165-f526-4def-bb89-10a74ffd8285",
                "8b4b8e5b-487a-47a8-8722-4994c358d796",
                "9e336c83-7b7b-4a39-9022-5f54753413c9",
                true
            );
        assertEquals(
            "e3db9165-f526-4def-bb89-10a74ffd8285",
            TouristPortalIdAllocation.preferredIdFromBlock(configured)
        );
    }
}
