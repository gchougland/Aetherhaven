package com.hexvane.aetherhaven.townsfolk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TownsfolkPoolStateTest {

    @Test
    void releaseForTownOnlyReleasesThatTownsCheckouts() {
        UUID dissolvedTown = UUID.randomUUID();
        UUID otherTown = UUID.randomUUID();
        TownsfolkPoolState pool = new TownsfolkPoolState();
        pool.checkout(record("guard", dissolvedTown));
        pool.checkout(record("tourist", dissolvedTown));
        pool.checkout(record("neighbor", otherTown));

        assertEquals(2, pool.releaseForTown(dissolvedTown));
        assertFalse(pool.isCheckedOut(dissolvedTown, "guard"));
        assertFalse(pool.isCheckedOut(dissolvedTown, "tourist"));
        assertTrue(pool.isCheckedOut(otherTown, "neighbor"));
    }

    @Test
    void sameCharacterIdCanCheckoutInTwoTowns() {
        UUID townA = UUID.randomUUID();
        UUID townB = UUID.randomUUID();
        TownsfolkPoolState pool = new TownsfolkPoolState();
        pool.checkout(record("shared_id", townA));
        pool.checkout(record("shared_id", townB));

        assertTrue(pool.isCheckedOut(townA, "shared_id"));
        assertTrue(pool.isCheckedOut(townB, "shared_id"));
        assertFalse(pool.isCheckedOut(townA, "other"));
    }

    private static TownsfolkPoolCheckoutRecord record(String characterId, UUID townId) {
        return new TownsfolkPoolCheckoutRecord(
            characterId,
            townId.toString(),
            UUID.randomUUID().toString(),
            TownsfolkAssignmentKinds.TOURIST,
            ""
        );
    }
}
