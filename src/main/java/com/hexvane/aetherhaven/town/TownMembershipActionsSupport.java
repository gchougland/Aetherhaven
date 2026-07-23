package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Journal active-town cleanup when membership changes. */
final class TownMembershipActionsSupport {
    private TownMembershipActionsSupport() {}

    static void clearActiveTownIfMember(@Nonnull World world, @Nonnull UUID playerUuid, @Nonnull UUID townId) {
        TownPlayerResolution.clearActiveTownIdIfMatches(world, playerUuid, townId);
    }
}
