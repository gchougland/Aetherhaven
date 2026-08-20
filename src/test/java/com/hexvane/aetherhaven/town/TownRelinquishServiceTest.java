package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TownRelinquishServiceTest {

    private static TownRecord townOwnedBy(UUID owner) {
        TownRecord town = new TownRecord(UUID.randomUUID(), owner, "w", 0, 0, 0, 1, 1, 0L);
        town.setDisplayName("Willowbrook");
        return town;
    }

    @Test
    void guestRelinquishRemovesOnlyThatMember() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        TownRecord town = townOwnedBy(owner);
        town.putMember(guest, TownMemberRole.BOTH);
        town.putMember(other, TownMemberRole.BOTH);

        TownRelinquishService.RelinquishResult result =
            TownRelinquishService.relinquish(town, guest, uuid -> false, uuid -> "n");

        assertNotNull(result);
        assertEquals(TownRelinquishService.RelinquishKind.LEFT_AS_MEMBER, result.kind);
        assertFalse(town.hasMemberOrOwner(guest));
        assertTrue(town.isOwner(owner));
        assertTrue(town.isMemberPlayer(other));
    }

    @Test
    void ownerWithMemberTransfersOwnership() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        TownRecord town = townOwnedBy(owner);
        town.putMember(member, TownMemberRole.BOTH);

        TownRelinquishService.RelinquishResult result =
            TownRelinquishService.relinquish(town, owner, uuid -> false, uuid -> "River");

        assertNotNull(result);
        assertEquals(TownRelinquishService.RelinquishKind.TRANSFERRED, result.kind);
        assertEquals(member, result.successorUuid);
        assertTrue(town.isOwner(member));
        assertFalse(town.isOwner(owner));
        assertFalse(town.isMemberPlayer(member));
        assertFalse(town.hasMemberOrOwner(owner));
    }

    @Test
    void successorWhoAlreadyOwnsATownIsSkipped() {
        UUID owner = UUID.randomUUID();
        UUID busy = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        TownRecord town = townOwnedBy(owner);
        town.putMember(busy, TownMemberRole.BOTH);
        town.putMember(free, TownMemberRole.BOTH);
        Set<UUID> alreadyOwns = Set.of(busy);

        TownRelinquishService.RelinquishResult result =
            TownRelinquishService.relinquish(town, owner, alreadyOwns::contains, uuid -> "n");

        assertNotNull(result);
        assertEquals(TownRelinquishService.RelinquishKind.TRANSFERRED, result.kind);
        assertEquals(free, result.successorUuid);
        assertTrue(town.isOwner(free));
        assertTrue(town.isMemberPlayer(busy));
    }

    @Test
    void ownerAloneClearsOwner() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        TownRecord town = townOwnedBy(owner);
        town.addPendingInvite(new TownPendingInvite(UUID.randomUUID(), System.currentTimeMillis(), owner));

        TownRelinquishService.RelinquishResult result =
            TownRelinquishService.relinquish(town, owner, uuid -> false, uuid -> "n");

        assertNotNull(result);
        assertEquals(TownRelinquishService.RelinquishKind.ORPHANED, result.kind);
        assertFalse(town.hasOwner());
        assertNull(town.getOwnerUuid());
        assertFalse(town.isOwner(owner));
        assertFalse(town.hasMemberOrOwner(owner));
        assertFalse(town.hasMemberOrOwner(stranger));
        assertTrue(town.getPendingInvites().isEmpty());
    }

    @Test
    void unownedTownOwnerLookupIsNullSafe() {
        UUID owner = UUID.randomUUID();
        TownRecord town = townOwnedBy(owner);
        town.clearOwner();
        assertFalse(town.hasOwner());
        assertNull(town.getOwnerUuid());
        assertFalse(town.isOwner(owner));
        assertFalse(town.hasMemberOrOwner(owner));
    }

    @Test
    void claimSetsOwnerAndFailsWhenBlocked() {
        UUID claimer = UUID.randomUUID();
        TownRecord town = townOwnedBy(UUID.randomUUID());
        town.clearOwner();

        assertNull(TownRelinquishService.tryClaim(town, claimer, "Ash", false));
        assertTrue(town.isOwner(claimer));

        UUID other = UUID.randomUUID();
        assertEquals(
            TownRelinquishService.ClaimFail.ALREADY_OWNED,
            TownRelinquishService.tryClaim(town, other, "Bee", false)
        );

        TownRecord empty = townOwnedBy(UUID.randomUUID());
        empty.clearOwner();
        assertEquals(
            TownRelinquishService.ClaimFail.CLAIMANT_OWNS_TOWN,
            TownRelinquishService.tryClaim(empty, claimer, "Ash", true)
        );
        assertFalse(empty.hasOwner());
    }
}
