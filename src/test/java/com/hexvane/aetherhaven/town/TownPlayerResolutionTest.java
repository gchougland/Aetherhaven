package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TownPlayerResolutionTest {

    @Test
    void affiliatedTownsSortByDisplayName() {
        UUID owner = UUID.randomUUID();
        TownRecord beta =
            new TownRecord(UUID.randomUUID(), owner, "w", 0, 0, 0, 1, 1, 0L);
        beta.setDisplayName("Beta Vale");
        TownRecord alpha =
            new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "w", 0, 0, 0, 1, 1, 0L);
        alpha.setDisplayName("Alpha Creek");
        alpha.putMember(owner, TownMemberRole.BOTH);

        List<TownRecord> sorted = List.of(beta, alpha);
        sorted = sorted.stream().sorted(TownPlayerResolution.affiliatedTownDisplayOrder()).toList();
        assertEquals("Alpha Creek", sorted.get(0).getDisplayName());
        assertEquals("Beta Vale", sorted.get(1).getDisplayName());
    }

    @Test
    void ownerCanBeMemberElsewhereWithoutSameTownConflict() {
        UUID player = UUID.randomUUID();
        TownRecord owned =
            new TownRecord(UUID.randomUUID(), player, "w", 0, 0, 0, 1, 1, 0L);
        TownRecord guest =
            new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "w", 0, 0, 0, 1, 1, 0L);

        assertNull(guest.findPendingInvite(player));
        assert !guest.hasMemberOrOwner(player);

        guest.putMember(player, TownMemberRole.BOTH);
        assert guest.hasMemberOrOwner(player);
        assert owned.getOwnerUuid().equals(player);
        assert !owned.hasMemberOrOwner(player) || owned.getOwnerUuid().equals(player);
    }

    @Test
    void journalActiveTownIdRoundTrip() {
        PlayerTownJournalState state = new PlayerTownJournalState();
        UUID id = UUID.randomUUID();
        state.setActiveTownId(id);
        assertEquals(id, state.getActiveTownId());
        state.clearActiveTownId();
        assertNull(state.getActiveTownId());
    }
}
