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
        assertFalse(pool.isCheckedOut("guard"));
        assertFalse(pool.isCheckedOut("tourist"));
        assertTrue(pool.isCheckedOut("neighbor"));
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
