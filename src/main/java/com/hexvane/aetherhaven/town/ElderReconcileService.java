package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;

/** @deprecated Use {@link TownResidentReconcileService} — elder dedupe is handled there. */
@Deprecated
public final class ElderReconcileService {
    private ElderReconcileService() {}

    public static void scheduleAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownResidentReconcileService.scheduleAfterWorldLoad(world, plugin);
    }

    public static void onTownMemberPlayerReady(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull UUID playerUuid) {
        TownResidentReconcileService.onTownMemberPlayerReady(world, plugin, playerUuid);
    }
}
