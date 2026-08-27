package com.hexvane.aetherhaven.difficulty;

import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class DifficultyAccess {
    private DifficultyAccess() {}

    /** True when the sender may open or save difficulty for the given town (owner, or admin with a named town). */
    public static boolean canChangeDifficulty(
        @Nonnull TownManager tm,
        @Nonnull UUID senderUuid,
        @Nonnull TownRecord town,
        boolean isAdmin
    ) {
        TownCommandResolution res =
            TownCommandResolution.resolveForOwnerAction(tm, senderUuid, town.getDisplayName(), isAdmin);
        return res.isOk() && res.townOrThrow().getTownId().equals(town.getTownId());
    }
}
